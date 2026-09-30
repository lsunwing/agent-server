package com.david.agent.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<AuthService.LoginResponse> login(@Valid @RequestBody LoginBody body) {
        return Mono.fromCallable(() -> authService.login(new AuthService.LoginRequest(body.username(), body.password())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/me")
    public Mono<User> me(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    JwtService.JwtPrincipal principal = AuthWebFilter.requirePrincipal(exchange);
                    return authService.currentUser(principal.userId());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/logout")
    public Mono<Void> logout() {
        // JWT 无状态，前端清除 token 即可；保留接口便于后续黑名单扩展
        return Mono.empty();
    }

    public record LoginBody(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }
}
