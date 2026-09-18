package com.david.agent.document.service;

import com.david.agent.document.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    private TextChunker chunker;

    @BeforeEach
    void setUp() {
        // chunkSize 故意调小，便于触发表格/列表拆分路径
        RagProperties properties = new RagProperties(
                true, "./uploads/rag-test", "5MB", 5, 300, 50, List.of("md", "txt", "log"));
        chunker = new TextChunker(properties);
    }

    @Test
    void tableShouldNotBeSplitMidRow() {
        StringBuilder md = new StringBuilder();
        md.append("# 字段字典\n\n");
        md.append("| 字段名 | 类型 | 说明 |\n");
        md.append("| --- | --- | --- |\n");
        for (int i = 0; i < 40; i++) {
            md.append("| col_").append(i)
                    .append(" | varchar | 这是第").append(i).append("个字段的较长说明文案，用于撑大表格触发拆分 |\n");
        }

        List<String> chunks = chunker.chunk(md.toString());
        assertTrue(chunks.size() >= 2, "大表应拆成多个 chunk, actual=" + chunks.size());

        List<String> tableChunks = chunks.stream()
                .filter(c -> c.contains("| 字段名 |") || c.contains("| col_"))
                .toList();
        assertTrue(tableChunks.size() >= 2, "表格数据应拆成多个 batch, actual=" + tableChunks.size());

        for (String chunk : tableChunks) {
            assertTrue(chunk.contains("| 字段名 | 类型 | 说明 |"), "每个表格 chunk 都应重复表头: " + preview(chunk));
            // 每一行数据都必须完整（以 | 开头且以 | 结束的行）
            for (String line : chunk.lines().toList()) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || t.startsWith("| ---")) {
                    continue;
                }
                if (t.startsWith("|")) {
                    assertTrue(t.endsWith("|"), "表格行必须完整闭合: " + t);
                }
            }
        }
    }

    @Test
    void smallTableKeptWhole() {
        String md = """
                ## 小表

                | a | b |
                | --- | --- |
                | 1 | 2 |
                | 3 | 4 |
                """;
        List<String> chunks = chunker.chunk(md);
        assertEquals(2, chunks.size()); // 1 heading + 1 table
        assertTrue(chunks.get(1).contains("| 1 | 2 |"));
        assertTrue(chunks.get(1).contains("| 3 | 4 |"));
        assertTrue(chunks.get(1).contains("## 小表"), "应带上标题路径前缀");
    }

    @Test
    void codeBlockNotBrokenByWindow() {
        String code = "```\n" + "line1\n".repeat(80) + "```\n";
        List<String> chunks = chunker.chunk("# 示例\n\n" + code);
        long codeChunks = chunks.stream().filter(c -> c.contains("```") || c.contains("line1")).count();
        assertTrue(codeChunks >= 1);
        for (String c : chunks) {
            if (c.contains("line1")) {
                // 按行切时不应出现半个 "line"
                assertFalse(c.contains("line") && !c.contains("line1") && !c.contains("line1\n") && c.contains("lin"),
                        "代码行不应被字符窗口切断: " + preview(c));
            }
        }
    }

    @Test
    void headingPathPrefixed() {
        String md = """
                # 父级

                ## 子级

                这是正文内容。
                """;
        List<String> chunks = chunker.chunk(md);
        assertTrue(chunks.stream().anyMatch(c -> c.contains("# 父级") && c.contains("## 子级") && c.contains("这是正文内容")),
                "正文 chunk 应包含标题路径: " + chunks);
    }

    @Test
    void emptyInput() {
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    private String preview(String s) {
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
    }
}
