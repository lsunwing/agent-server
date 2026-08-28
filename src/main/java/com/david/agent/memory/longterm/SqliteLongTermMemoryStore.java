package com.david.agent.memory.longterm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Slf4j
@Component
@ConditionalOnProperty(name = "agent.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
public class SqliteLongTermMemoryStore implements LongTermMemoryStore {

    private static final String SELECT = """
            SELECT id, type, memory_key, content, importance, status,
                   source_conversation_id, metadata, created_at, updated_at
            FROM long_term_memory
            """;

    private final JdbcTemplate jdbc;

    public SqliteLongTermMemoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initSchema();
    }

    private void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS long_term_memory (
                    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
                    type                   TEXT    NOT NULL,
                    memory_key             TEXT,
                    content                TEXT    NOT NULL,
                    importance             INTEGER NOT NULL DEFAULT 5,
                    status                 TEXT    NOT NULL DEFAULT 'ACTIVE',
                    source_conversation_id TEXT,
                    metadata               TEXT,
                    created_at             TEXT    NOT NULL DEFAULT (datetime('now')),
                    updated_at             TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ltm_type_status ON long_term_memory(type, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ltm_key ON long_term_memory(memory_key)");
        log.info("SQLite long-term memory store initialized");
    }

    @Override
    public Mono<Memory> insert(Memory memory) {
        return blocking(() -> {
            jdbc.update("""
                    INSERT INTO long_term_memory (type, memory_key, content, importance, status, source_conversation_id, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    memory.type().name(),
                    memory.memoryKey(),
                    memory.content(),
                    memory.importance(),
                    memory.status() == null ? MemoryStatus.ACTIVE.name() : memory.status().name(),
                    memory.sourceConversationId(),
                    memory.metadata()
            );
            Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
            Memory saved = queryById(id);
            log.info("[memory-store] INSERT ok, id={}, type={}, key={}, importance={}, status={}",
                    saved == null ? id : saved.id(),
                    memory.type(), memory.memoryKey(), memory.importance(),
                    memory.status() == null ? MemoryStatus.ACTIVE : memory.status());
            return saved;
        });
    }

    @Override
    public Mono<Memory> getById(Long id) {
        return blocking(() -> queryById(id));
    }

    @Override
    public Mono<List<Memory>> findActiveByType(MemoryType type) {
        return findActive(MemoryQuery.byType(type));
    }

    @Override
    public Mono<List<Memory>> findActive(MemoryQuery query) {
        return blocking(() -> {
            MemoryQuery q = query == null ? new MemoryQuery(null, null, null, null) : query;
            StringBuilder where = new StringBuilder("status = 'ACTIVE'");
            List<Object> args = new ArrayList<>();
            if (q.type() != null) {
                where.append(" AND type = ?");
                args.add(q.type().name());
            }
            if (hasText(q.memoryKey())) {
                where.append(" AND memory_key = ?");
                args.add(q.memoryKey());
            }
            if (hasText(q.keyword())) {
                where.append(" AND (content LIKE ? OR IFNULL(memory_key, '') LIKE ?)");
                args.add(like(q.keyword()));
                args.add(like(q.keyword()));
            }
            String sql = SELECT + " WHERE " + where + " ORDER BY importance DESC, updated_at DESC";
            if (q.limit() != null && q.limit() > 0) {
                sql += " LIMIT ?";
                args.add(q.limit());
            }
            return jdbc.query(sql, this::mapRow, args.toArray());
        });
    }

    @Override
    public Mono<Integer> updateStatus(Long id, MemoryStatus status) {
        return blocking(() -> jdbc.update(
                "UPDATE long_term_memory SET status = ?, updated_at = datetime('now') WHERE id = ?",
                status.name(), id
        ));
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return blocking(() -> jdbc.update("DELETE FROM long_term_memory WHERE id = ?", id));
    }

    private Memory queryById(Long id) {
        List<Memory> result = jdbc.query(SELECT + " WHERE id = ?", this::mapRow, id);
        return result.isEmpty() ? null : result.get(0);
    }

    private Memory mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Memory(
                rs.getLong("id"),
                MemoryType.valueOf(rs.getString("type")),
                rs.getString("memory_key"),
                rs.getString("content"),
                rs.getInt("importance"),
                MemoryStatus.valueOf(rs.getString("status")),
                rs.getString("source_conversation_id"),
                rs.getString("metadata"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private String like(String keyword) {
        return "%" + keyword.trim() + "%";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> Mono<T> blocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
