package config;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The application's tunable settings, parsed from the {@code system_config} reference data (FR-31)
 * and held immutably for the life of a run.
 *
 * <p>Values are administered through {@code SystemConfigService} and loaded by
 * {@link ReferenceDataLoader}. A missing or unparseable entry falls back to the documented default
 * rather than failing the boot, so a partially-seeded database still starts.
 *
 * <p>The tax rate here is the <em>current</em> rate used when pricing an open order; it is
 * snapshotted onto the order at finalisation and never retro-applied to a finalised bill
 * (BR-18, BR-09).
 */
public final class AppConfig {

    /** Config keys as seeded by {@code db/seed.sql}. */
    public static final String KEY_TAX_RATE = "tax_rate";
    public static final String KEY_IDLE_TIMEOUT_MIN = "idle_timeout_min";
    public static final String KEY_LOGIN_MAX_ATTEMPTS = "login_max_attempts";
    public static final String KEY_RESERVATION_SLOT_MINUTES = "reservation_slot_minutes";
    public static final String KEY_DISCOUNT_APPROVAL_THRESHOLD = "discount_approval_threshold";

    /** Defaults per data-model.md §6; used when a key is absent or malformed. */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.1400");
    private static final int DEFAULT_IDLE_TIMEOUT_MIN = 15;
    private static final int DEFAULT_LOGIN_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_RESERVATION_SLOT_MINUTES = 90;
    private static final BigDecimal DEFAULT_DISCOUNT_APPROVAL_THRESHOLD = new BigDecimal("20.00");

    private final BigDecimal taxRate;
    private final int idleTimeoutMinutes;
    private final int loginMaxAttempts;
    private final int reservationSlotMinutes;
    private final BigDecimal discountApprovalThreshold;

    private AppConfig(BigDecimal taxRate, int idleTimeoutMinutes, int loginMaxAttempts,
                      int reservationSlotMinutes, BigDecimal discountApprovalThreshold) {
        this.taxRate = taxRate;
        this.idleTimeoutMinutes = idleTimeoutMinutes;
        this.loginMaxAttempts = loginMaxAttempts;
        this.reservationSlotMinutes = reservationSlotMinutes;
        this.discountApprovalThreshold = discountApprovalThreshold;
    }

    /** Parses raw {@code system_config} key/value pairs into typed settings. */
    public static AppConfig from(Map<String, String> raw) {
        return new AppConfig(
            decimal(raw, KEY_TAX_RATE, DEFAULT_TAX_RATE),
            integer(raw, KEY_IDLE_TIMEOUT_MIN, DEFAULT_IDLE_TIMEOUT_MIN),
            integer(raw, KEY_LOGIN_MAX_ATTEMPTS, DEFAULT_LOGIN_MAX_ATTEMPTS),
            integer(raw, KEY_RESERVATION_SLOT_MINUTES, DEFAULT_RESERVATION_SLOT_MINUTES),
            decimal(raw, KEY_DISCOUNT_APPROVAL_THRESHOLD, DEFAULT_DISCOUNT_APPROVAL_THRESHOLD));
    }

    /** All defaults — for tests and for a boot with no reachable configuration. */
    public static AppConfig defaults() {
        return new AppConfig(
            DEFAULT_TAX_RATE, DEFAULT_IDLE_TIMEOUT_MIN, DEFAULT_LOGIN_MAX_ATTEMPTS,
            DEFAULT_RESERVATION_SLOT_MINUTES, DEFAULT_DISCOUNT_APPROVAL_THRESHOLD);
    }

    private static BigDecimal decimal(Map<String, String> raw, String key, BigDecimal fallback) {
        String value = raw.get(key);
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            return parsed.signum() < 0 ? fallback : parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int integer(Map<String, String> raw, String key, int fallback) {
        String value = raw.get(key);
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed <= 0 ? fallback : parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The current tax rate as a decimal fraction (0.1400 = 14%). */
    public BigDecimal taxRate() { return taxRate; }

    /** Minutes of inactivity before the session is auto-logged-out (FR-04). */
    public int idleTimeoutMinutes() { return idleTimeoutMinutes; }

    /** Consecutive failed logins allowed before throttling (BR-07). */
    public int loginMaxAttempts() { return loginMaxAttempts; }

    /** The length of a reservation slot, used by the overlap check (BR-27). */
    public int reservationSlotMinutes() { return reservationSlotMinutes; }

    /** Discount amount above which manager approval is required (BR-17). */
    public BigDecimal discountApprovalThreshold() { return discountApprovalThreshold; }
}
