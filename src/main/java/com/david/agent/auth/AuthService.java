package com.david.agent.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        Optional<UserCredential> found = userRepository.findByUsername(request.username());
        if (found.isEmpty()) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        UserCredential credential = found.get();
        if (!passwordHasher.matches(request.password(), credential.passwordSalt(), credential.passwordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        String token = jwtService.issue(credential.id(), credential.username());
        return new LoginResponse(token, jwtService.ttlSeconds(), credential.toUser());
    }

    public User currentUser(long userId) {
        return userRepository.findById(userId)
                .map(UserCredential::toUser)
                .orElseThrow(() -> new UnauthorizedException("用户不存在或已失效"));
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, long expiresIn, User user) {
    }
}
