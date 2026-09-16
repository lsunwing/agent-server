package com.david.agent.document.store;

import com.david.agent.document.model.RagChunk;
import com.david.agent.document.model.RagDocument;
import com.david.agent.document.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Slf4j
@Component
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class SqliteDocumentStore implements DocumentStore {

    private final JdbcTemplate jdbc;

    public SqliteDocumentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initSchema();
    }

    private void initSchema() {
        jdbc.execute("PRAGMA foreign_keys = ON");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS rag_document (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_name    TEXT    NOT NULL,
                    file_path    TEXT    NOT NULL UNIQUE,
                    file_type    TEXT    NOT NULL,
                    file_size    INTEGER NOT NULL DEFAULT 0,
                    chunk_count  INTEGER NOT NULL DEFAULT 0,
                    created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
                    updated_at   TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS rag_chunk (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    document_id  INTEGER NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
                    chunk_index  INTEGER NOT NULL,
                    content      TEXT    NOT NULL,
                    file_path    TEXT    NOT NULL,
                    created_at   TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_rag_chunk_doc ON rag_chunk(document_id)");
        log.info("[rag] SQLite document store initialized");
    }

    @Override
    public Mono<RagDocument> insertDocument(RagDocument doc) {
        return blocking(() -> {
            jdbc.update("""
                            INSERT INTO rag_document (file_name, file_path, file_type, file_size, chunk_count)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    doc.fileName(), doc.filePath(), doc.fileType(), doc.fileSize(), doc.chunkCount());
            Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
            return queryDocumentById(id);
        });
    }

    @Override
    public Mono<RagDocument> updateDocument(RagDocument doc) {
        return blocking(() -> {
            jdbc.update("""
                            UPDATE rag_document SET file_name=?, file_path=?, file_type=?, file_size=?, chunk_count=?, updated_at=datetime('now')
                            WHERE id=?
                            """,
                    doc.fileName(), doc.filePath(), doc.fileType(), doc.fileSize(), doc.chunkCount(), doc.id());
            return queryDocumentById(doc.id());
        });
    }

    @Override
    public Mono<List<RagDocument>> findAllDocuments() {
        return blocking(() -> jdbc.query(
                "SELECT * FROM rag_document ORDER BY updated_at DESC",
                this::mapDocument));
    }

    @Override
    public Mono<RagDocument> findDocumentById(Long id) {
        return blocking(() -> queryDocumentById(id));
    }

    @Override
    public Mono<Void> deleteDocument(Long id) {
        return blocking(() -> {
            jdbc.update("DELETE FROM rag_document WHERE id = ?", id);
            return null;
        }).then();
    }

    @Override
    public Mono<List<RagChunk>> insertChunks(List<RagChunk> chunks) {
        return blocking(() -> {
            List<RagChunk> result = new ArrayList<>();
            for (RagChunk chunk : chunks) {
                jdbc.update("""
                                INSERT INTO rag_chunk (document_id, chunk_index, content, file_path)
                                VALUES (?, ?, ?, ?)
                                """,
                        chunk.documentId(), chunk.chunkIndex(), chunk.content(), chunk.filePath());
                Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
                result.add(queryChunkById(id));
            }
            return result;
        });
    }

    @Override
    public Mono<List<RagChunk>> findChunksByDocumentId(Long documentId) {
        return blocking(() -> jdbc.query(
                "SELECT * FROM rag_chunk WHERE document_id = ? ORDER BY chunk_index",
                this::mapChunk, documentId));
    }

    @Override
    public Mono<List<RagChunk>> searchChunks(String query, int limit) {
        return blocking(() -> {
            if (query == null || query.isBlank()) {
                return List.of();
            }
            String like = "%" + query.trim() + "%";
            return jdbc.query(
                    "SELECT * FROM rag_chunk WHERE content LIKE ? OR file_path LIKE ? ORDER BY id LIMIT ?",
                    this::mapChunk, like, like, limit);
        });
    }

    @Override
    public Mono<Void> deleteChunksByDocumentId(Long documentId) {
        return blocking(() -> {
            jdbc.update("DELETE FROM rag_chunk WHERE document_id = ?", documentId);
            return null;
        }).then();
    }

    private RagDocument queryDocumentById(Long id) {
        List<RagDocument> result = jdbc.query("SELECT * FROM rag_document WHERE id = ?", this::mapDocument, id);
        return result.isEmpty() ? null : result.get(0);
    }

    private RagChunk queryChunkById(Long id) {
        List<RagChunk> result = jdbc.query("SELECT * FROM rag_chunk WHERE id = ?", this::mapChunk, id);
        return result.isEmpty() ? null : result.get(0);
    }

    private RagDocument mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new RagDocument(
                rs.getLong("id"),
                rs.getString("file_name"),
                rs.getString("file_path"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getInt("chunk_count"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private RagChunk mapChunk(ResultSet rs, int rowNum) throws SQLException {
        return new RagChunk(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("file_path"),
                rs.getString("created_at")
        );
    }

    private <T> Mono<T> blocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
