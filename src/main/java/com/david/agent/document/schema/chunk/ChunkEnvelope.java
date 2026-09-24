package com.david.agent.document.schema.chunk;

public record ChunkEnvelope(
        String content,
        ChunkMetadata metadata,
        int order
) {
    public ChunkEnvelope {
        content = content == null ? "" : content;
        metadata = metadata == null
                ? new ChunkMetadata("", "", "", "", "", "")
                : metadata;
    }
}
