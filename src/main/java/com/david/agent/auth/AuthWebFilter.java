package com.david.agent.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthWebFilter implements WebFilter {

    public static final String PRINCIPAL_ATTR = "auth.principal";

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/auth/login",
            "/auth/wechat",
            "/api/ping",
            "/ping"
    );

    private final JwtService jwtService;

    public static JwtService.JwtPrincipal requirePrincipal(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(PRINCIPAL_ATTR);
        if (value instanceof JwtService.JwtPrincipal principal) {
            return principal;
        }
        throw new UnauthorizedException("未登录或登录已过期");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = extractBearer(authorization);
        Optional<JwtService.JwtPrincipal> principal = jwtService.parse(token);
        if (principal.isEmpty()) {
            log.debug("[auth] reject path={} reason=missing-or-invalid-token", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
            byte[] body = "{\"error\":\"未登录或登录已过期\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }

        exchange.getAttributes().put(PRINCIPAL_ATTR, principal.get());
        return chain.filter(exchange);
    }

    private boolean isPublic(String path) {
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        for (String prefix : PUBLIC_PREFIXES) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
