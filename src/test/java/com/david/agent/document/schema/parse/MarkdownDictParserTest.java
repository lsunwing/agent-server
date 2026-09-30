package com.david.agent.document.schema.parse;

import com.david.agent.document.schema.model.ColumnSchema;
import com.david.agent.document.schema.model.IndexType;
import com.david.agent.document.schema.model.RelationSchema;
import com.david.agent.document.schema.model.SchemaDocument;
import com.david.agent.document.schema.model.TableSchema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDictParserTest {

    private static final Path TEMPLATE = Path.of("D:/document/AI/RAG/sd_tables.template.md");

    private final MarkdownDictParser parser = new MarkdownDictParser();

    private String loadTemplate() throws IOException {
        return Files.readString(TEMPLATE, StandardCharsets.UTF_8);
    }

    @Test
    void supportsTemplate() throws IOException {
        assertTrue(parser.supports(loadTemplate(), "sd_tables.template.md"));
    }

    @Test
    void doesNotSupportPlainDoc() {
        String md = """
                # 设计说明

                ## 背景

                这是一篇普通设计文档，没有数据字典表。
                """;
        assertFalse(parser.supports(md, "design.md"));
    }

    @Test
    void parseTwoTables() throws IOException {
        SchemaDocument doc = parser.parse(loadTemplate(), "sd_tables.template.md");
        assertEquals(2, doc.tables().size());
        assertEquals("t_user_arrears", doc.tables().get(0).name());
        assertEquals("t_user_payment", doc.tables().get(1).name());
        assertEquals("用户欠费信息表", doc.tables().get(0).displayName());
    }

    @Test
    void parseArrearsColumns() throws IOException {
        SchemaDocument doc = parser.parse(loadTemplate(), "sd_tables.template.md");
        TableSchema table = doc.tables().get(0);
        assertEquals(13, table.columns().size());

        ColumnSchema status = table.columns().stream()
                .filter(c -> "arrears_status".equals(c.name()))
                .findFirst().orElseThrow();
        assertEquals("TINYINT", status.type());
        assertEquals(Boolean.FALSE, status.nullable());
        assertEquals("1", status.defaultValue());
        assertTrue(status.comment().contains("欠费状态"));
        assertEquals(5, status.enums().size());
        assertEquals("正常欠费", status.enums().get("1"));

        ColumnSchema id = table.columns().stream()
                .filter(c -> "id".equals(c.name()))
                .findFirst().orElseThrow();
        assertTrue(id.primaryKey());
    }

    @Test
    void parseIndexes() throws IOException {
        SchemaDocument doc = parser.parse(loadTemplate(), "sd_tables.template.md");
        TableSchema table = doc.tables().get(0);
        assertEquals(5, table.indexes().size());

        assertTrue(table.indexes().stream().anyMatch(
                i -> "uk_account_bill".equals(i.name())
                        && i.type() == IndexType.UNIQUE
                        && i.columns().equals(List.of("account_no", "bill_month"))));
        assertTrue(table.indexes().stream().anyMatch(i -> i.type() == IndexType.PRIMARY));
    }

    @Test
    void parseRelations() throws IOException {
        SchemaDocument doc = parser.parse(loadTemplate(), "sd_tables.template.md");
        TableSchema payment = doc.tables().get(1);
        assertEquals(2, payment.relations().size());

        RelationSchema user = payment.relations().stream()
                .filter(r -> "user_id".equals(r.fromColumn()))
                .findFirst().orElseThrow();
        assertEquals("t_user_profile", user.toTable());
        assertEquals("id", user.toColumn());

        ColumnSchema arrearsId = payment.columns().stream()
                .filter(c -> "arrears_id".equals(c.name()))
                .findFirst().orElseThrow();
        assertTrue(arrearsId.foreignKey());
        assertEquals("t_user_arrears", arrearsId.refTable());
    }

    @Test
    void purposeParsed() throws IOException {
        SchemaDocument doc = parser.parse(loadTemplate(), "sd_tables.template.md");
        assertNotNull(doc.tables().get(0).purpose());
        assertFalse(doc.tables().get(0).purpose().isBlank());
    }
}
