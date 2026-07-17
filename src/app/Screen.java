package app;

import service.security.Permission;

/**
 * The screens reachable from the dashboard's side nav, each tagged with the permission its role
 * must hold (FRD §2.4). {@code DashboardController} builds the nav from this enum filtered by the
 * session's role, so a Cashier never sees a Management entry.
 *
 * <p>Hiding an entry here is presentation only — the service behind each screen calls
 * {@code RbacGuard.require} regardless, so bypassing the nav gains nothing (BR-03, defence in
 * depth).
 *
 * <p>Screens whose FXML has not been built yet are marked {@link #isImplemented()} {@code false}:
 * they appear in the nav (so the shape of the app is visible) but report "not yet available"
 * instead of failing to load.
 */
public enum Screen {

    // --- Operations ---
    ORDERS("Orders", Section.OPERATIONS, Permission.CREATE_ORDER, "/view/orders.fxml", "OR", true),
    TABLES("Tables", Section.OPERATIONS, Permission.UPDATE_TABLE_STATUS, "/view/tables.fxml", "TB", true),
    RESERVATIONS("Reservations", Section.OPERATIONS, Permission.MANAGE_RESERVATION, "/view/reservations.fxml", "RS", false),

    // --- Management ---
    MENU("Menu", Section.MANAGEMENT, Permission.MANAGE_MENU, "/view/menu.fxml", "MN", true),
    INVENTORY("Inventory", Section.MANAGEMENT, Permission.MANAGE_STOCK, "/view/inventory.fxml", "IN", true),
    SUPPLIERS("Suppliers", Section.MANAGEMENT, Permission.MANAGE_SUPPLIERS, "/view/suppliers.fxml", "SP", true),
    PURCHASING("Purchasing", Section.MANAGEMENT, Permission.MANAGE_PURCHASING, "/view/purchasing.fxml", "PO", true),
    STAFF("Staff", Section.MANAGEMENT, Permission.MANAGE_STAFF, "/view/staff.fxml", "ST", false),
    REPORTS("Reports", Section.MANAGEMENT, Permission.VIEW_REPORTS, "/view/reports.fxml", "RP", false),

    // --- Administration ---
    USERS("User Accounts", Section.ADMINISTRATION, Permission.MANAGE_USERS, "/view/users.fxml", "US", true),
    SYSTEM_CONFIG("System Settings", Section.ADMINISTRATION, Permission.CONFIGURE_SYSTEM, "/view/system-config.fxml", "SY", true);

    /** The side nav's three groups. */
    public enum Section {
        OPERATIONS("Operations"),
        MANAGEMENT("Management"),
        ADMINISTRATION("Administration");

        private final String title;

        Section(String title) { this.title = title; }

        public String title() { return title; }
    }

    private final String title;
    private final Section section;
    private final Permission permission;
    private final String fxml;
    private final String chip;
    private final boolean implemented;

    Screen(String title, Section section, Permission permission, String fxml, String chip, boolean implemented) {
        this.title = title;
        this.section = section;
        this.permission = permission;
        this.fxml = fxml;
        this.chip = chip;
        this.implemented = implemented;
    }

    public String title() { return title; }

    public Section section() { return section; }

    /** The permission a role must hold for this screen to appear and function. */
    public Permission permission() { return permission; }

    public String fxml() { return fxml; }

    /** The two-letter mark shown in the nav chip. */
    public String chip() { return chip; }

    /** False while the screen's FXML is still to be built in a later phase. */
    public boolean isImplemented() { return implemented; }
}
