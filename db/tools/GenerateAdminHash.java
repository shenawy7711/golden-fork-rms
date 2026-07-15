// ============================================================================
//  Golden Fork RMS — one-off bootstrap utility (NOT part of the application).
//
//  Prints a salted BCrypt hash for the first Administrator password, plus a
//  ready-to-run SQL UPDATE. The app stores passwords ONLY as BCrypt hashes
//  (BR-02), so you cannot type a plain-text password into seed.sql — you paste
//  the hash this tool produces.
//
//  Requires jBCrypt on the classpath  ->  Maven: org.mindrot:jbcrypt:0.4
//  (the same dependency the app's PasswordHasher uses).
//
//  Usage:
//    java GenerateAdminHash [password]        (defaults to "admin123")
//  See db/README or the chat instructions for the exact compile/run commands.
// ============================================================================

import org.mindrot.jbcrypt.BCrypt;

public class GenerateAdminHash {

    public static void main(String[] args) {
        String password = (args.length > 0) ? args[0] : "admin123";

        // gensalt(10) = cost factor 10 (2^10 rounds) — same default the app should use.
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));

        System.out.println();
        System.out.println("  Password : " + password);
        System.out.println("  BCrypt   : " + hash);
        System.out.println("  verify   : " + BCrypt.checkpw(password, hash) + "   (must be true)");
        System.out.println();
        System.out.println("  --- Option A: paste the hash into db/seed.sql, replacing");
        System.out.println("  ---           'REPLACE_WITH_BCRYPT_HASH_FOR_admin123'");
        System.out.println();
        System.out.println("  --- Option B: run this UPDATE in DBeaver AFTER seeding:");
        System.out.println("  UPDATE user_account SET password_hash = '" + hash
                + "' WHERE username = 'admin';");
        System.out.println();
    }
}
