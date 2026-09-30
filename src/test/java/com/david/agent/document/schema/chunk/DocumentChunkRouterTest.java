package com.david.agent.document.schema.chunk;

import com.david.agent.document.schema.config.SchemaRagProperties;
import com.david.agent.document.schema.parse.MarkdownDictParser;
import com.david.agent.document.service.TextChunker;
import com.david.agent.document.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkRouterTest {

    private static final Path TEMPLATE = Path.of("D:/document/AI/RAG/sd_tables.template.md");

    private DocumentChunkRouter router(SchemaRagProperties schemaProperties) {
        RagProperties ragProperties = new RagProperties(
                true, "./uploads/rag-test", "5MB", 5, 500, 50, List.of("md", "txt", "log"));
        return new DocumentChunkRouter(
                List.of(new MarkdownDictParser()),
                new DefaultSchemaChunkBuilder(schemaProperties),
                new TextChunker(ragProperties),
                schemaProperties
        );
    }

    private SchemaRagProperties schemaDefaults() {
        return new SchemaRagProperties(true, "auto", "generic",
                true, true, true, true, 2500, 8, 30);
    }

    @Test
    void routesTemplateToSchemaChunks() throws IOException {
        String content = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        List<ChunkEnvelope> chunks = router(schemaDefaults()).chunk(content, "sd_tables.template.md");

        assertEquals(8, chunks.size());
        assertTrue(chunks.stream().allMatch(c -> c.content().startsWith("# 表：")));
        assertTrue(chunks.stream().anyMatch(c -> c.metadata().tableName().equals("t_user_payment")));
    }

    @Test
    void routesPlainMarkdownToTextChunker() {
        String md = """
                # 父级

                ## 子级

                这是正文内容。
                """;
        List<ChunkEnvelope> chunks = router(schemaDefaults()).chunk(md, "design.md");
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(c -> "text".equals(c.metadata().section())));
    }

    @Test
    void schemaOffAlwaysUsesTextChunker() throws IOException {
        String content = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        SchemaRagProperties off = new SchemaRagProperties(true, "off", "generic",
                true, true, true, true, 2500, 8, 30);
        List<ChunkEnvelope> chunks = router(off).chunk(content, "sd_tables.template.md");
        assertTrue(chunks.stream().allMatch(c -> "text".equals(c.metadata().section())));
    }
}
