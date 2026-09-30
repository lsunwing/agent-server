package com.david.agent.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashIsSaltedAndVerifiable() {
        String salt = hasher.newSalt();
        String hash = hasher.hash("admin123", salt);
        assertTrue(hasher.matches("admin123", salt, hash));
        assertFalse(hasher.matches("wrong", salt, hash));
    }

    @Test
    void differentSaltsProduceDifferentHashes() {
        String salt1 = hasher.newSalt();
        String salt2 = hasher.newSalt();
        assertNotEquals(salt1, salt2);
        assertNotEquals(hasher.hash("admin123", salt1), hasher.hash("admin123", salt2));
    }
}
