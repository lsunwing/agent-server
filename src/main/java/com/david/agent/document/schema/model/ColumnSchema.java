package com.david.agent.document.schema.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record ColumnSchema(
        String name,
        String type,
        Boolean nullable,
        String defaultValue,
        String comment,
        Map<String, String> enums,
        boolean primaryKey,
        boolean foreignKey,
        String refTable,
        String refColumn
) {
    public ColumnSchema {
        name = name == null ? "" : name.trim();
        type = type == null ? "" : type.trim();
        defaultValue = defaultValue == null ? "" : defaultValue.trim();
        comment = comment == null ? "" : comment.trim();
        enums = enums == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(enums));
        refTable = refTable == null ? "" : refTable.trim();
        refColumn = refColumn == null ? "" : refColumn.trim();
    }
}
