package com.david.agent.document.service;

import com.david.agent.document.config.RagProperties;
import com.david.agent.document.model.RagChunk;
import com.david.agent.document.model.RagDocument;
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
import java.nio.charset.StandardCharsets;
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

    public Mono<RagDocumentVO> upload(FilePart file) {
        String originalName = file.filename();
        String ext = fileStorage.resolveExtension(originalName);
        if (!properties.supportedTypes().contains(ext)) {
            return Mono.error(new IllegalArgumentException("Unsupported file type: " + ext));
        }
        return fileStorage.store(file)
                .flatMap(storedPath -> {
                    try {
                        String content = Files.readString(storedPath, StandardCharsets.UTF_8);
                        List<String> chunks = documentChunkRouter.chunk(content, originalName)
                                .stream().map(ChunkEnvelope::content).toList();
                        long fileSize = Files.size(storedPath);

                        RagDocument doc = new RagDocument(
                                null,
                                originalName,
                                storedPath.toString(),
                                ext,
                                fileSize,
                                chunks.size(),
                                null,
                                null
                        );
                        return documentStore.insertDocument(doc)
                                .flatMap(saved -> {
                                    List<RagChunk> chunkRecords = new java.util.ArrayList<>();
                                    for (int i = 0; i < chunks.size(); i++) {
                                        chunkRecords.add(new RagChunk(
                                                null, saved.id(), i, chunks.get(i), saved.fileName(), null));
                                    }
                                    if (chunkRecords.isEmpty()) {
                                        return Mono.just(saved);
                                    }
                                    return documentStore.insertChunks(chunkRecords).thenReturn(saved);
                                })
                                .map(this::toVO);
                    } catch (IOException e) {
                        return Mono.error(new RuntimeException("Failed to store file: " + e.getMessage(), e));
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
                    return documentStore.deleteDocument(id);
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
                        String content = Files.readString(filePath, StandardCharsets.UTF_8);
                        List<String> chunks = documentChunkRouter.chunk(content, doc.fileName())
                                .stream().map(ChunkEnvelope::content).toList();

                        return documentStore.deleteChunksByDocumentId(id)
                                .then(Mono.defer(() -> {
                                    List<RagChunk> chunkRecords = new java.util.ArrayList<>();
                                    for (int i = 0; i < chunks.size(); i++) {
                                        chunkRecords.add(new RagChunk(
                                                null, id, i, chunks.get(i), doc.fileName(), null));
                                    }
                                    RagDocument updated = new RagDocument(
                                            doc.id(), doc.fileName(), doc.filePath(), doc.fileType(),
                                            doc.fileSize(), chunks.size(), doc.createdAt(), null);
                                    if (chunkRecords.isEmpty()) {
                                        return documentStore.updateDocument(updated);
                                    }
                                    return documentStore.insertChunks(chunkRecords)
                                            .then(documentStore.updateDocument(updated));
                                }))
                                .map(this::toVO);
                    } catch (IOException e) {
                        return Mono.error(new RuntimeException("Failed to read file: " + e.getMessage(), e));
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
        return new RagChunkVO(chunk.id(), chunk.chunkIndex(), chunk.content(), chunk.filePath());
    }
}
