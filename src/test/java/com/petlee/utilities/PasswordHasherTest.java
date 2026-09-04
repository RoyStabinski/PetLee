package com.petlee.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-10's acceptance criteria. These ship with the hasher rather than waiting for T-37: it is the
 * one piece of security-critical code in the project, and a regression here is silent.
 */
class PasswordHasherTest {

    @Test
    @DisplayName("the right password verifies")
    void verifiesTheCorrectPassword() {
        assertTrue(PasswordHasher.verify("secret123", PasswordHasher.hash("secret123")));
    }

    @Test
    @DisplayName("a wrong password does not")
    void rejectsAWrongPassword() {
        String stored = PasswordHasher.hash("secret123");
        assertAll(
                () -> assertFalse(PasswordHasher.verify("wrong", stored)),
                () -> assertFalse(PasswordHasher.verify("secret124", stored), "one character out"),
                () -> assertFalse(PasswordHasher.verify("SECRET123", stored), "case matters"),
                () -> assertFalse(PasswordHasher.verify("secret1234", stored), "a longer guess"));
    }

    @Test
    @DisplayName("the salt is random, so the same password hashes to two different strings")
    void saltsEveryHash() {
        String first = PasswordHasher.hash("x");
        String second = PasswordHasher.hash("x");

        assertNotEquals(first, second, "identical digests mean the salt is not random");
        assertAll(
                () -> assertTrue(PasswordHasher.verify("x", first)),
                () -> assertTrue(PasswordHasher.verify("x", second)));
    }

    @Test
    @DisplayName("a corrupt stored value fails the login instead of throwing")
    void neverThrowsOnBadStoredData() {
        assertAll(
                () -> assertFalse(PasswordHasher.verify("x", null)),
                () -> assertFalse(PasswordHasher.verify("x", "")),
                () -> assertFalse(PasswordHasher.verify("x", "garbage")),
                () -> assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$notanumber$c2FsdA==$aGFzaA==")),
                () -> assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$0$c2FsdA==$aGFzaA==")),
                () -> assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$210000$not!base64$aGFzaA==")),
                () -> assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$210000$c2FsdA==")),
                () -> assertFalse(PasswordHasher.verify("x", "bcrypt$210000$c2FsdA==$aGFzaA==")),
                () -> assertFalse(PasswordHasher.verify(null, PasswordHasher.hash("x"))));
    }

    @Test
    @DisplayName("an absent password is refused, not silently hashed")
    void rejectsNullAndEmptyPasswords() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("")));
    }

    @Test
    @DisplayName("the encoded form is self-describing and fits password_hash VARCHAR(255)")
    void encodedFormFitsTheColumn() {
        String encoded = PasswordHasher.hash("secret123");
        String[] parts = encoded.split("\\$");

        assertAll(
                () -> assertTrue(encoded.startsWith("pbkdf2_sha256$"), encoded),
                () -> assertEquals(4, parts.length, "algorithm, iterations, salt, hash"),
                () -> assertEquals(210_000, Integer.parseInt(parts[1]), "iteration count is stored"),
                () -> assertTrue(encoded.length() <= 255, "length " + encoded.length()),
                () -> assertTrue(encoded.length() < 120, "unexpectedly long: " + encoded.length()));
    }

    @Test
    @DisplayName("verification uses the stored iteration count, not the current constant")
    void verifiesAgainstTheStoredParameters() {
        // The point of the self-describing format: raising ITERATIONS later must not lock out
        // everyone hashed under the old value. A digest whose iteration count is edited no longer
        // matches, which is the same mechanism seen from the other side.
        String stored = PasswordHasher.hash("secret123");
        String tampered = stored.replaceFirst("\\$210000\\$", "\\$210001\\$");

        assertAll(
                () -> assertNotEquals(stored, tampered, "the test edited nothing"),
                () -> assertTrue(PasswordHasher.verify("secret123", stored)),
                () -> assertFalse(PasswordHasher.verify("secret123", tampered)));
    }
}
