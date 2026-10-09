package com.david.agent.document.model;

public record RagChunk(
        Long id,
        Long documentId,
        int chunkIndex,
        String content,
        String filePath,
        String headingPath,
        String chunkType,
        String createdAt
) {
    public RagChunk {
        content = content == null ? "" : content;
        filePath = filePath == null ? "" : filePath;
        headingPath = headingPath == null ? "" : headingPath;
        chunkType = chunkType == null ? "paragraph" : chunkType;
    }
}
