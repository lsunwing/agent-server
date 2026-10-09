package com.david.agent.document.service;

import com.david.agent.document.config.RagProperties;
import com.david.agent.document.model.RagChunk;
import com.david.agent.document.model.RagDocument;
import com.david.agent.document.parse.DocumentParseService;
import com.david.agent.document.schema.chunk.ChunkEnvelope;
import com.david.agent.document.schema.chunk.DocumentChunkRouter;
import com.david.agent.document.store.DocumentStore;
import com.david.agent.document.vo.RagChunkVO;
import com.david.agent.document.vo.RagDocumentDetailVO;
import com.david.agent.document.vo.RagDocumentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentRagService {

    private final DocumentStore documentStore;
    private final DocumentChunkRouter documentChunkRouter;
    private final FileStorage fileStorage;
    private final RagProperties properties;
    private final DocumentParseService documentParseService;

    public Mono<RagDocumentVO> upload(FilePart file) {
        String originalName = file.filename();
        String ext = fileStorage.resolveExtension(originalName);
        if (!properties.supportedTypes().contains(ext) && !documentParseService.supports(ext)) {
            return Mono.error(new IllegalArgumentException("Unsupported file type: " + ext));
        }
        return fileStorage.store(file)
                .flatMap(storedPath -> {
                    try {
                        String content = documentParseService.extract(storedPath, ext);
                        List<ChunkEnvelope> envelopes = documentChunkRouter.chunk(content, originalName);
                        long fileSize = Files.size(storedPath);

                        RagDocument doc = new RagDocument(
                                null,
                                originalName,
                                storedPath.toString(),
                                ext,
                                fileSize,
                                envelopes.size(),
                                null,
                                null
                        );
                        return documentStore.insertDocument(doc)
                                .flatMap(saved -> {
                                    List<RagChunk> chunkRecords = new java.util.ArrayList<>();
                                    for (int i = 0; i < envelopes.size(); i++) {
                                        ChunkEnvelope env = envelopes.get(i);
                                        chunkRecords.add(new RagChunk(
                                                null, saved.id(), i, env.content(), saved.fileName(),
                                                env.metadata().section(), env.metadata().tableName(), null));
                                    }
                                    if (chunkRecords.isEmpty()) {
                                        return Mono.just(saved);
                                    }
                                    return documentStore.insertChunks(chunkRecords).thenReturn(saved);
                                })
                                .map(this::toVO);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to parse file: " + e.getMessage(), e));
                    }
                });
    }

    public Mono<List<RagDocumentVO>> listAll() {
        return documentStore.findAllDocuments()
                .map(docs -> docs.stream().map(this::toVO).toList());
    }

    public Mono<RagDocumentDetailVO> getById(Long id) {
        return documentStore.findDocumentById(id)
                .flatMap(doc -> documentStore.findChunksByDocumentId(id)
                        .map(chunks -> new RagDocumentDetailVO(
                                toVO(doc),
                                chunks.stream().map(this::toChunkVO).toList()
                        )));
    }

    public Mono<Void> delete(Long id) {
        return documentStore.findDocumentById(id)
                .flatMap(doc -> {
                    fileStorage.delete(Path.of(doc.filePath()));
                    // 先删 chunk 再删 document，避免残留孤儿数据
                    return documentStore.deleteChunksByDocumentId(id)
                            .then(documentStore.deleteDocument(id));
                });
    }

    public Mono<RagDocumentVO> reindex(Long id) {
        return documentStore.findDocumentById(id)
                .flatMap(doc -> {
                    Path filePath = Path.of(doc.filePath());
                    if (!Files.exists(filePath)) {
                        return Mono.error(new IllegalStateException("File not found: " + doc.filePath()));
                    }
                    try {
                        String content = documentParseService.extract(filePath, doc.fileType());
                        List<ChunkEnvelope> envelopes = documentChunkRouter.chunk(content, doc.fileName());

                        return documentStore.deleteChunksByDocumentId(id)
                                .then(Mono.defer(() -> {
                                    List<RagChunk> chunkRecords = new java.util.ArrayList<>();
                                    for (int i = 0; i < envelopes.size(); i++) {
                                        ChunkEnvelope env = envelopes.get(i);
                                        chunkRecords.add(new RagChunk(
                                                null, id, i, env.content(), doc.fileName(),
                                                env.metadata().section(), env.metadata().tableName(), null));
                                    }
                                    RagDocument updated = new RagDocument(
                                            doc.id(), doc.fileName(), doc.filePath(), doc.fileType(),
                                            doc.fileSize(), envelopes.size(), doc.createdAt(), null);
                                    if (chunkRecords.isEmpty()) {
                                        return documentStore.updateDocument(updated);
                                    }
                                    return documentStore.insertChunks(chunkRecords)
                                            .then(documentStore.updateDocument(updated));
                                }))
                                .map(this::toVO);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to parse file: " + e.getMessage(), e));
                    }
                });
    }

    public Mono<List<RagChunkVO>> search(String query, int limit) {
        return documentStore.searchChunks(query, limit)
                .map(chunks -> chunks.stream().map(this::toChunkVO).toList());
    }

    private RagDocumentVO toVO(RagDocument doc) {
        return new RagDocumentVO(
                doc.id(), doc.fileName(), doc.fileType(),
                doc.chunkCount(), doc.fileSize(), doc.createdAt(), doc.updatedAt());
    }

    private RagChunkVO toChunkVO(RagChunk chunk) {
        return new RagChunkVO(chunk.id(), chunk.chunkIndex(), chunk.content(), chunk.filePath(),
                chunk.headingPath(), chunk.chunkType());
    }
}
