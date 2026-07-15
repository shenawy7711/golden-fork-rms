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
