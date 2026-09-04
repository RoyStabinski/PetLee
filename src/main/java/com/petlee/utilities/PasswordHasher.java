package com.petlee.utilities;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Salted, iterated password hashing — specification §4's "user passwords will not be stored as
 * plain text in the database".
 *
 * <h2>Algorithm</h2>
 * PBKDF2 with HMAC-SHA256, 210 000 iterations, a 16-byte {@link SecureRandom} salt and a 256-bit
 * derived key. It is a genuinely accepted password KDF and it ships with the JDK, so it costs no
 * dependency and raises no licence question (ADR-003).
 *
 * <h2>Encoded form</h2>
 * <pre>pbkdf2_sha256$&lt;iterations&gt;$&lt;base64 salt&gt;$&lt;base64 hash&gt;</pre>
 * Self-describing on purpose. The parameters travel with the digest, so the iteration count can be
 * raised later without invalidating everyone's stored password: {@link #verify} derives with the
 * values it reads out of the stored string, not with the constants below. The result is 90
 * characters, inside {@code password_hash VARCHAR(255)}.
 *
 * <h2>Two properties worth stating</h2>
 * <ul>
 *   <li>Hashing the same password twice gives two different strings, because the salt is random.
 *       That is what defeats a rainbow table.</li>
 *   <li>Comparison goes through {@link MessageDigest#isEqual}, which is constant-time.
 *       {@code String.equals} stops at the first differing byte and leaks, through timing, how much
 *       of a guess was right.</li>
 * </ul>
 *
 * <p>Nothing here logs, and no method takes or returns a plaintext password anywhere but as an
 * argument — a password must not be able to reach a log file through this class.
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
        // Stateless utility.
    }

    /**
     * Hashes a password with a fresh random salt.
     *
     * @param plainPassword the password, neither {@code null} nor empty
     * @return the encoded digest, safe to store as-is
     * @throws IllegalArgumentException if the password is {@code null} or empty — an empty password
     *         must not be silently hashable
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
     * Checks a password against a stored digest, deriving with the salt and iteration count the
     * digest carries.
     *
     * <p>Never throws. A malformed, empty or {@code null} stored value is simply a failed
     * verification: a corrupt row must fail one login, not answer the endpoint with a 500.
     *
     * @param plainPassword the password to check, may be {@code null}
     * @param encoded       the stored digest, may be {@code null} or malformed
     * @return {@code true} only if the password matches
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

            // Derive to the stored digest's own length, so a digest written by a future version
            // with a different key size still verifies.
            byte[] actual = derive(plainPassword, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException malformed) {
            // Unparsable iteration count or base64 — the stored value is corrupt, so the login
            // fails. Deliberately swallowed; see the method contract.
            return false;
        }
    }

    /**
     * Runs the key derivation.
     *
     * @throws IllegalStateException if the JRE has no PBKDF2WithHmacSHA256, which every supported
     *         JDK does. That is a broken platform, not a bad password, so it is not turned into a
     *         failed verification.
     */
    private static byte[] derive(String password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(ALGORITHM + " is unavailable", e);
        } finally {
            // Clears the copy this spec holds; the caller's String is beyond our reach.
            spec.clearPassword();
        }
    }
}
