package com.david.agent.document.schema.model;

import java.util.List;

public record IndexSchema(
        String name,
        IndexType type,
        List<String> columns
) {
    public IndexSchema {
        name = name == null ? "" : name.trim();
        type = type == null ? IndexType.NORMAL : type;
        columns = columns == null ? List.of() : columns.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
