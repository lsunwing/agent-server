package com.david.agent.document.vo;

public record RagChunkVO(
        Long id,
        int chunkIndex,
        String content,
        String filePath,
        String headingPath,
        String chunkType
) {
}
