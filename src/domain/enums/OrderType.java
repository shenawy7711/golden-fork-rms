package domain.enums;

/** Order fulfilment type (maps to orders.order_type). */
public enum OrderType {
    DINE_IN("Dine-in"),
    TAKEAWAY("Takeaway");

    private final String db;

    OrderType(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static OrderType fromDb(String v) {
        for (OrderType t : values()) if (t.db.equals(v)) return t;
        throw new IllegalArgumentException("Unknown OrderType: " + v);
    }
}
