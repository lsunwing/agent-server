package com.david.agent.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService newService(Duration ttl) {
        AuthProperties props = new AuthProperties();
        props.setSecret("unit-test-secret");
        props.setTokenTtl(ttl);
        return new JwtService(props, new ObjectMapper());
    }

    @Test
    void issueAndParseRoundTrip() {
        JwtService service = newService(Duration.ofHours(1));
        String token = service.issue(7L, "alice");
        JwtService.JwtPrincipal principal = service.parse(token).orElseThrow();
        assertEquals(7L, principal.userId());
        assertEquals("alice", principal.username());
    }

    @Test
    void rejectTamperedToken() {
        JwtService service = newService(Duration.ofHours(1));
        String token = service.issue(7L, "alice");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".AAAA" + parts[2].substring(4);
        assertFalse(service.parse(tampered).isPresent());
    }

    @Test
    void rejectExpiredToken() {
        // JWT exp 按秒存储，这里用 1s TTL 并跨秒等待
        JwtService service = newService(Duration.ofSeconds(1));
        String token = service.issue(7L, "alice");
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(service.parse(token).isPresent());
    }

    @Test
    void rejectBlankToken() {
        JwtService service = newService(Duration.ofHours(1));
        assertTrue(service.parse("").isEmpty());
        assertTrue(service.parse(null).isEmpty());
        assertTrue(service.parse("not-a-jwt").isEmpty());
    }
}
