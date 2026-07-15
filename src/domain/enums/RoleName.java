package domain.enums;

/** The three RBAC roles (maps to role.role_name). */
public enum RoleName {
    ADMINISTRATOR("Administrator"),
    MANAGER("Manager"),
    CASHIER("Cashier");

    private final String db;

    RoleName(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static RoleName fromDb(String v) {
        for (RoleName r : values()) if (r.db.equals(v)) return r;
        throw new IllegalArgumentException("Unknown RoleName: " + v);
    }
}
