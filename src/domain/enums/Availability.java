package domain.enums;

/** Menu-item availability (maps to menu_item.availability). */
public enum Availability {
    AVAILABLE("Available"),
    UNAVAILABLE("Unavailable");

    private final String db;

    Availability(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static Availability fromDb(String v) {
        for (Availability a : values()) if (a.db.equals(v)) return a;
        throw new IllegalArgumentException("Unknown Availability: " + v);
    }
}
