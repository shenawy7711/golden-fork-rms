package domain.enums;

/** Live status of a dining table (maps to dining_table.status). */
public enum TableStatus {
    FREE("Free"),
    OCCUPIED("Occupied"),
    RESERVED("Reserved"),
    NEEDS_CLEANING("Needs Cleaning");

    private final String db;

    TableStatus(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static TableStatus fromDb(String v) {
        for (TableStatus t : values()) if (t.db.equals(v)) return t;
        throw new IllegalArgumentException("Unknown TableStatus: " + v);
    }
}
