package domain.enums;

/** Session audit event type (maps to login_event.event_type). */
public enum LoginEventType {
    LOGIN("LOGIN"),
    LOGOUT("LOGOUT");

    private final String db;

    LoginEventType(String db) { this.db = db; }

    public String dbValue() { return db; }

    public static LoginEventType fromDb(String v) {
        for (LoginEventType e : values()) if (e.db.equals(v)) return e;
        throw new IllegalArgumentException("Unknown LoginEventType: " + v);
    }
}
