package com.david.agent.auth;

/**
 * 含口令哈希的用户持久化记录。
 */
public record UserCredential(
        long id,
        String username,
        String passwordHash,
        String passwordSalt,
        String displayName,
        String avatarColor,
        String role
) {

    public User toUser() {
        return new User(id, username, displayName, avatarColor, role);
    }
}
