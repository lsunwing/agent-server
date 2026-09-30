package com.david.agent.auth;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 加盐口令哈希。
 */
@Component
public class PasswordHasher {

    private static final int ITERATIONS = 10_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    public String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hash(String rawPassword, String saltBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        KeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    public boolean matches(String rawPassword, String saltBase64, String expectedHash) {
        String actual = hash(rawPassword, saltBase64);
        return MessageDigestEquals.constantTimeEquals(actual, expectedHash);
    }

    /** 避免直接使用 String.equals 的简易常量时间比较 */
    static final class MessageDigestEquals {
        private MessageDigestEquals() {
        }

        static boolean constantTimeEquals(String a, String b) {
            if (a == null || b == null) {
                return false;
            }
            byte[] left = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] right = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.security.MessageDigest.isEqual(left, right);
        }
    }
}
