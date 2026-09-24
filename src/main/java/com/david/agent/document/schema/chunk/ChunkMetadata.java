package com.david.agent.document.schema.chunk;

import java.util.Map;

public record ChunkMetadata(
        String section,
        String tableName,
        String displayName,
        String columnNames,
        String sourceName,
        String dialect
) {
    public static final String SECTION_OVERVIEW = "overview";
    public static final String SECTION_COLUMNS = "columns";
    public static final String SECTION_INDEXES = "indexes";
    public static final String SECTION_RELATIONS = "relations";

    public ChunkMetadata {
        section = section == null ? "" : section;
        tableName = tableName == null ? "" : tableName;
        displayName = displayName == null ? "" : displayName;
        columnNames = columnNames == null ? "" : columnNames;
        sourceName = sourceName == null ? "" : sourceName;
        dialect = dialect == null ? "" : dialect;
    }

    public Map<String, String> asMap() {
        return Map.of(
                "section", section,
                "table_name", tableName,
                "display_name", displayName,
                "column_names", columnNames,
                "source_name", sourceName,
                "dialect", dialect
        );
    }
}
