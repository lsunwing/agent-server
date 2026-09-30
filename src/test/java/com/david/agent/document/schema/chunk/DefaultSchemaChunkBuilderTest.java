package com.david.agent.document.schema.chunk;

import com.david.agent.document.schema.config.SchemaRagProperties;
import com.david.agent.document.schema.model.ColumnSchema;
import com.david.agent.document.schema.model.IndexSchema;
import com.david.agent.document.schema.model.IndexType;
import com.david.agent.document.schema.model.RelationSchema;
import com.david.agent.document.schema.model.SchemaDocument;
import com.david.agent.document.schema.model.TableSchema;
import com.david.agent.document.schema.parse.MarkdownDictParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSchemaChunkBuilderTest {

    private static final Path TEMPLATE = Path.of("D:/document/AI/RAG/sd_tables.template.md");

    private DefaultSchemaChunkBuilder builder(SchemaRagProperties properties) {
        return new DefaultSchemaChunkBuilder(properties);
    }

    private SchemaRagProperties defaults() {
        return new SchemaRagProperties(true, "auto", "generic",
                true, true, true, true, 2500, 8, 30);
    }

    private SchemaDocument parseTemplate() throws IOException {
        String content = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        return new MarkdownDictParser().parse(content, "sd_tables.template.md");
    }

    @Test
    void buildsMultiViewForTemplate() throws IOException {
        List<ChunkEnvelope> chunks = builder(defaults()).build(parseTemplate());

        // 2 tables × (overview + columns + indexes + relations)
        assertEquals(8, chunks.size());

        long overviews = chunks.stream()
                .filter(c -> ChunkMetadata.SECTION_OVERVIEW.equals(c.metadata().section())).count();
        long columns = chunks.stream()
                .filter(c -> ChunkMetadata.SECTION_COLUMNS.equals(c.metadata().section())).count();
        long indexes = chunks.stream()
                .filter(c -> ChunkMetadata.SECTION_INDEXES.equals(c.metadata().section())).count();
        long relations = chunks.stream()
                .filter(c -> ChunkMetadata.SECTION_RELATIONS.equals(c.metadata().section())).count();

        assertEquals(2, overviews);
        assertEquals(2, columns);
        assertEquals(2, indexes);
        assertEquals(2, relations);
    }

    @Test
    void overviewContainsIdentityAndEnumsHint() throws IOException {
        List<ChunkEnvelope> chunks = builder(defaults()).build(parseTemplate());
        String overview = chunks.stream()
                .filter(c -> ChunkMetadata.SECTION_OVERVIEW.equals(c.metadata().section())
                        && "t_user_arrears".equals(c.metadata().tableName()))
                .findFirst().orElseThrow().content();

        assertTrue(overview.startsWith("# 表：t_user_arrears（用户欠费信息表）"));
        assertTrue(overview.contains("**用途**"));
        assertTrue(overview.contains("arrears_status"));
        assertTrue(overview.contains("uk_account_bill"));
        assertTrue(overview.contains("t_user_profile"));
    }

    @Test
    void columnsChunkKeepsWholeTableWhenFits() throws IOException {
        List<ChunkEnvelope> chunks = builder(defaults()).build(parseTemplate());
        ChunkEnvelope columns = chunks.stream()
                .filter(c -> ChunkMetadata.SECTION_COLUMNS.equals(c.metadata().section())
                        && "t_user_arrears".equals(c.metadata().tableName()))
                .findFirst().orElseThrow();

        assertTrue(columns.content().startsWith("# 表：t_user_arrears（用户欠费信息表） · 字段信息"));
        assertTrue(columns.content().contains("| id |"));
        assertTrue(columns.content().contains("| arrears_status |"));
        assertTrue(columns.content().contains("| operator |"));
        assertFalse(columns.content().contains(" 1/"));
        for (String line : columns.content().lines().toList()) {
            String t = line.trim();
            if (t.startsWith("|") && !t.startsWith("|--") && !t.startsWith("| :")) {
                assertTrue(t.endsWith("|"), "table row must close: " + t);
            }
        }
    }

    @Test
    void largeColumnTableSplitsWithHeaderRepeat() {
        List<ColumnSchema> columns = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            columns.add(new ColumnSchema("col_" + i, "VARCHAR(50)", false, "-", "说明".repeat(20), Map.of(),
                    false, false, "", ""));
        }
        TableSchema table = new TableSchema("t_big", "大表", "用途", "",
                columns,
                List.of(new IndexSchema("PRIMARY", IndexType.PRIMARY, List.of("id"))),
                List.of());
        SchemaDocument doc = new SchemaDocument("big.md", "mysql", List.of(table));

        SchemaRagProperties properties = new SchemaRagProperties(true, "auto", "generic",
                true, true, true, false, 400, 5, 30);
        List<ChunkEnvelope> chunks = builder(properties).build(doc);
        List<ChunkEnvelope> columnChunks = chunks.stream()
                .filter(c -> ChunkMetadata.SECTION_COLUMNS.equals(c.metadata().section()))
                .toList();

        assertTrue(columnChunks.size() >= 2);
        for (ChunkEnvelope chunk : columnChunks) {
            assertTrue(chunk.content().contains("# 表：t_big（大表）"));
            assertTrue(chunk.content().contains("| 字段名 |"));
            assertTrue(chunk.content().contains("· 字段信息"));
        }
    }

    @Test
    void orderIsStable() throws IOException {
        List<ChunkEnvelope> chunks = builder(defaults()).build(parseTemplate());
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).order());
        }
    }

    @Test
    void noBareHeadingChunks() throws IOException {
        List<ChunkEnvelope> chunks = builder(defaults()).build(parseTemplate());
        for (ChunkEnvelope chunk : chunks) {
            assertFalse(chunk.content().isBlank());
            assertTrue(chunk.content().startsWith("# 表："), "chunk must carry table identity: " + chunk.content());
        }
    }
}
