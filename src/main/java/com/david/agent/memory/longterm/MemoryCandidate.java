package com.david.agent.memory.longterm;

public record MemoryCandidate(
        MemoryType type,
        String memoryKey,
        String content,
        int importance
) {
    public MemoryCandidate {
        type = type == null ? MemoryType.FACT : type;
        memoryKey = memoryKey == null ? "" : memoryKey.trim();
        content = content == null ? "" : content.trim();
    }
}
