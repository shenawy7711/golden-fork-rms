package domain.enums;

/** Type of stock ledger movement (maps to stock_movement.movement_type). */
public enum MovementType {
    RECEIPT("Receipt"),
    ADJUSTMENT("Adjustment");

    private final String db;

    MovementType(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static MovementType fromDb(String v) {
        for (MovementType m : values()) if (m.db.equals(v)) return m;
        throw new IllegalArgumentException("Unknown MovementType: " + v);
    }
}
