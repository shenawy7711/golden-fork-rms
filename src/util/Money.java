package util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic helpers: two-decimal precision with a single HALF-UP rounding step per
 * figure (BR-13, BR-16). All monetary computation in the system goes through these so rounding
 * is consistent; {@code BillingService} is the only caller that composes them into bill figures.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {}

    /** Rounds a value to 2 decimals HALF-UP (one rounding step). */
    public static BigDecimal round(BigDecimal v) {
        return v.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String v) { return round(new BigDecimal(v)); }

    public static BigDecimal of(long v) { return round(BigDecimal.valueOf(v)); }

    /** a + b, rounded once. */
    public static BigDecimal add(BigDecimal a, BigDecimal b) { return round(a.add(b)); }

    /** a - b, rounded once. */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) { return round(a.subtract(b)); }

    /** a * b, rounded once (e.g. unit_price * qty, or discountable * tax_rate). */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) { return round(a.multiply(b)); }

    public static boolean isNegative(BigDecimal v) { return v != null && v.signum() < 0; }

    public static boolean isZero(BigDecimal v) { return v != null && v.signum() == 0; }

    /** The larger of two amounts (both assumed 2dp). */
    public static BigDecimal max(BigDecimal a, BigDecimal b) { return a.compareTo(b) >= 0 ? a : b; }

    /** The smaller of two amounts (used to cap a fixed discount at the subtotal, BR-17). */
    public static BigDecimal min(BigDecimal a, BigDecimal b) { return a.compareTo(b) <= 0 ? a : b; }
}
