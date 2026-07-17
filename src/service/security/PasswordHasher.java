package service.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Salted one-way password hashing (BR-02, NFR-04). Uses BCrypt (cost 10) — the same library and
 * scheme that produced the seeded admin hash in db/seed.sql. Plaintext passwords are never
 * stored, logged, or echoed.
 */
public final class PasswordHasher {

    private static final int COST = 10;

    // A real hash of a throwaway value, computed once. AuthService verifies against this when the
    // username is unknown so that a failed sign-in costs the same either way — returning early
    // without hashing is measurably faster and reveals which usernames exist.
    private static final String DUMMY_HASH = BCrypt.hashpw("no-such-account", BCrypt.gensalt(COST));

    private PasswordHasher() {}

    /**
     * A valid BCrypt hash that no supplied password will match. Used to keep the cost of a failed
     * sign-in constant; never stored against an account.
     */
    public static String dummyHash() {
        return DUMMY_HASH;
    }

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
