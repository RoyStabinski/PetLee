package com.petlee.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Specification §4: "user passwords will not be stored as plain text in the database".
 * Exercises {@link PasswordHasher} directly — no container, no database.
 */
class PasswordHasherTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Test
    void verifyAcceptsTheCorrectPassword() {
        String digest = PasswordHasher.hash(PASSWORD);

        assertTrue(PasswordHasher.verify(PASSWORD, digest));
    }

    @Test
    void verifyRejectsAWrongPassword() {
        String digest = PasswordHasher.hash(PASSWORD);

        assertFalse(PasswordHasher.verify("wrong password", digest));
    }

    @Test
    void theDigestNeverContainsThePlaintext() {
        String digest = PasswordHasher.hash(PASSWORD);

        assertFalse(digest.contains(PASSWORD));
    }

    @Test
    void hashingTheSamePasswordTwiceProducesDifferentDigests() {
        // The salt is random per call; a fixed digest would mean no salting at all.
        assertNotEquals(PasswordHasher.hash(PASSWORD), PasswordHasher.hash(PASSWORD));
    }

    @Test
    void verifyRejectsAMalformedDigestInsteadOfThrowing() {
        assertFalse(PasswordHasher.verify(PASSWORD, "not-a-real-digest"));
        assertFalse(PasswordHasher.verify(PASSWORD, "pbkdf2_sha256$notanumber$c2FsdA==$aGFzaA=="));
    }
}
