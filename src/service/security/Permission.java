package service.security;

import domain.enums.RoleName;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * The set of guarded permissions and the role -> permission grants, keyed to the FRD §2.4
 * Role–Permission Matrix (Principle IV). Grants are cumulative:
 * Administrator ⊇ Manager ⊇ Cashier.
 */
public enum Permission {
    LOGIN,
    MANAGE_USERS,
    CONFIGURE_SYSTEM,
    MANAGE_MENU,
    DEFINE_TABLES,
    UPDATE_TABLE_STATUS,
    CREATE_ORDER,
    APPLY_DISCOUNT,
    APPROVE_DISCOUNT,
    MANAGE_STOCK,
    MANAGE_SUPPLIERS,
    MANAGE_PURCHASING,
    MANAGE_STAFF,
    MANAGE_RESERVATION,
    VIEW_REPORTS,
    EXPORT_REPORTS;

    private static final Map<RoleName, EnumSet<Permission>> GRANTS = buildGrants();

    private static Map<RoleName, EnumSet<Permission>> buildGrants() {
        // Administrator: everything.
        EnumSet<Permission> admin = EnumSet.allOf(Permission.class);
        // Manager: everything except user-account and system-config administration.
        EnumSet<Permission> manager = EnumSet.complementOf(EnumSet.of(MANAGE_USERS, CONFIGURE_SYSTEM));
        // Cashier: front-line operations only (discounts only up to the approval threshold).
        EnumSet<Permission> cashier = EnumSet.of(
            LOGIN, UPDATE_TABLE_STATUS, CREATE_ORDER, APPLY_DISCOUNT, MANAGE_RESERVATION);

        Map<RoleName, EnumSet<Permission>> m = new EnumMap<>(RoleName.class);
        m.put(RoleName.ADMINISTRATOR, admin);
        m.put(RoleName.MANAGER, manager);
        m.put(RoleName.CASHIER, cashier);
        return m;
    }

    /** True if the given role holds this permission. */
    public boolean isGrantedTo(RoleName role) {
        EnumSet<Permission> set = GRANTS.get(role);
        return set != null && set.contains(this);
    }

    /** A copy of all permissions granted to a role. */
    public static EnumSet<Permission> grantedTo(RoleName role) {
        EnumSet<Permission> set = GRANTS.get(role);
        return set == null ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(set);
    }
}
