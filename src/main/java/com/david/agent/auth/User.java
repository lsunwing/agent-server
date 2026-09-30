package com.david.agent.auth;

/**
 * 系统用户（不含敏感字段）。
 */
public record User(
        long id,
        String username,
        String displayName,
        String avatarColor,
        String role
) {
}
