package util;

import java.time.LocalDateTime;

/**
 * Date/time helpers: current timestamp, future-date checks (FR-24), and the interval-overlap
 * test used to prevent double-booking a table (FR-26, BR-27). Pure, dependency-free.
 */
public final class DateTimeUtil {

    private DateTimeUtil() {}

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** True if the instant is strictly after now (reservations must be in the future). */
    public static boolean isFuture(LocalDateTime t) {
        return t != null && t.isAfter(LocalDateTime.now());
    }

    /** End of a window that starts at {@code start} and lasts {@code durationMinutes}. */
    public static LocalDateTime endOf(LocalDateTime start, int durationMinutes) {
        return start.plusMinutes(durationMinutes);
    }

    /**
     * True if two time windows overlap. Half-open intervals [start, end) so that back-to-back
     * bookings (one ends exactly when the next begins) do NOT overlap (BR-27).
     */
    public static boolean overlaps(LocalDateTime aStart, int aDurationMinutes,
                                   LocalDateTime bStart, int bDurationMinutes) {
        LocalDateTime aEnd = aStart.plusMinutes(aDurationMinutes);
        LocalDateTime bEnd = bStart.plusMinutes(bDurationMinutes);
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
