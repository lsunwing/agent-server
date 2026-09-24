package com.david.agent.document.schema.parse;

import com.david.agent.document.schema.SchemaParseException;
import com.david.agent.document.schema.model.ColumnSchema;
import com.david.agent.document.schema.model.IndexSchema;
import com.david.agent.document.schema.model.IndexType;
import com.david.agent.document.schema.model.RelationSchema;
import com.david.agent.document.schema.model.SchemaDocument;
import com.david.agent.document.schema.model.TableSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析固定契约的数据字典 Markdown（见 sd_tables.template.md）。
 *
 * <pre>
 * # t_user_arrears · 用户欠费信息表
 * - 用途: ...
 * ## 字段 | ## 索引 | ## 关联
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class MarkdownDictParser implements SchemaDocumentParser {

    private static final Pattern TABLE_TITLE = Pattern.compile(
            "^#\\s+(?<name>`?[^\\s`·:：]+`?)(?:\\s*[·:：|\\-–—]\\s*(?<display>.+))?\\s*$");
    private static final Pattern META_LINE = Pattern.compile("^\\s*[-*]\\s*(?<key>[^:：]+?)\\s*[:：]\\s*(?<value>.*)$");
    private static final Pattern SECTION = Pattern.compile("^##\\s+(?<title>.+?)\\s*$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\s*\\|?[\\s:|\\-]+\\|?\\s*$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");

    @Override
    public boolean supports(String content, String fileName) {
        if (content == null || content.isBlank()) {
            return false;
        }
        boolean hasTableTitle = false;
        boolean hasFieldSection = false;
        int tableTitleCount = 0;
        for (String line : content.lines().toList()) {
            if (TABLE_TITLE.matcher(line).matches() && !line.startsWith("##")) {
                // exclude pure headings that look like H1-only noise: require a table-name-like token
                String name = extractName(line);
                if (name != null && (name.contains("_") || name.toLowerCase(Locale.ROOT).startsWith("t_")
                        || line.contains("·") || line.contains(":") || line.contains("："))) {
                    hasTableTitle = true;
                    tableTitleCount++;
                }
            }
            String section = sectionTitle(line);
            if (section != null && isFieldSection(section)) {
                hasFieldSection = true;
            }
        }
        return tableTitleCount >= 2 || (hasTableTitle && hasFieldSection);
    }

    @Override
    public SchemaDocument parse(String content, String fileName) {
        if (content == null || content.isBlank()) {
            throw new SchemaParseException("schema content is blank: " + fileName);
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        List<TableBlock> blocks = splitTableBlocks(normalized);
        if (blocks.isEmpty()) {
            throw new SchemaParseException("no table sections found in: " + fileName);
        }
        List<TableSchema> tables = new ArrayList<>();
        for (TableBlock block : blocks) {
            TableSchema table = parseTable(block);
            if (!table.name().isBlank() && !table.columns().isEmpty()) {
                tables.add(table);
            } else {
                log.warn("[schema] skip incomplete table block, name={}, columns={}", table.name(), table.columns().size());
            }
        }
        if (tables.isEmpty()) {
            throw new SchemaParseException("no valid tables parsed from: " + fileName);
        }
        log.info("[schema] parsed document source={}, tables={}", fileName, tables.size());
        return new SchemaDocument(fileName, "generic", tables);
    }

    // ---------- 切表 ----------

    private List<TableBlock> splitTableBlocks(String text) {
        List<TableBlock> blocks = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        String name = null;
        String display = null;
        List<String> body = new ArrayList<>();

        for (String line : lines) {
            Matcher title = TABLE_TITLE.matcher(line);
            if (title.matches() && line.startsWith("# ") && !line.startsWith("##")) {
                if (name != null) {
                    blocks.add(new TableBlock(name, display, body));
                }
                name = stripBackticks(title.group("name"));
                display = title.group("display") == null ? "" : title.group("display").trim();
                body = new ArrayList<>();
            } else if (name != null) {
                body.add(line);
            }
        }
        if (name != null) {
            blocks.add(new TableBlock(name, display, body));
        }
        return blocks;
    }

    private TableSchema parseTable(TableBlock block) {
        Map<String, String> meta = new LinkedHashMap<>();
        Map<SectionKind, List<String>> sections = new LinkedHashMap<>();
        SectionKind current = null;

        for (String line : block.body()) {
            String section = sectionTitle(line);
            if (section != null) {
                current = classifySection(section);
                sections.computeIfAbsent(current, k -> new ArrayList<>());
                continue;
            }
            if (current == null) {
                Matcher metaMatcher = META_LINE.matcher(line);
                if (metaMatcher.matches()) {
                    meta.put(metaMatcher.group("key").trim(), metaMatcher.group("value").trim());
                }
                continue;
            }
            if (!line.isBlank()) {
                sections.computeIfAbsent(current, k -> new ArrayList<>()).add(line);
            }
        }

        List<ColumnSchema> columns = parseColumns(sections.get(SectionKind.FIELDS));
        List<IndexSchema> indexes = parseIndexes(sections.get(SectionKind.INDEXES));
        List<RelationSchema> relations = parseRelations(sections.get(SectionKind.RELATIONS));
        applyPrimaryKeyFlags(columns, indexes);
        applyForeignKeys(columns, relations);

        return new TableSchema(
                block.name(),
                block.display(),
                meta.getOrDefault("用途", meta.getOrDefault("purpose", "")),
                meta.getOrDefault("域", meta.getOrDefault("domain", "")),
                columns,
                indexes,
                relations
        );
    }

    // ---------- 表格 ----------

    private List<ColumnSchema> parseColumns(List<String> lines) {
        List<List<String>> rows = parseMarkdownTable(lines);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> header = headerIndex(rows.get(0),
                Map.of("name", List.of("name", "字段名", "列名", "column"),
                        "type", List.of("type", "数据类型", "类型", "datatype"),
                        "nullable", List.of("nullable", "是否可空", "可空", "null"),
                        "default", List.of("default", "默认值", "默认"),
                        "comment", List.of("comment", "业务含义", "说明", "注释", "含义"),
                        "enums", List.of("enums", "enum", "取值", "枚举")));

        Integer nameIdx = header.get("name");
        if (nameIdx == null) {
            return List.of();
        }

        List<ColumnSchema> columns = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String name = cell(row, nameIdx);
            if (name.isBlank()) {
                continue;
            }
            columns.add(new ColumnSchema(
                    name,
                    cell(row, header.get("type")),
                    parseNullable(cell(row, header.get("nullable"))),
                    cell(row, header.get("default")),
                    cell(row, header.get("comment")),
                    parseEnums(cell(row, header.get("enums"))),
                    false,
                    false,
                    "",
                    ""
            ));
        }
        return columns;
    }

    private List<IndexSchema> parseIndexes(List<String> lines) {
        List<List<String>> rows = parseMarkdownTable(lines);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> header = headerIndex(rows.get(0),
                Map.of("name", List.of("name", "索引名", "名称"),
                        "type", List.of("type", "类型"),
                        "columns", List.of("columns", "字段", "列")));
        Integer nameIdx = header.get("name");
        Integer colsIdx = header.get("columns");
        if (nameIdx == null || colsIdx == null) {
            return List.of();
        }
        List<IndexSchema> indexes = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String name = cell(row, nameIdx);
            if (name.isBlank()) {
                continue;
            }
            List<String> cols = splitColumns(cell(row, colsIdx));
            IndexType type = IndexType.parse(cell(row, header.get("type")));
            if (type == IndexType.NORMAL && cols.size() == 1 && "id".equalsIgnoreCase(cols.get(0))
                    && name.toLowerCase(Locale.ROOT).contains("primary")) {
                type = IndexType.PRIMARY;
            }
            indexes.add(new IndexSchema(name, type, cols));
        }
        return indexes;
    }

    private List<RelationSchema> parseRelations(List<String> lines) {
        List<List<String>> rows = parseMarkdownTable(lines);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> header = headerIndex(rows.get(0),
                Map.of("fromColumn", List.of("from_column", "fromcolumn", "字段", "来源列"),
                        "toTable", List.of("to_table", "totable", "目标表", "关联表"),
                        "toColumn", List.of("to_column", "tocolumn", "目标列", "关联列"),
                        "comment", List.of("comment", "说明", "注释", "含义")));
        Integer fromIdx = header.get("fromColumn");
        Integer toTableIdx = header.get("toTable");
        if (fromIdx == null || toTableIdx == null) {
            return List.of();
        }
        List<RelationSchema> relations = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String from = cell(row, fromIdx);
            String toTable = cell(row, toTableIdx);
            if (from.isBlank() || toTable.isBlank()) {
                continue;
            }
            relations.add(new RelationSchema(
                    from,
                    toTable,
                    cell(row, header.get("toColumn")),
                    cell(row, header.get("comment"))
            ));
        }
        return relations;
    }

    private List<List<String>> parseMarkdownTable(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<List<String>> rows = new ArrayList<>();
        boolean headerSeen = false;
        for (String line : lines) {
            if (!TABLE_ROW.matcher(line).matches() && !TABLE_SEP.matcher(line).matches()) {
                continue;
            }
            if (TABLE_SEP.matcher(line).matches()) {
                if (headerSeen) {
                    continue;
                }
                continue;
            }
            List<String> cells = splitRow(line);
            if (cells.isEmpty()) {
                continue;
            }
            if (!headerSeen) {
                headerSeen = true;
            }
            rows.add(cells);
        }
        // drop separator-only if captured as data (already skipped); require header + optional rows
        return rows.size() <= 1 && rows.isEmpty() ? List.of() : rows;
    }

    private Map<String, Integer> headerIndex(List<String> headerRow, Map<String, List<String>> aliases) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String cell = headerRow.get(i).trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
                if (index.containsKey(entry.getKey())) {
                    continue;
                }
                for (String alias : entry.getValue()) {
                    if (cell.equals(alias.toLowerCase(Locale.ROOT))) {
                        index.put(entry.getKey(), i);
                        break;
                    }
                }
            }
        }
        return index;
    }

    private List<String> splitRow(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String[] parts = trimmed.split("\\|", -1);
        List<String> cells = new ArrayList<>(parts.length);
        for (String part : parts) {
            cells.add(part.trim());
        }
        return cells;
    }

    private List<String> splitColumns(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> cols = new ArrayList<>();
        for (String part : raw.split("[,，]")) {
            String cleaned = stripBackticks(part.trim());
            if (!cleaned.isEmpty()) {
                cols.add(cleaned);
            }
        }
        return cols;
    }

    private Map<String, String> parseEnums(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> enums = new LinkedHashMap<>();
        for (String part : raw.split("[;；]")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int eq = item.indexOf('=');
            int dash = item.indexOf('-');
            int split = eq >= 0 ? eq : dash;
            if (split <= 0) {
                continue;
            }
            String key = item.substring(0, split).trim();
            String value = item.substring(split + 1).trim();
            if (!key.isEmpty()) {
                enums.put(key, value);
            }
        }
        return enums;
    }

    private Boolean parseNullable(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "no", "not null", "false", "n" -> false;
            case "yes", "true", "y", "null" -> true;
            default -> null;
        };
    }

    private void applyPrimaryKeyFlags(List<ColumnSchema> columns, List<IndexSchema> indexes) {
        if (columns.isEmpty()) {
            return;
        }
        List<String> pkCols = indexes.stream()
                .filter(idx -> idx.type() == IndexType.PRIMARY)
                .flatMap(idx -> idx.columns().stream())
                .toList();
        if (pkCols.isEmpty()) {
            return;
        }
        for (int i = 0; i < columns.size(); i++) {
            ColumnSchema col = columns.get(i);
            boolean pk = pkCols.stream().anyMatch(pkCol -> pkCol.equalsIgnoreCase(col.name()));
            if (pk != col.primaryKey()) {
                columns.set(i, new ColumnSchema(
                        col.name(), col.type(), col.nullable(), col.defaultValue(), col.comment(),
                        col.enums(), pk, col.foreignKey(), col.refTable(), col.refColumn()));
            }
        }
    }

    private void applyForeignKeys(List<ColumnSchema> columns, List<RelationSchema> relations) {
        if (columns.isEmpty() || relations.isEmpty()) {
            return;
        }
        Map<String, RelationSchema> byColumn = new LinkedHashMap<>();
        for (RelationSchema relation : relations) {
            byColumn.put(relation.fromColumn().toLowerCase(Locale.ROOT), relation);
        }
        for (int i = 0; i < columns.size(); i++) {
            ColumnSchema col = columns.get(i);
            RelationSchema relation = byColumn.get(col.name().toLowerCase(Locale.ROOT));
            if (relation == null) {
                continue;
            }
            columns.set(i, new ColumnSchema(
                    col.name(), col.type(), col.nullable(), col.defaultValue(), col.comment(),
                    col.enums(), col.primaryKey(), true, relation.toTable(), relation.toColumn()));
        }
    }

    // ---------- 小工具 ----------

    private String sectionTitle(String line) {
        Matcher matcher = SECTION.matcher(line);
        return matcher.matches() ? matcher.group("title").trim() : null;
    }

    private boolean isFieldSection(String title) {
        String t = title.trim();
        return t.startsWith("字段") || t.equalsIgnoreCase("fields") || t.equalsIgnoreCase("columns");
    }

    private SectionKind classifySection(String title) {
        String t = title.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("字段") || t.startsWith("fields") || t.startsWith("columns") || t.startsWith("col")) {
            return SectionKind.FIELDS;
        }
        if (t.startsWith("索引") || t.startsWith("index")) {
            return SectionKind.INDEXES;
        }
        if (t.startsWith("关联") || t.startsWith("relation") || t.startsWith("fk") || t.startsWith("foreign")) {
            return SectionKind.RELATIONS;
        }
        return SectionKind.OTHER;
    }

    private String extractName(String titleLine) {
        Matcher matcher = TABLE_TITLE.matcher(titleLine);
        return matcher.matches() ? stripBackticks(matcher.group("name")) : null;
    }

    private String stripBackticks(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }

    private String cell(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index) == null ? "" : row.get(index).trim();
    }

    private enum SectionKind {
        FIELDS, INDEXES, RELATIONS, OTHER
    }

    private record TableBlock(String name, String display, List<String> body) {
    }
}
