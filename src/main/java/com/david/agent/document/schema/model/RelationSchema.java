package com.david.agent.document.schema.model;

public record RelationSchema(
        String fromColumn,
        String toTable,
        String toColumn,
        String description
) {
    public RelationSchema {
        fromColumn = fromColumn == null ? "" : fromColumn.trim();
        toTable = toTable == null ? "" : toTable.trim();
        toColumn = toColumn == null ? "" : toColumn.trim();
        description = description == null ? "" : description.trim();
    }
}
