package domain.enums;

/** Purchase-order lifecycle state (maps to purchase_order.status). */
public enum PoStatus {
    ORDERED("Ordered"),
    PARTIALLY_RECEIVED("Partially Received"),
    RECEIVED("Received"),
    CANCELLED("Cancelled");

    private final String db;

    PoStatus(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static PoStatus fromDb(String v) {
        for (PoStatus s : values()) if (s.db.equals(v)) return s;
        throw new IllegalArgumentException("Unknown PoStatus: " + v);
    }
}
