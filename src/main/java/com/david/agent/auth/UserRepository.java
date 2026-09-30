package com.david.agent.auth;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserRepository {

    private static final String SEED_ADMIN = "admin";
    private static final String SEED_ADMIN_PASSWORD = "admin123";
    private static final String SEED_DEMO = "demo";
    private static final String SEED_DEMO_PASSWORD = "demo123";

    private final JdbcTemplate jdbc;
    private final PasswordHasher passwordHasher;
    private final AuthProperties authProperties;

    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS app_user (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT    NOT NULL UNIQUE,
                    password_hash TEXT    NOT NULL,
                    password_salt TEXT    NOT NULL,
                    display_name  TEXT    NOT NULL,
                    avatar_color  TEXT    NOT NULL DEFAULT '#2563eb',
                    role          TEXT    NOT NULL DEFAULT 'USER',
                    created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
                    updated_at    TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);
        log.info("[auth] app_user table ready");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS wechat_identity (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    identity_key  TEXT    NOT NULL UNIQUE,
                    user_id       INTEGER NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                    nickname      TEXT,
                    avatar_url    TEXT,
                    created_at    TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);
        log.info("[auth] wechat_identity table ready");
        if (authProperties.isSeedAdmin()) {
            seedIfMissing(SEED_ADMIN, SEED_ADMIN_PASSWORD, "管理员", "#2563eb", "ADMIN");
            seedIfMissing(SEED_DEMO, SEED_DEMO_PASSWORD, "演示用户", "#16a34a", "USER");
        }
    }

    private void seedIfMissing(String username, String rawPassword, String displayName, String color, String role) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM app_user WHERE username = ?", Integer.class, username);
        if (count != null && count > 0) {
            return;
        }
        String salt = passwordHasher.newSalt();
        String hash = passwordHasher.hash(rawPassword, salt);
        jdbc.update("""
                        INSERT INTO app_user (username, password_hash, password_salt, display_name, avatar_color, role)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                username, hash, salt, displayName, color, role);
        log.info("[auth] seeded user: {} ({})", username, role);
    }

    public Optional<UserCredential> findByUsername(String username) {
        return jdbc.query("""
                        SELECT id, username, password_hash, password_salt, display_name, avatar_color, role
                        FROM app_user WHERE username = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new UserCredential(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("password_salt"),
                            rs.getString("display_name"),
                            rs.getString("avatar_color"),
                            rs.getString("role")
                    ));
                },
                username);
    }

    public Optional<UserCredential> findById(long id) {
        return jdbc.query("""
                        SELECT id, username, password_hash, password_salt, display_name, avatar_color, role
                        FROM app_user WHERE id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new UserCredential(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("password_salt"),
                            rs.getString("display_name"),
                            rs.getString("avatar_color"),
                            rs.getString("role")
                    ));
                },
                id);
    }

    /**
     * 按微信身份查找或创建本地用户（mock / 开放平台统一入口）。
     */
    public User findOrCreateWeChatUser(String identityKey, String nickname, String avatarUrl, String avatarColor) {
        String key = identityKey == null || identityKey.isBlank() ? "unknown" : identityKey.trim();
        Optional<Long> boundUserId = jdbc.query("""
                        SELECT user_id FROM wechat_identity WHERE identity_key = ?
                        """,
                rs -> rs.next() ? Optional.of(rs.getLong("user_id")) : Optional.empty(),
                key);

        if (boundUserId.isPresent()) {
            return findById(boundUserId.get())
                    .map(UserCredential::toUser)
                    .orElseThrow(() -> new IllegalStateException("微信绑定用户不存在: " + boundUserId.get()));
        }

        String displayName = (nickname == null || nickname.isBlank()) ? "微信用户" : nickname.trim();
        String username = resolveWeChatUsername(key);
        Optional<UserCredential> existing = findByUsername(username);
        User user;
        if (existing.isPresent()) {
            user = existing.get().toUser();
        } else {
            String salt = passwordHasher.newSalt();
            // 微信登录用户不设置可用密码
            String hash = passwordHasher.hash(UUID.randomUUID().toString(), salt);
            String color = (avatarColor == null || avatarColor.isBlank()) ? "#07c160" : avatarColor;
            jdbc.update("""
                            INSERT INTO app_user (username, password_hash, password_salt, display_name, avatar_color, role)
                            VALUES (?, ?, ?, ?, ?, 'USER')
                            """,
                    username, hash, salt, displayName, color);
            Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
            user = findById(id)
                    .map(UserCredential::toUser)
                    .orElseThrow(() -> new IllegalStateException("创建微信用户失败"));
        }

        jdbc.update("""
                        INSERT INTO wechat_identity (identity_key, user_id, nickname, avatar_url)
                        VALUES (?, ?, ?, ?)
                        """,
                key, user.id(), displayName, avatarUrl);
        return user;
    }

    private String resolveWeChatUsername(String identityKey) {
        String compact = identityKey.replaceAll("[^a-zA-Z0-9]", "");
        if (compact.length() > 12) {
            compact = compact.substring(0, 12);
        }
        if (compact.isBlank()) {
            compact = "guest";
        }
        return "wx_" + compact.toLowerCase();
    }
}
