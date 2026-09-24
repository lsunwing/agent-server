package com.david.agent.document.schema.chunk;

import com.david.agent.document.schema.config.SchemaRagProperties;
import com.david.agent.document.schema.model.ColumnSchema;
import com.david.agent.document.schema.model.IndexSchema;
import com.david.agent.document.schema.model.IndexType;
import com.david.agent.document.schema.model.RelationSchema;
import com.david.agent.document.schema.model.SchemaDocument;
import com.david.agent.document.schema.model.TableSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用 Schema 分块：只认中间模型，不认源文档排版。
 * 视图顺序固定：overview → columns(1..k) → indexes → relations。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class DefaultSchemaChunkBuilder implements SchemaChunkBuilder {

    private final SchemaRagProperties properties;

    @Override
    public List<ChunkEnvelope> build(SchemaDocument document) {
        List<ChunkEnvelope> chunks = new ArrayList<>();
        int order = 0;
        for (TableSchema table : document.tables()) {
            if (properties.emitOverview()) {
                chunks.add(new ChunkEnvelope(
                        renderOverview(table),
                        meta(ChunkMetadata.SECTION_OVERVIEW, table, columnNamesOf(table.columns()), document),
                        order++
                ));
            }
            order = appendColumns(chunks, table, document, order);
            if (properties.emitIndexes() && !table.indexes().isEmpty()) {
                chunks.add(new ChunkEnvelope(
                        renderIndexes(table),
                        meta(ChunkMetadata.SECTION_INDEXES, table, "", document),
                        order++
                ));
            }
            if (properties.emitRelations() && !table.relations().isEmpty()) {
                chunks.add(new ChunkEnvelope(
                        renderRelations(table),
                        meta(ChunkMetadata.SECTION_RELATIONS, table, "", document),
                        order++
                ));
            }
        }
        log.info("[schema-chunk] built {} chunks for {} tables, source={}",
                chunks.size(), document.tables().size(), document.sourceName());
        return chunks;
    }

    private int appendColumns(List<ChunkEnvelope> chunks, TableSchema table, SchemaDocument document, int order) {
        if (!properties.emitColumns() || table.columns().isEmpty()) {
            return order;
        }
        String whole = renderColumns(table, table.columns(), 0, 0);
        if (whole.length() <= properties.maxChunkSize()) {
            chunks.add(new ChunkEnvelope(
                    whole,
                    meta(ChunkMetadata.SECTION_COLUMNS, table, columnNamesOf(table.columns()), document),
                    order++
            ));
            return order;
        }
        int per = properties.fieldsPerChunk();
        int totalParts = (table.columns().size() + per - 1) / per;
        for (int i = 0; i < table.columns().size(); i += per) {
            int part = i / per + 1;
            List<ColumnSchema> slice = table.columns().subList(i, Math.min(i + per, table.columns().size()));
            chunks.add(new ChunkEnvelope(
                    renderColumns(table, slice, part, totalParts),
                    meta(ChunkMetadata.SECTION_COLUMNS, table, columnNamesOf(slice), document),
                    order++
            ));
        }
        return order;
    }

    private ChunkMetadata meta(String section, TableSchema table, String columnNames, SchemaDocument document) {
        return new ChunkMetadata(
                section,
                table.name(),
                table.displayName(),
                columnNames,
                document.sourceName(),
                document.dialect()
        );
    }

    private String renderOverview(TableSchema table) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 表：").append(table.name());
        if (!table.displayName().isBlank()) {
            sb.append("（").append(table.displayName()).append("）");
        }
        sb.append("\n");
        if (!table.purpose().isBlank()) {
            sb.append("\n**用途**：").append(table.purpose()).append("\n");
        }
        if (!table.domain().isBlank()) {
            sb.append("\n**域**：").append(table.domain()).append("\n");
        }
        List<String> names = table.columns().stream().map(ColumnSchema::name).toList();
        int listed = Math.min(names.size(), properties.maxColumnsListed());
        sb.append("\n**字段一览**（").append(names.size()).append("）：");
        sb.append(String.join(", ", names.subList(0, listed)));
        if (names.size() > listed) {
            sb.append(" …等 ").append(names.size()).append(" 列");
        }
        sb.append("\n");

        if (!table.indexes().isEmpty()) {
            sb.append("\n**索引**：\n");
            for (IndexSchema index : table.indexes()) {
                sb.append("- ").append(indexLabel(index)).append("\n");
            }
        }
        if (!table.relations().isEmpty()) {
            sb.append("\n**关联**：\n");
            for (RelationSchema relation : table.relations()) {
                sb.append("- ").append(relation.fromColumn()).append(" → ")
                        .append(relation.toTable());
                if (!relation.toColumn().isBlank()) {
                    sb.append(".").append(relation.toColumn());
                }
                if (!relation.description().isBlank()) {
                    sb.append("（").append(relation.description()).append("）");
                }
                sb.append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    private String renderColumns(TableSchema table, List<ColumnSchema> columns, int part, int totalParts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 表：").append(table.name());
        if (!table.displayName().isBlank()) {
            sb.append("（").append(table.displayName()).append("）");
        }
        sb.append(" · 字段信息");
        if (totalParts > 1) {
            sb.append(" ").append(part).append("/").append(totalParts);
        }
        sb.append("\n\n");
        sb.append("| 字段名 | 数据类型 | 是否可空 | 默认值 | 业务含义 | 枚举 |\n");
        sb.append("|:--|:--|:--|:--|:--|:--|\n");
        for (ColumnSchema column : columns) {
            sb.append("| ").append(column.name())
                    .append(" | ").append(column.type())
                    .append(" | ").append(nullableText(column.nullable()))
                    .append(" | ").append(defaultText(column.defaultValue()))
                    .append(" | ").append(escapeCell(column.comment()))
                    .append(" | ").append(escapeCell(renderEnums(column.enums())))
                    .append(" |\n");
        }
        return sb.toString().stripTrailing();
    }

    private String renderIndexes(TableSchema table) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 表：").append(table.name());
        if (!table.displayName().isBlank()) {
            sb.append("（").append(table.displayName()).append("）");
        }
        sb.append(" · 索引信息\n\n");
        for (IndexSchema index : table.indexes()) {
            sb.append("- **").append(indexTypeName(index.type())).append("**：`")
                    .append(index.name()).append("` ON (`")
                    .append(String.join(", ", index.columns()))
                    .append("`)\n");
        }
        return sb.toString().stripTrailing();
    }

    private String renderRelations(TableSchema table) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 表：").append(table.name());
        if (!table.displayName().isBlank()) {
            sb.append("（").append(table.displayName()).append("）");
        }
        sb.append(" · 关联关系\n\n");
        for (RelationSchema relation : table.relations()) {
            sb.append("- ").append(relation.fromColumn())
                    .append(" → ").append(relation.toTable());
            if (!relation.toColumn().isBlank()) {
                sb.append("(").append(relation.toColumn()).append(")");
            }
            if (!relation.description().isBlank()) {
                sb.append("  ").append(relation.description());
            }
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String indexLabel(IndexSchema index) {
        return indexTypeName(index.type()) + "：" + index.name()
                + "(" + String.join(", ", index.columns()) + ")";
    }

    private String indexTypeName(IndexType type) {
        return switch (type) {
            case PRIMARY -> "主键";
            case UNIQUE -> "唯一";
            case NORMAL -> "普通";
        };
    }

    private String nullableText(Boolean nullable) {
        if (nullable == null) {
            return "-";
        }
        return nullable ? "YES" : "NOT NULL";
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String renderEnums(Map<String, String> enums) {
        if (enums == null || enums.isEmpty()) {
            return "";
        }
        return enums.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    private String escapeCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("|", "｜").replace("\n", " ");
    }

    private String columnNamesOf(List<ColumnSchema> columns) {
        return columns.stream().map(ColumnSchema::name).collect(Collectors.joining(","));
    }
}
