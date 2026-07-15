package service.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Salted one-way password hashing (BR-02, NFR-04). Uses BCrypt (cost 10) — the same library and
 * scheme that produced the seeded admin hash in db/seed.sql. Plaintext passwords are never
 * stored, logged, or echoed.
 */
public final class PasswordHasher {

    private static final int COST = 10;

    private PasswordHasher() {}

    /** Returns a salted BCrypt hash of the plaintext password. */
    public static String hash(String plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("password required");
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(COST));
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash. Returns false for a null/blank
     * or malformed stored hash rather than throwing, so a bad seed value cannot crash login.
     */
    public static boolean verify(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null || storedHash.isEmpty()) return false;
        try {
            return BCrypt.checkpw(plaintext, storedHash);
        } catch (IllegalArgumentException notABcryptHash) {
            return false;
        }
    }
}
