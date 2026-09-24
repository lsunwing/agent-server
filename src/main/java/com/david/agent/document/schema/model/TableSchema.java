package com.david.agent.document.schema.model;

import java.util.List;

public record TableSchema(
        String name,
        String displayName,
        String purpose,
        String domain,
        List<ColumnSchema> columns,
        List<IndexSchema> indexes,
        List<RelationSchema> relations
) {
    public TableSchema {
        name = name == null ? "" : name.trim();
        displayName = displayName == null ? "" : displayName.trim();
        purpose = purpose == null ? "" : purpose.trim();
        domain = domain == null ? "" : domain.trim();
        columns = columns == null ? List.of() : List.copyOf(columns);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    public String title() {
        return displayName.isBlank() ? name : name + " · " + displayName;
    }
}
