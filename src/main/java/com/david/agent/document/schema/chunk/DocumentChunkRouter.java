package com.david.agent.document.schema.chunk;

import com.david.agent.document.schema.config.SchemaRagProperties;
import com.david.agent.document.schema.parse.SchemaDocumentParser;
import com.david.agent.document.service.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块路由：Schema 文档走 SchemaChunkBuilder，其余走通用 TextChunker。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentChunkRouter {

    private final List<SchemaDocumentParser> parsers;
    private final SchemaChunkBuilder schemaChunkBuilder;
    private final TextChunker textChunker;
    private final SchemaRagProperties schemaProperties;

    public List<ChunkEnvelope> chunk(String content, String fileName) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        if (schemaProperties.isOff()) {
            return fromTextChunker(content, fileName);
        }

        SchemaDocumentParser parser = resolveParser(content, fileName);
        if (parser == null) {
            if (schemaProperties.isForceSchema()) {
                log.warn("[schema] force-schema enabled but no parser supports file={}", fileName);
            }
            return fromTextChunker(content, fileName);
        }

        try {
            return schemaChunkBuilder.build(parser.parse(content, fileName));
        } catch (Exception e) {
            log.warn("[schema] parse failed, fallback to TextChunker, file={}", fileName, e);
            return fromTextChunker(content, fileName);
        }
    }

    private SchemaDocumentParser resolveParser(String content, String fileName) {
        if (schemaProperties.isForceSchema()) {
            return parsers.isEmpty() ? null : parsers.get(0);
        }
        for (SchemaDocumentParser parser : parsers) {
            if (parser.supports(content, fileName)) {
                return parser;
            }
        }
        return null;
    }

    private List<ChunkEnvelope> fromTextChunker(String content, String fileName) {
        List<String> plain = textChunker.chunk(content);
        List<ChunkEnvelope> result = new ArrayList<>(plain.size());
        int order = 0;
        for (String piece : plain) {
            ChunkMetadata metadata = new ChunkMetadata(
                    "text", "", "", "", fileName == null ? "" : fileName, "");
            result.add(new ChunkEnvelope(piece, metadata, order++));
        }
        return result;
    }
}
