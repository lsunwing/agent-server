package com.david.agent.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 微信扫码登录接口。
 */
@RestController
@RequestMapping("/auth/wechat")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WeChatAuthController {

    private final WeChatLoginService weChatLoginService;

    @GetMapping("/qrcode")
    public Mono<WeChatLoginService.WeChatQrResponse> qrcode() {
        return Mono.fromCallable(weChatLoginService::createQrCode)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/status")
    public Mono<WeChatLoginService.WeChatStatusResponse> status(@RequestParam("ticket") String ticket) {
        return Mono.fromCallable(() -> weChatLoginService.status(ticket))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/tickets/{ticket}/mock-login")
    public Mono<AuthService.LoginResponse> mockLogin(@PathVariable("ticket") String ticket) {
        return Mono.fromCallable(() -> weChatLoginService.mockLogin(ticket))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/callback")
    public Mono<AuthService.LoginResponse> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state
    ) {
        return Mono.fromCallable(() -> weChatLoginService.handleCallback(code, state))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
