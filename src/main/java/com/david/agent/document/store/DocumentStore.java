package com.david.agent.document.store;

import com.david.agent.document.model.RagChunk;
import com.david.agent.document.model.RagDocument;
import reactor.core.publisher.Mono;

import java.util.List;

public interface DocumentStore {

    Mono<RagDocument> insertDocument(RagDocument doc);

    Mono<RagDocument> updateDocument(RagDocument doc);

    Mono<List<RagDocument>> findAllDocuments();

    Mono<RagDocument> findDocumentById(Long id);

    Mono<Void> deleteDocument(Long id);

    Mono<List<RagChunk>> insertChunks(List<RagChunk> chunks);

    Mono<List<RagChunk>> findChunksByDocumentId(Long documentId);

    Mono<List<RagChunk>> searchChunks(String query, int limit);

    Mono<Void> deleteChunksByDocumentId(Long documentId);
}
