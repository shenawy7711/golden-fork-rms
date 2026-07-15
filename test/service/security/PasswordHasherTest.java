package service.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies password hashing/verification, including the exact hash seeded in db/seed.sql. */
class PasswordHasherTest {

    @Test
    void hashAndVerifyRoundTrip() {
        String hash = PasswordHasher.hash("admin123");
        assertTrue(PasswordHasher.verify("admin123", hash), "correct password verifies");
        assertFalse(PasswordHasher.verify("wrong", hash), "wrong password rejected");
    }

    @Test
    void verifiesSeededAdminHash() {
        // The exact BCrypt hash committed in db/seed.sql for the admin/admin123 account.
        String seeded = "$2a$10$Hg4y87rntmSjf4wbo1ErqOwgH4zN4jp44mkRiVGL7DeS07j8ZG3Fm";
        assertTrue(PasswordHasher.verify("admin123", seeded), "seeded admin password logs in");
        assertFalse(PasswordHasher.verify("admin124", seeded), "near-miss rejected");
    }

    @Test
    void malformedStoredHashDoesNotThrow() {
        assertFalse(PasswordHasher.verify("admin123", "not-a-bcrypt-hash"));
    }
}
