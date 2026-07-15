package domain.enums;

/** How a discount is expressed on an order (maps to orders.discount_type). */
public enum DiscountType {
    NONE("None"),
    PERCENTAGE("Percentage"),
    FIXED("Fixed");

    private final String db;

    DiscountType(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static DiscountType fromDb(String v) {
        for (DiscountType d : values()) if (d.db.equals(v)) return d;
        throw new IllegalArgumentException("Unknown DiscountType: " + v);
    }
}
