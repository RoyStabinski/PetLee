package com.petlee.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private static final String PASSWORD = "Correct-Horse-9";

    @Test
    @DisplayName("A hashed password verifies against its own digest")
    void should_verify_when_password_matches_its_hash() {
        String stored = PasswordHasher.hash(PASSWORD);

        assertTrue(PasswordHasher.verify(PASSWORD, stored));
    }

    @Test
    @DisplayName("A wrong password does not verify")
    void should_fail_when_password_is_wrong() {
        String stored = PasswordHasher.hash(PASSWORD);

        assertFalse(PasswordHasher.verify("correct-horse-9", stored));
    }

    @Test
    @DisplayName("Hashing the same password twice gives two different digests that both verify")
    void should_produce_different_digests_when_same_password_is_hashed_twice() {
        String first = PasswordHasher.hash(PASSWORD);
        String second = PasswordHasher.hash(PASSWORD);

        assertNotEquals(first, second, "each hash should use a fresh random salt");
        assertTrue(PasswordHasher.verify(PASSWORD, first));
        assertTrue(PasswordHasher.verify(PASSWORD, second));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "not-a-hash",
            "pbkdf2_sha256$210000$c2FsdA==",                      // three parts instead of four
            "bcrypt$210000$c2FsdA==$ZGlnZXN0",                    // another scheme's prefix
            "pbkdf2_sha256$many$c2FsdA==$ZGlnZXN0",               // iterations not a number
            "pbkdf2_sha256$0$c2FsdA==$ZGlnZXN0",                  // iterations not positive
            "pbkdf2_sha256$210000$!!not-base64!!$ZGlnZXN0",       // salt not Base64
            "pbkdf2_sha256$210000$$ZGlnZXN0",                     // empty salt
            "pbkdf2_sha256$210000$c2FsdA==$"                      // empty digest
    })
    @DisplayName("A null, empty or malformed stored value fails without throwing")
    void should_fail_without_throwing_when_stored_value_is_malformed(String stored) {
        boolean verified = assertDoesNotThrow(() -> PasswordHasher.verify(PASSWORD, stored));

        assertFalse(verified);
    }

    @Test
    @DisplayName("The stored format starts with pbkdf2_sha256$")
    void should_start_with_scheme_prefix_when_hashed() {
        assertTrue(PasswordHasher.hash(PASSWORD).startsWith("pbkdf2_sha256$"));
    }
}
