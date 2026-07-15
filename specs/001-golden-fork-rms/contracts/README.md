# Contracts — Golden Fork RMS

For a desktop app the "contracts" are the **Service-layer API** (the reuse boundary consumed by
JavaFX controllers today and by web controllers in Phase 2) and the **UI screen contracts** (one
FXML + one controller per screen). No REST endpoints exist in Phase 1; these service signatures
are exactly what a Phase-2 web tier will wrap.

Conventions for every service method below:
- **Permission**: the `RbacGuard.require(session, …)` check performed **before** acting
  (Principle IV / FR-02). `—` means any authenticated session.
- Inputs are validated in the service (FRD Appendix A) before persistence.
- Failures raise typed exceptions: `ValidationException`, `AuthorizationException`,
  `ConflictException`, `PersistenceException` (controllers map these to FRD messages).
- No `javafx.*` types appear in any signature (Principle I).

| File | Module | Services | FRs |
|---|---|---|---|
| [auth-admin.md](./auth-admin.md) | Auth & Administration | AuthService, UserService, SystemConfigService, RbacGuard | FR-01…FR-04, FR-31 |
| [menu-tables.md](./menu-tables.md) | Menu & Tables | MenuService, TableService | FR-05…FR-09 |
| [pos-billing.md](./pos-billing.md) | Orders & Billing (POS) | OrderService, BillingService, ReceiptService | FR-10…FR-17 |
| [inventory-purchasing.md](./inventory-purchasing.md) | Inventory & Suppliers | InventoryService, SupplierService, PurchasingService | FR-18…FR-22 |
| [staff-reservations.md](./staff-reservations.md) | Staff & Reservations | StaffService, ReservationService | FR-23…FR-26 |
| [reporting.md](./reporting.md) | Reporting | ReportService, ReportExporter | FR-27…FR-30 |
| [ui-screens.md](./ui-screens.md) | UI (View/Controller) | 13 screens, RBAC-filtered nav | all |

**Permission enum** (`service/security/Permission.java`), keyed to FRD §2.4:
`LOGIN`, `MANAGE_USERS`, `CONFIGURE_SYSTEM`, `MANAGE_MENU`, `DEFINE_TABLES`, `UPDATE_TABLE_STATUS`,
`CREATE_ORDER`, `APPLY_DISCOUNT`, `APPROVE_DISCOUNT`, `MANAGE_STOCK`, `MANAGE_SUPPLIERS`,
`MANAGE_PURCHASING`, `MANAGE_STAFF`, `MANAGE_RESERVATION`, `VIEW_REPORTS`, `EXPORT_REPORTS`.
Role grants: Administrator = all; Manager = all except `MANAGE_USERS`/`CONFIGURE_SYSTEM`;
Cashier = `LOGIN`, `UPDATE_TABLE_STATUS`, `CREATE_ORDER`, `APPLY_DISCOUNT` (≤ threshold),
`MANAGE_RESERVATION`.
