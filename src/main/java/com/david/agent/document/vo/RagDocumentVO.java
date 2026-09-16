package com.david.agent.document.vo;

public record RagDocumentVO(
        Long id,
        String fileName,
        String fileType,
        int chunkCount,
        long fileSize,
        String createdAt,
        String updatedAt
) {
}
