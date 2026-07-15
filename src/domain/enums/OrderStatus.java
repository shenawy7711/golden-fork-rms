package domain.enums;

/** Order lifecycle state (maps to orders.status). */
public enum OrderStatus {
    OPEN("Open"),
    PAID_CLOSED("Paid/Closed"),
    CANCELLED("Cancelled");

    private final String db;

    OrderStatus(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static OrderStatus fromDb(String v) {
        for (OrderStatus s : values()) if (s.db.equals(v)) return s;
        throw new IllegalArgumentException("Unknown OrderStatus: " + v);
    }
}
