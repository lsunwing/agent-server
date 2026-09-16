package com.david.agent.document.service;

import com.david.agent.document.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Markdown 结构感知分块器（P0）。
 *
 * 解析顺序（按行扫描）：
 * 1. Fenced code block（``` 或 ~~~）— 整块保留，禁止窗口切断
 * 2. ATX Heading（# ~ ######）— 开启新语义单元，并维护标题路径
 * 3. GFM Table（连续 | 行 + 分隔行）— 按「表头 + 完整数据行」分组
 * 4. List（- / * / + 或 1.）— 按项聚合
 * 5. Paragraph — 普通段落；超长再退回固定窗口 + overlap 兜底
 *
 * 每个非标题块都会带上当前标题路径前缀，便于独立检索时理解上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class TextChunker {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.*");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*([-*+] |\\d+[.)] )\\S?");
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(```|~~~)");

    private final RagProperties properties;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            log.info("[rag-chunk] skip: input empty");
            return List.of();
        }

        int size = properties.chunkSize();
        int overlap = properties.chunkOverlap();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');

        log.info("[rag-chunk] start: length={}, chunkSize={}, overlap={}",
                normalized.length(), size, overlap);

        List<Block> blocks = parseBlocks(normalized);
        log.info("[rag-chunk] parsed blocks: total={}, {}", blocks.size(), countByType(blocks));

        List<String> chunks = new ArrayList<>();
        for (Block block : blocks) {
            switch (block.type()) {
                case HEADING -> chunks.add(withHeadingContext(block));
                case CODE -> chunkCodeBlock(block, size, chunks);
                case TABLE -> chunkTable(block, size, chunks);
                case LIST -> chunkList(block, size, overlap, chunks);
                case PARAGRAPH -> chunkParagraph(block, size, overlap, chunks);
            }
        }

        log.info("[rag-chunk] done: totalChunks={}, avgLen={}, maxLen={}, minLen={}",
                chunks.size(),
                chunks.stream().mapToInt(String::length).average().orElse(0),
                chunks.stream().mapToInt(String::length).max().orElse(0),
                chunks.stream().mapToInt(String::length).min().orElse(0));
        return chunks;
    }

    // ---------- 解析 ----------

    private List<Block> parseBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        List<String> headingTrail = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];

            // 1) fenced code：整块吞掉，直到闭合 fence
            if (CODE_FENCE.matcher(line).find()) {
                flushParagraph(blocks, paragraph, headingTrail);
                int start = i;
                List<String> codeLines = new ArrayList<>();
                codeLines.add(line);
                i++;
                while (i < lines.length) {
                    codeLines.add(lines[i]);
                    if (CODE_FENCE.matcher(lines[i]).find() && i > start) {
                        i++;
                        break;
                    }
                    i++;
                }
                blocks.add(new Block(BlockType.CODE, String.join("\n", codeLines), headingPath(headingTrail)));
                log.debug("[rag-chunk] block=CODE lines={} path='{}'", codeLines.size(), headingPath(headingTrail));
                continue;
            }

            // 2) heading：落盘当前段落，记录标题路径
            if (HEADING.matcher(line).matches()) {
                flushParagraph(blocks, paragraph, headingTrail);
                int level = headingLevel(line);
                while (headingTrail.size() >= level) {
                    headingTrail.remove(headingTrail.size() - 1);
                }
                while (headingTrail.size() < level - 1) {
                    headingTrail.add("");
                }
                headingTrail.add(line);
                blocks.add(new Block(BlockType.HEADING, line, headingPath(headingTrail.subList(0, headingTrail.size() - 1))));
                log.debug("[rag-chunk] block=HEADING level={} text='{}'", level, line.trim());
                i++;
                continue;
            }

            // 3) table：连续 | 行（要求紧随分隔行才认定为表，避免误判）
            if (isTableStart(lines, i)) {
                flushParagraph(blocks, paragraph, headingTrail);
                List<String> tableLines = new ArrayList<>();
                while (i < lines.length && TABLE_ROW.matcher(lines[i]).matches()) {
                    tableLines.add(lines[i]);
                    i++;
                }
                blocks.add(new Block(BlockType.TABLE, String.join("\n", tableLines), headingPath(headingTrail)));
                log.info("[rag-chunk] block=TABLE rows={} (含表头/分隔行) path='{}' preview='{}'",
                        tableLines.size(), headingPath(headingTrail), preview(tableLines.get(0)));
                continue;
            }

            // 4) list：连续列表项
            if (LIST_ITEM.matcher(line).find()) {
                flushParagraph(blocks, paragraph, headingTrail);
                List<String> listLines = new ArrayList<>();
                while (i < lines.length && (LIST_ITEM.matcher(lines[i]).find() || isListContinuation(lines[i]))) {
                    listLines.add(lines[i]);
                    i++;
                }
                blocks.add(new Block(BlockType.LIST, String.join("\n", listLines), headingPath(headingTrail)));
                log.debug("[rag-chunk] block=LIST items≈{} path='{}'", listLines.size(), headingPath(headingTrail));
                continue;
            }

            // 5) 普通行 → 段落缓冲；空行表示段落结束
            if (line.isBlank()) {
                flushParagraph(blocks, paragraph, headingTrail);
            } else {
                if (paragraph.isEmpty()) {
                    paragraph.append(line);
                } else {
                    paragraph.append('\n').append(line);
                }
            }
            i++;
        }
        flushParagraph(blocks, paragraph, headingTrail);
        return blocks;
    }

    private boolean isTableStart(String[] lines, int i) {
        // 表格至少 3 行：表头 + 分隔 + 数据；或表头 + 分隔
        if (i + 1 >= lines.length) {
            return false;
        }
        return TABLE_ROW.matcher(lines[i]).matches()
                && TABLE_SEPARATOR.matcher(lines[i + 1]).matches();
    }

    private boolean isListContinuation(String line) {
        // 列表项的缩进续行
        return line.startsWith("  ") || line.startsWith("\t");
    }

    private void flushParagraph(List<Block> blocks, StringBuilder paragraph, List<String> headingTrail) {
        if (paragraph.isEmpty()) {
            return;
        }
        String content = paragraph.toString().trim();
        paragraph.setLength(0);
        if (!content.isEmpty()) {
            blocks.add(new Block(BlockType.PARAGRAPH, content, headingPath(headingTrail)));
            log.debug("[rag-chunk] block=PARAGRAPH len={} path='{}'", content.length(), headingPath(headingTrail));
        }
    }

    // ---------- 各类型分块 ----------

    /** 代码块整块保留；仅当远超 size 时按行硬切（仍保持行边界）。 */
    private void chunkCodeBlock(Block block, int size, List<String> chunks) {
        String content = block.content();
        if (content.length() <= size) {
            chunks.add(withHeadingContext(block));
            log.debug("[rag-chunk] CODE keep-whole len={}", content.length());
            return;
        }
        log.info("[rag-chunk] CODE too large len={} > {}, split by lines", content.length(), size);
        List<String> lines = content.lines().toList();
        StringBuilder buf = new StringBuilder();
        String context = headingContextPrefix(block);
        int part = 0;
        for (String line : lines) {
            if (buf.length() + line.length() + 1 > size && !buf.isEmpty()) {
                chunks.add(context + buf.toString().stripTrailing());
                log.debug("[rag-chunk] CODE part#{} len={}", part++, buf.length());
                buf.setLength(0);
            }
            buf.append(line).append('\n');
        }
        if (!buf.isEmpty()) {
            chunks.add(context + buf.toString().stripTrailing());
            log.debug("[rag-chunk] CODE part#{} len={}", part, buf.length());
        }
    }

    /**
     * 表格分块策略：
     * - 始终保留「表头 + 分隔行」
     * - 数据行按完整行累加，直到接近 chunkSize
     * - 每个分片都重复表头，保证 chunk 可独立理解
     */
    private void chunkTable(Block block, int size, List<String> chunks) {
        List<String> lines = block.content().lines().filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) {
            return;
        }
        if (lines.size() < 2) {
            // 异常：只有一行，退化为段落
            chunks.add(withHeadingContext(block));
            return;
        }

        String header = lines.get(0);
        String separator = lines.get(1);
        List<String> dataRows = lines.subList(2, lines.size());
        String context = headingContextPrefix(block);
        String headerBlock = context + header + "\n" + separator;

        // 小表：整表一个 chunk
        if (block.content().length() + context.length() <= size) {
            chunks.add(context + block.content());
            log.info("[rag-chunk] TABLE keep-whole: dataRows={}, totalLen={}",
                    dataRows.size(), block.content().length());
            return;
        }

        log.info("[rag-chunk] TABLE split: dataRows={}, chunkSize={}, headerLen={}",
                dataRows.size(), size, headerBlock.length());

        StringBuilder buf = new StringBuilder(headerBlock);
        int batch = 0;
        int rowsInBatch = 0;
        for (String row : dataRows) {
            // 当前 batch 再加一行会超限 → 先落盘
            if (buf.length() + row.length() + 1 > size && rowsInBatch > 0) {
                chunks.add(buf.toString());
                log.info("[rag-chunk] TABLE batch#{} emitted: rows={}, len={} (header repeated)",
                        batch++, rowsInBatch, buf.length());
                buf = new StringBuilder(headerBlock);
                rowsInBatch = 0;
            }
            buf.append('\n').append(row);
            rowsInBatch++;
        }
        if (rowsInBatch > 0) {
            chunks.add(buf.toString());
            log.info("[rag-chunk] TABLE batch#{} emitted: rows={}, len={} (header repeated)",
                    batch, rowsInBatch, buf.length());
        }
    }

    private void chunkList(Block block, int size, int overlap, List<String> chunks) {
        String content = block.content();
        if (content.length() <= size) {
            chunks.add(withHeadingContext(block));
            log.debug("[rag-chunk] LIST keep-whole len={}", content.length());
            return;
        }
        // 超长列表：按项聚合，不切断单项
        log.info("[rag-chunk] LIST too large len={} > {}, group by items", content.length(), size);
        List<String> items = splitListItems(content);
        String context = headingContextPrefix(block);
        StringBuilder buf = new StringBuilder(context);
        int batch = 0;
        int itemsInBatch = 0;
        for (String item : items) {
            if (buf.length() + item.length() + 1 > size && itemsInBatch > 0) {
                chunks.add(buf.toString().stripTrailing());
                log.debug("[rag-chunk] LIST batch#{} emitted: items={}, len={}", batch++, itemsInBatch, buf.length());
                buf = new StringBuilder(context);
                itemsInBatch = 0;
            }
            if (buf.length() > context.length()) {
                buf.append('\n');
            }
            buf.append(item);
            itemsInBatch++;
        }
        if (itemsInBatch > 0) {
            chunks.add(buf.toString().stripTrailing());
            log.debug("[rag-chunk] LIST batch#{} emitted: items={}, len={}", batch, itemsInBatch, buf.length());
        }
    }

    private List<String> splitListItems(String content) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.lines().toList()) {
            if (LIST_ITEM.matcher(line).find() && !current.isEmpty()) {
                items.add(current.toString());
                current.setLength(0);
            }
            if (current.isEmpty()) {
                current.append(line);
            } else {
                current.append('\n').append(line);
            }
        }
        if (!current.isEmpty()) {
            items.add(current.toString());
        }
        return items;
    }

    /** 普通段落：短的直接收；长的按字符窗口 + overlap（唯一使用 overlap 的路径）。 */
    private void chunkParagraph(Block block, int size, int overlap, List<String> chunks) {
        String content = block.content();
        String context = headingContextPrefix(block);
        if (context.length() + content.length() <= size) {
            chunks.add(withHeadingContext(block));
            log.debug("[rag-chunk] PARA keep-whole len={}", content.length());
            return;
        }

        int effectiveSize = Math.max(50, size - context.length());
        int step = Math.max(1, effectiveSize - overlap);
        log.info("[rag-chunk] PARA window-split: len={}, effectiveSize={}, overlap={}, step={}",
                content.length(), effectiveSize, overlap, step);

        int part = 0;
        for (int i = 0; i < content.length(); i += step) {
            int end = Math.min(i + effectiveSize, content.length());
            String piece = content.substring(i, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(context + piece);
                log.debug("[rag-chunk] PARA part#{} offset={} len={}", part, i, piece.length());
                part++;
            }
            if (end >= content.length()) {
                break;
            }
        }
    }

    // ---------- 工具 ----------

    private String withHeadingContext(Block block) {
        return headingContextPrefix(block) + block.content();
    }

    /**
     * 标题路径前缀：把父级标题原样带上，让 chunk 独立可读。
     * 例：\n# 用户表\n## 字段说明\n + 正文
     */
    private String headingContextPrefix(Block block) {
        String path = block.headingPath();
        if (path == null || path.isBlank()) {
            return "";
        }
        return path + "\n\n";
    }

    private String headingPath(List<String> headingTrail) {
        List<String> parts = new ArrayList<>();
        for (String h : headingTrail) {
            if (h != null && !h.isBlank()) {
                parts.add(h);
            }
        }
        return String.join("\n", parts);
    }

    private int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return Math.min(Math.max(level, 1), 6);
    }

    private String countByType(List<Block> blocks) {
        Map<BlockType, Integer> counter = new LinkedHashMap<>();
        for (Block b : blocks) {
            counter.merge(b.type(), 1, Integer::sum);
        }
        return counter.toString();
    }

    private String preview(String line) {
        if (line == null) {
            return "";
        }
        return line.length() <= 80 ? line : line.substring(0, 80) + "...";
    }

    // ---------- 内部模型 ----------

    private enum BlockType {
        HEADING, CODE, TABLE, LIST, PARAGRAPH
    }

    private record Block(BlockType type, String content, String headingPath) {
    }
}
