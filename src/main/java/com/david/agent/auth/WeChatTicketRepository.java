package com.david.agent.auth;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WeChatTicketRepository {

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS wechat_login_ticket (
                    ticket         TEXT    PRIMARY KEY,
                    status         TEXT    NOT NULL,
                    user_id        INTEGER,
                    created_at_ms  INTEGER NOT NULL,
                    expires_at_ms  INTEGER NOT NULL
                )
                """);
        log.info("[auth] wechat_login_ticket table ready");
    }

    public void insert(WeChatLoginTicket ticket) {
        jdbc.update("""
                        INSERT INTO wechat_login_ticket (ticket, status, user_id, created_at_ms, expires_at_ms)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                ticket.ticket(),
                ticket.status().name(),
                ticket.userId(),
                ticket.createdAtEpochMs(),
                ticket.expiresAtEpochMs());
    }

    public Optional<WeChatLoginTicket> findByTicket(String ticket) {
        List<WeChatLoginTicket> rows = jdbc.query("""
                        SELECT ticket, status, user_id, created_at_ms, expires_at_ms
                        FROM wechat_login_ticket WHERE ticket = ?
                        """,
                (rs, i) -> {
                    Object userIdRaw = rs.getObject("user_id");
                    Long userId = userIdRaw == null ? null : ((Number) userIdRaw).longValue();
                    return new WeChatLoginTicket(
                            rs.getString("ticket"),
                            WeChatTicketStatus.valueOf(rs.getString("status")),
                            userId,
                            rs.getLong("created_at_ms"),
                            rs.getLong("expires_at_ms")
                    );
                },
                ticket);
        return rows.stream().findFirst();
    }

    public void updateStatus(String ticket, WeChatTicketStatus status, Long userId) {
        jdbc.update("UPDATE wechat_login_ticket SET status = ?, user_id = ? WHERE ticket = ?",
                status.name(), userId, ticket);
    }

    public void deleteExpired(long nowEpochMs) {
        jdbc.update("DELETE FROM wechat_login_ticket WHERE expires_at_ms < ?", nowEpochMs - 60_000L);
    }
}
