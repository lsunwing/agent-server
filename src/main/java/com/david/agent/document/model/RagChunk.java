package com.david.agent.document.model;

public record RagChunk(
        Long id,
        Long documentId,
        int chunkIndex,
        String content,
        String filePath,
        String createdAt
) {
    public RagChunk {
        content = content == null ? "" : content;
        filePath = filePath == null ? "" : filePath;
    }
}
