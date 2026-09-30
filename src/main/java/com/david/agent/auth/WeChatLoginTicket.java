package com.david.agent.auth;

/**
 * 微信扫码登录票据。
 */
public record WeChatLoginTicket(
        String ticket,
        WeChatTicketStatus status,
        Long userId,
        long createdAtEpochMs,
        long expiresAtEpochMs
) {

    public boolean isExpired(long nowEpochMs) {
        return nowEpochMs > expiresAtEpochMs;
    }

    public WeChatLoginTicket withStatus(WeChatTicketStatus newStatus, Long newUserId) {
        return new WeChatLoginTicket(ticket, newStatus, newUserId, createdAtEpochMs, expiresAtEpochMs);
    }
}
