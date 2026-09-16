package com.david.agent.document.model;

public record RagDocument(
        Long id,
        String fileName,
        String filePath,
        String fileType,
        long fileSize,
        int chunkCount,
        String createdAt,
        String updatedAt
) {
    public RagDocument {
        fileName = fileName == null ? "" : fileName;
        filePath = filePath == null ? "" : filePath;
        fileType = fileType == null ? "" : fileType;
    }
}
