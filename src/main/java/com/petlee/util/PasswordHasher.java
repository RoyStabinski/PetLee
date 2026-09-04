package com.petlee.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 password hashing, 210 000 iterations over a random 16-byte salt.
 *
 * <p>The encoded form {@code pbkdf2_sha256$iterations$salt$hash} carries its own parameters, so
 * the iteration count can be raised later without invalidating stored passwords. Comparison is
 * constant-time, and nothing in this class logs.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2_sha256";
    private static final String SEPARATOR = "$";

    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    /** Thread-safe, and seeded by the platform. Held once rather than per call. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * Hashes a password with a fresh random salt.
     *
     * @param plainPassword the password, neither null nor empty
     * @return the encoded digest, safe to store as-is
     * @throws IllegalArgumentException if the password is null or empty
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("password must not be null or empty");
        }

        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] digest = derive(plainPassword, salt, ITERATIONS, KEY_BITS);

        Base64.Encoder base64 = Base64.getEncoder();
        return PREFIX + SEPARATOR + ITERATIONS
                + SEPARATOR + base64.encodeToString(salt)
                + SEPARATOR + base64.encodeToString(digest);
    }

    /**
     * Checks a password against a stored digest. Never throws: a corrupt row fails one login
     * rather than answering the endpoint with a 500.
     *
     * @param plainPassword the password to check, may be null
     * @param encoded       the stored digest, may be null or malformed
     * @return true only if the password matches
     */
    public static boolean verify(String plainPassword, String encoded) {
        if (plainPassword == null || plainPassword.isEmpty() || encoded == null) {
            return false;
        }

        String[] parts = encoded.split("\\" + SEPARATOR);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            Base64.Decoder base64 = Base64.getDecoder();
            byte[] salt = base64.decode(parts[2]);
            byte[] expected = base64.decode(parts[3]);
            if (salt.length == 0 || expected.length == 0) {
                return false;
            }

            // To the stored digest's own length, so a different key size still verifies.
            byte[] actual = derive(plainPassword, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException malformed) {
            return false; // corrupt stored value; see the method contract
        }
    }

    /**
     * Runs the key derivation.
     *
     * @throws IllegalStateException if the JRE has no PBKDF2WithHmacSHA256 — a broken platform,
     *         not a bad password, so it is not turned into a failed verification
     */
    private static byte[] derive(String password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(ALGORITHM + " is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }
}
