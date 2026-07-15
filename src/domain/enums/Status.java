package domain.enums;

/** Active/Inactive lifecycle flag for soft-deletable records (user, staff, supplier, stock item). */
public enum Status {
    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String db;

    Status(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static Status fromDb(String v) {
        for (Status s : values()) if (s.db.equals(v)) return s;
        throw new IllegalArgumentException("Unknown Status: " + v);
    }
}
