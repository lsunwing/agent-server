package com.david.agent.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WeChatLoginService {

    private final AuthProperties authProperties;
    private final WeChatTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordHasher passwordHasher;
    private final ObjectMapper objectMapper;

    private final SecureRandom random = new SecureRandom();
    private final WebClient webClient = WebClient.builder().build();

    public WeChatQrResponse createQrCode() {
        AuthProperties.WeChat wechat = authProperties.getWechat();
        long now = Instant.now().toEpochMilli();
        long ttlMs = wechat.getTicketTtl().toMillis();
        String ticket = newTicketId();

        ticketRepository.insert(new WeChatLoginTicket(
                ticket,
                WeChatTicketStatus.WAITING,
                null,
                now,
                now + ttlMs
        ));
        ticketRepository.deleteExpired(now);

        String qrContent = resolveQrContent(ticket);
        return new WeChatQrResponse(
                ticket,
                qrContent,
                wechat.isMock() ? "mock" : "open-platform",
                ttlMs / 1000,
                wechat.isMock() && wechat.isMockEnabled()
        );
    }

    public WeChatStatusResponse status(String ticket) {
        return ticketRepository.findByTicket(ticket)
                .map(this::toStatusResponse)
                .orElseGet(() -> new WeChatStatusResponse(WeChatTicketStatus.INVALID.name(), null, null, 0));
    }

    public AuthService.LoginResponse mockLogin(String ticket) {
        AuthProperties.WeChat wechat = authProperties.getWechat();
        if (!wechat.isMock() || !wechat.isMockEnabled()) {
            throw new UnauthorizedException("当前未开启模拟扫码登录");
        }

        WeChatLoginTicket current = requireActiveTicket(ticket);
        if (current.status() == WeChatTicketStatus.CONFIRMED) {
            // 幂等：已确认则直接签发
            return issueForUser(current.userId());
        }

        ticketRepository.updateStatus(ticket, WeChatTicketStatus.SCANNED, null);
        User user = userRepository.findOrCreateWeChatUser("mock", "微信用户", null, "#07c160");
        ticketRepository.updateStatus(ticket, WeChatTicketStatus.CONFIRMED, user.id());
        return issueForUser(user.id());
    }

    public AuthService.LoginResponse handleCallback(String code, String state) {
        AuthProperties.WeChat wechat = authProperties.getWechat();
        if (!wechat.isOpenPlatform()) {
            throw new UnauthorizedException("未启用微信开放平台登录");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new UnauthorizedException("微信回调参数不完整");
        }

        WeChatLoginTicket ticket = requireActiveTicket(state);
        try {
            String tokenBody = webClient.get()
                    .uri(UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                            .queryParam("appid", wechat.getAppId())
                            .queryParam("secret", wechat.getAppSecret())
                            .queryParam("code", code)
                            .queryParam("grant_type", "authorization_code")
                            .toUriString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode tokenJson = objectMapper.readTree(tokenBody == null || tokenBody.isBlank() ? "{}" : tokenBody);
            if (tokenJson.has("errcode") && tokenJson.path("errcode").asInt(0) != 0) {
                throw new UnauthorizedException("微信授权失败: " + tokenJson.path("errmsg").asText("unknown"));
            }

            String openid = tokenJson.path("openid").asText(null);
            if (openid == null || openid.isBlank()) {
                throw new UnauthorizedException("微信授权未返回 openid");
            }
            String unionid = tokenJson.path("unionid").asText(null);

            String nickname = "微信用户";
            String avatar = null;
            try {
                String userInfoBody = webClient.get()
                        .uri(UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/userinfo")
                                .queryParam("access_token", tokenJson.path("access_token").asText(""))
                                .queryParam("openid", openid)
                                .queryParam("lang", "zh_CN")
                                .toUriString())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                JsonNode userJson = objectMapper.readTree(userInfoBody == null || userInfoBody.isBlank() ? "{}" : userInfoBody);
                if (userJson.has("nickname") && !userJson.path("nickname").asText().isBlank()) {
                    nickname = userJson.path("nickname").asText();
                }
                if (userJson.has("headimgurl") && !userJson.path("headimgurl").asText().isBlank()) {
                    avatar = userJson.path("headimgurl").asText();
                }
            } catch (Exception e) {
                log.warn("[auth][wechat] fetch user info failed: {}", e.getMessage());
            }

            User user = userRepository.findOrCreateWeChatUser(
                    unionid != null && !unionid.isBlank() ? unionid : openid,
                    nickname,
                    avatar,
                    "#07c160"
            );
            ticketRepository.updateStatus(state, WeChatTicketStatus.CONFIRMED, user.id());
            return issueForUser(user.id());
        } catch (UnauthorizedException e) {
            ticketRepository.updateStatus(state, WeChatTicketStatus.INVALID, null);
            throw e;
        } catch (Exception e) {
            ticketRepository.updateStatus(state, WeChatTicketStatus.INVALID, null);
            throw new UnauthorizedException("微信登录失败: " + e.getMessage());
        }
    }

    private WeChatLoginTicket requireActiveTicket(String ticket) {
        WeChatLoginTicket current = ticketRepository.findByTicket(ticket)
                .orElseThrow(() -> new UnauthorizedException("登录二维码已失效"));
        long now = Instant.now().toEpochMilli();
        if (current.isExpired(now) || current.status() == WeChatTicketStatus.EXPIRED || current.status() == WeChatTicketStatus.INVALID) {
            ticketRepository.updateStatus(ticket, WeChatTicketStatus.EXPIRED, current.userId());
            throw new UnauthorizedException("登录二维码已过期，请刷新");
        }
        return current;
    }

    private WeChatStatusResponse toStatusResponse(WeChatLoginTicket ticket) {
        long now = Instant.now().toEpochMilli();
        if (ticket.isExpired(now) && ticket.status() != WeChatTicketStatus.CONFIRMED) {
            ticketRepository.updateStatus(ticket.ticket(), WeChatTicketStatus.EXPIRED, ticket.userId());
            return new WeChatStatusResponse(WeChatTicketStatus.EXPIRED.name(), null, null, 0);
        }

        if (ticket.status() == WeChatTicketStatus.CONFIRMED && ticket.userId() != null) {
            AuthService.LoginResponse login = issueForUser(ticket.userId());
            return new WeChatStatusResponse(
                    WeChatTicketStatus.CONFIRMED.name(),
                    login.token(),
                    login.user(),
                    login.expiresIn()
            );
        }

        long expireIn = Math.max(0, (ticket.expiresAtEpochMs() - now) / 1000);
        return new WeChatStatusResponse(ticket.status().name(), null, null, expireIn);
    }

    private AuthService.LoginResponse issueForUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("登录票据未绑定用户");
        }
        User user = userRepository.findById(userId)
                .map(UserCredential::toUser)
                .orElseThrow(() -> new UnauthorizedException("用户不存在或已失效"));
        String token = jwtService.issue(user.id(), user.username());
        return new AuthService.LoginResponse(token, jwtService.ttlSeconds(), user);
    }

    private String resolveQrContent(String ticket) {
        AuthProperties.WeChat wechat = authProperties.getWechat();
        if (wechat.isOpenPlatform()) {
            return UriComponentsBuilder.fromHttpUrl("https://open.weixin.qq.com/connect/qrconnect")
                    .queryParam("appid", wechat.getAppId())
                    .queryParam("redirect_uri", wechat.getRedirectUri())
                    .queryParam("response_type", "code")
                    .queryParam("scope", "snsapi_login")
                    .queryParam("state", ticket)
                    .toUriString()
                    + "#wechat_redirect";
        }
        // mock：本地确认链接（也可被前端直接展示为二维码）
        String base = wechat.getRedirectUri();
        if (base != null && base.contains("/login")) {
            base = base.substring(0, base.indexOf("/login"));
        }
        if (base == null || base.isBlank()) {
            base = "http://localhost:5173";
        }
        return base + "/wechat/scan?ticket=" + ticket;
    }

    private String newTicketId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public record WeChatQrResponse(
            String ticket,
            String qrContent,
            String mode,
            long expireIn,
            boolean mockEnabled
    ) {
    }

    public record WeChatStatusResponse(
            String status,
            String token,
            User user,
            long expiresIn
    ) {
    }
}
