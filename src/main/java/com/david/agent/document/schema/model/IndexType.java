package com.david.agent.document.schema.model;

public enum IndexType {
    PRIMARY,
    UNIQUE,
    NORMAL;

    public static IndexType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "primary", "主键", "primary key" -> PRIMARY;
            case "unique", "唯一", "unique key", "unique index" -> UNIQUE;
            default -> NORMAL;
        };
    }
}
