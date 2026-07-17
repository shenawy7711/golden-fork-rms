package util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Field-validation predicates from FRD Appendix A (lengths, non-negative, email/phone format,
 * required-contact). Pure helpers with no dependencies — services compose these and throw
 * {@code ValidationException} with the field-specific message when a rule fails.
 */
public final class Validation {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[+]?[0-9 ()\\-]{6,30}$");

    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");

    /** Appendix A: password minimum length. */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /** The message shown when {@link #isValidPassword} fails (Appendix A: "reject with policy hint"). */
    public static final String PASSWORD_POLICY_HINT =
        "Password must be at least " + PASSWORD_MIN_LENGTH + " characters and include a letter and a digit.";

    private Validation() {}

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** True when the trimmed length is within [min, max] (inclusive). */
    public static boolean hasLength(String s, int min, int max) {
        if (s == null) return false;
        int n = s.trim().length();
        return n >= min && n <= max;
    }

    /**
     * Appendix A password policy: at least {@value #PASSWORD_MIN_LENGTH} characters, with at least
     * one letter and one digit.
     *
     * <p>Not trimmed — leading/trailing spaces are legitimate password characters, and trimming
     * here would silently accept a password that then fails to verify at login.
     */
    public static boolean isValidPassword(String password) {
        return password != null
            && password.length() >= PASSWORD_MIN_LENGTH
            && HAS_LETTER.matcher(password).matches()
            && HAS_DIGIT.matcher(password).matches();
    }

    public static boolean isValidEmail(String s) {
        return s != null && EMAIL.matcher(s.trim()).matches();
    }

    public static boolean isValidPhone(String s) {
        return s != null && PHONE.matcher(s.trim()).matches();
    }

    public static boolean isNonNegative(BigDecimal v) {
        return v != null && v.signum() >= 0;
    }

    public static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    /** At least one of phone/email is present and well-formed (reservation contact rule, FR-24). */
    public static boolean hasAtLeastOneContact(String phone, String email) {
        return (!isBlank(phone) && isValidPhone(phone))
            || (!isBlank(email) && isValidEmail(email));
    }
}
