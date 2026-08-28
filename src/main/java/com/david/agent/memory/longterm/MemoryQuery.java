package com.david.agent.memory.longterm;

public record MemoryQuery(
        MemoryType type,
        String memoryKey,
        String keyword,
        Integer limit
) {
    public static MemoryQuery byType(MemoryType type) {
        return new MemoryQuery(type, null, null, null);
    }

    public static MemoryQuery keyword(String keyword, int limit) {
        return new MemoryQuery(null, null, keyword, limit);
    }
}
