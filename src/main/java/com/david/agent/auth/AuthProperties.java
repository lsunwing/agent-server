package com.david.agent.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agent.auth")
public class AuthProperties {

    /** 是否启用登录鉴权 */
    private boolean enabled = true;

    /** JWT 签名密钥 */
    private String secret = "change-me-agent-console-secret";

    /** Token 有效期 */
    private Duration tokenTtl = Duration.ofDays(7);

    /** 启动时写入种子账号 */
    private boolean seedAdmin = true;

    private final WeChat wechat = new WeChat();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public boolean isSeedAdmin() {
        return seedAdmin;
    }

    public void setSeedAdmin(boolean seedAdmin) {
        this.seedAdmin = seedAdmin;
    }

    public WeChat getWechat() {
        return wechat;
    }

    public static class WeChat {

        /** 是否启用微信扫码登录 */
        private boolean enabled = true;

        /** mock: 本地模拟；open-platform: 微信开放平台网站应用 */
        private String mode = "mock";

        private String appId = "";

        private String appSecret = "";

        /** OAuth 回调地址 */
        private String redirectUri = "http://localhost:5173/login";

        /** 扫码票据有效期 */
        private Duration ticketTtl = Duration.ofMinutes(5);

        /** mock 模式是否允许模拟扫码 */
        private boolean mockEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public Duration getTicketTtl() {
            return ticketTtl;
        }

        public void setTicketTtl(Duration ticketTtl) {
            this.ticketTtl = ticketTtl;
        }

        public boolean isMockEnabled() {
            return mockEnabled;
        }

        public void setMockEnabled(boolean mockEnabled) {
            this.mockEnabled = mockEnabled;
        }

        public boolean isOpenPlatform() {
            return "open-platform".equalsIgnoreCase(mode);
        }

        public boolean isMock() {
            return !isOpenPlatform();
        }
    }
}
