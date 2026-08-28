package com.david.agent.memory.longterm;

public record Memory(
        Long id,
        MemoryType type,
        String memoryKey,
        String content,
        int importance,
        MemoryStatus status,
        String sourceConversationId,
        String metadata,
        String createdAt,
        String updatedAt
) {
    public static Memory newActive(MemoryType type, String memoryKey, String content, int importance, String conversationId) {
        return new Memory(null, type, memoryKey, content, importance, MemoryStatus.ACTIVE, conversationId, null, null, null);
    }
}
