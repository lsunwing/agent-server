package com.david.agent.document.service;

import com.david.agent.document.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class TextChunker {

    private static final int MIN_BLOCK_LENGTH = 80;
    private static final int TITLE_MAX_LENGTH = 30;

    private final RagProperties properties;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String cleaned = cleanWordArtifacts(text);
        List<Block> blocks = parseBlocks(cleaned);
        List<Block> merged = mergeSmallBlocks(blocks);
        return buildChunks(merged);
    }

    private String cleanWordArtifacts(String text) {
        return text
                .replace("\u00B6", "")
                .replace("\u00B6\u00B6", "\n")
                .replaceAll("[\u00B6\u2028\u2029]", "\n")
                .replaceAll("\\r\\n?", "\n")
                .replaceAll("\\n{3,}", "\n\n");
    }

    private enum BlockType { HEADING, TABLE, CODE, PARAGRAPH }

    private record Block(BlockType type, String content, String headingPath) {}

    private boolean isChineseTitle(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.length() > TITLE_MAX_LENGTH) {
            return false;
        }
        if (trimmed.startsWith("#") || trimmed.startsWith("|") || trimmed.startsWith("```")) {
            return false;
        }
        if (trimmed.endsWith("。") || trimmed.endsWith("！") || trimmed.endsWith("？")
                || trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")
                || trimmed.endsWith("，") || trimmed.endsWith("；") || trimmed.endsWith(";")) {
            return false;
        }
        return !trimmed.contains("，") || trimmed.length() <= 15;
    }

    private List<Block> parseBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        List<String> headingStack = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        BlockType currentType = BlockType.PARAGRAPH;
        boolean inCode = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (inCode) {
                    buffer.append(line).append('\n');
                    blocks.add(new Block(BlockType.CODE, buffer.toString().trim(), headingPath(headingStack)));
                    buffer = new StringBuilder();
                    inCode = false;
                    currentType = BlockType.PARAGRAPH;
                } else {
                    flushBuffer(blocks, buffer, currentType, headingStack);
                    buffer.append(line).append('\n');
                    inCode = true;
                    currentType = BlockType.CODE;
                }
                continue;
            }

            if (inCode) {
                buffer.append(line).append('\n');
                continue;
            }

            if (trimmed.startsWith("#")) {
                flushBuffer(blocks, buffer, currentType, headingStack);
                int level = countLeadingHashes(trimmed);
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(trimmed);
                blocks.add(new Block(BlockType.HEADING, trimmed, headingPath(headingStack)));
                currentType = BlockType.PARAGRAPH;
                continue;
            }

            if (isChineseTitle(trimmed)) {
                flushBuffer(blocks, buffer, currentType, headingStack);
                while (!headingStack.isEmpty() && !headingStack.get(headingStack.size() - 1).startsWith("#")) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(trimmed);
                blocks.add(new Block(BlockType.HEADING, trimmed, headingPath(headingStack)));
                currentType = BlockType.PARAGRAPH;
                continue;
            }

            if (trimmed.startsWith("|")) {
                if (currentType != BlockType.TABLE) {
                    flushBuffer(blocks, buffer, currentType, headingStack);
                    currentType = BlockType.TABLE;
                }
                buffer.append(line).append('\n');
                continue;
            }

            if (trimmed.isEmpty()) {
                flushBuffer(blocks, buffer, currentType, headingStack);
                currentType = BlockType.PARAGRAPH;
                continue;
            }

            if (currentType != BlockType.PARAGRAPH) {
                flushBuffer(blocks, buffer, currentType, headingStack);
                currentType = BlockType.PARAGRAPH;
            }
            buffer.append(line).append('\n');
        }
        flushBuffer(blocks, buffer, currentType, headingStack);
        return blocks;
    }

    private void flushBuffer(List<Block> blocks, StringBuilder buffer, BlockType type, List<String> headingStack) {
        if (buffer.isEmpty()) return;
        String content = buffer.toString().trim();
        if (!content.isEmpty()) {
            blocks.add(new Block(type, content, headingPath(headingStack)));
        }
        buffer.setLength(0);
    }

    private String headingPath(List<String> headingStack) {
        return String.join("\n", headingStack);
    }

    private int countLeadingHashes(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '#') i++;
        return Math.max(i, 1);
    }

    private List<Block> mergeSmallBlocks(List<Block> blocks) {
        List<Block> merged = new ArrayList<>();
        int i = 0;
        while (i < blocks.size()) {
            Block current = blocks.get(i);
            if (current.type() == BlockType.PARAGRAPH && current.content().length() < MIN_BLOCK_LENGTH) {
                StringBuilder combined = new StringBuilder(current.content());
                int j = i + 1;
                while (j < blocks.size() && combined.length() < MIN_BLOCK_LENGTH) {
                    Block next = blocks.get(j);
                    if (next.type() == BlockType.HEADING || next.type() == BlockType.TABLE || next.type() == BlockType.CODE) {
                        break;
                    }
                    combined.append("\n\n").append(next.content());
                    j++;
                }
                merged.add(new Block(current.type(), combined.toString(), current.headingPath()));
                i = j;
            } else {
                merged.add(current);
                i++;
            }
        }
        return merged;
    }

    private List<String> buildChunks(List<Block> blocks) {
        List<String> chunks = new ArrayList<>();
        int size = properties.chunkSize();
        int overlap = properties.chunkOverlap();

        for (Block block : blocks) {
            if (block.type() == BlockType.HEADING) {
                chunks.add(block.content());
                continue;
            }

            String path = block.headingPath();
            String content = block.content();
            int overhead = path.isEmpty() ? 0 : path.length() + 2;

            if (content.length() + overhead <= size) {
                chunks.add(prefixHeading(block));
            } else if (block.type() == BlockType.TABLE) {
                for (String part : splitTable(content, size - overhead, overlap)) {
                    chunks.add(path.isEmpty() ? part : path + "\n\n" + part);
                }
            } else {
                for (String part : splitLongText(content, size - overhead, overlap)) {
                    chunks.add(path.isEmpty() ? part : path + "\n\n" + part);
                }
            }
        }
        return chunks;
    }

    private String prefixHeading(Block block) {
        String path = block.headingPath();
        if (path == null || path.isEmpty()) {
            return block.content();
        }
        return path + "\n\n" + block.content();
    }

    private List<String> splitTable(String table, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] lines = table.split("\\r?\\n");
        List<String> headerLines = new ArrayList<>();
        List<String> dataLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            boolean isSeparator = trimmed.startsWith("|") && trimmed.contains("---");
            boolean headerDone = !headerLines.isEmpty() && headerLines.stream().anyMatch(h -> h.contains("---"));
            if (!headerDone && (headerLines.isEmpty() || isSeparator)) {
                headerLines.add(line);
            } else {
                dataLines.add(line);
            }
        }
        if (headerLines.isEmpty()) {
            headerLines.add(lines.length > 0 ? lines[0] : "");
        }

        String header = String.join("\n", headerLines);
        StringBuilder current = new StringBuilder(header);
        for (String row : dataLines) {
            if (current.length() + row.length() + 1 > size && current.length() > header.length()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder(header);
            }
            current.append('\n').append(row);
        }
        if (current.length() > header.length()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private List<String> splitLongText(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (size <= 0) size = 100;
        int step = Math.max(size - overlap, 50);
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + size, text.length());
            String chunk = text.substring(i, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) break;
        }
        return chunks;
    }
}
