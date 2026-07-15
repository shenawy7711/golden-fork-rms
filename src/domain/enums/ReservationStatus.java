package domain.enums;

/** Reservation lifecycle state (maps to reservation.status). */
public enum ReservationStatus {
    BOOKED("Booked"),
    SEATED("Seated"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    NO_SHOW("No-Show");

    private final String db;

    ReservationStatus(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static ReservationStatus fromDb(String v) {
        for (ReservationStatus s : values()) if (s.db.equals(v)) return s;
        throw new IllegalArgumentException("Unknown ReservationStatus: " + v);
    }
}
