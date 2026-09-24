package com.david.agent.document.schema.model;

import java.util.List;

public record SchemaDocument(
        String sourceName,
        String dialect,
        List<TableSchema> tables
) {
    public SchemaDocument {
        sourceName = sourceName == null ? "" : sourceName.trim();
        dialect = dialect == null || dialect.isBlank() ? "generic" : dialect.trim();
        tables = tables == null ? List.of() : List.copyOf(tables);
    }
}
