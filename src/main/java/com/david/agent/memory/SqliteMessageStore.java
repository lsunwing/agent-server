package com.david.agent.memory;

import com.david.agent.agent.message.Message;
import com.david.agent.agent.message.MessageRole;
import com.david.agent.model.ToolCall;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "agent.memory.type", havingValue = "sqlite", matchIfMissing = true)
public class SqliteMessageStore implements MessageStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SqliteMessageStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        initSchema();
    }

    private void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS chat_message (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id TEXT    NOT NULL,
                    seq           INTEGER NOT NULL,
                    role          TEXT    NOT NULL,
                    content       TEXT,
                    name          TEXT,
                    tool_call_id  TEXT,
                    tool_calls    TEXT,
                    created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
                    UNIQUE(conversation_id, seq)
                )
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_chat_message_conversation
                    ON chat_message(conversation_id, seq)
                """);
        log.info("SQLite message store initialized");
    }

    @Override
    public void append(String conversationId, Message message) {
        int nextSeq = nextSeq(conversationId);
        jdbc.update("""
                INSERT INTO chat_message (conversation_id, seq, role, content, name, tool_call_id, tool_calls)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                conversationId,
                nextSeq,
                message.role().name(),
                message.content(),
                message.name(),
                message.toolCallId(),
                serializeToolCalls(message.toolCalls())
        );
    }

    @Override
    public List<Message> history(String conversationId) {
        return jdbc.query(
                "SELECT role, content, name, tool_call_id, tool_calls FROM chat_message WHERE conversation_id = ? ORDER BY seq",
                (rs, rowNum) -> new Message(
                        MessageRole.valueOf(rs.getString("role")),
                        rs.getString("content"),
                        rs.getString("name"),
                        rs.getString("tool_call_id"),
                        deserializeToolCalls(rs.getString("tool_calls"))
                ),
                conversationId
        );
    }

    @Override
    public void clear(String conversationId) {
        jdbc.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
    }

    private int nextSeq(String conversationId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(seq), -1) FROM chat_message WHERE conversation_id = ?",
                Integer.class,
                conversationId
        );
        return max + 1;
    }

    private String serializeToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize toolCalls", e);
            return null;
        }
    }

    private List<ToolCall> deserializeToolCalls(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize toolCalls: {}", json, e);
            return List.of();
        }
    }
}
