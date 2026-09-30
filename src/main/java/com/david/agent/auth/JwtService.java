package com.david.agent.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 轻量 JWT (HS256) 实现，避免引入额外依赖。
 */
@Component
@RequiredArgsConstructor
public class JwtService {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public String issue(long userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plus(authProperties.getTokenTtl());

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sub", username);
        payload.put("uid", userId);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", exp.getEpochSecond());

        String header = base64Url(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String body = base64Url(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signature = sign(header + "." + body);
        return header + "." + body + "." + signature;
    }

    /**
     * 校验并解析 token；无效时返回 empty。
     */
    public Optional<JwtPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String expected = sign(parts[0] + "." + parts[1]);
        if (!constantTimeEquals(expected, parts[2])) {
            return Optional.empty();
        }
        try {
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long exp = payload.path("exp").asLong(0);
            // RFC 7519：当前时间必须早于 exp，等于 exp 即过期
            if (exp > 0 && Instant.now().getEpochSecond() >= exp) {
                return Optional.empty();
            }
            long uid = payload.path("uid").asLong(0);
            String sub = payload.path("sub").asText(null);
            if (uid <= 0 || sub == null || sub.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new JwtPrincipal(uid, sub));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return authProperties.getTokenTtl().toSeconds();
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return base64Url(raw);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record JwtPrincipal(long userId, String username) {
    }
}
