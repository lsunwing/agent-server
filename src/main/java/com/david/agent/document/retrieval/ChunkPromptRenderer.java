package com.david.agent.document.retrieval;

import com.david.agent.document.config.RagProperties;
import com.david.agent.document.model.RagChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class ChunkPromptRenderer {

    private final RagProperties properties;

    public String render(List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[RAG Context - 以下是从知识库中检索到的相关内容，供参考]\n");
        int index = 1;
        for (RagChunk chunk : chunks) {
            sb.append(index++).append(". [").append(chunk.filePath()).append("] ")
                    .append(truncate(chunk.content()))
                    .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        int max = properties.chunkSize();
        return content.length() <= max ? content : content.substring(0, max) + "…";
    }
}
