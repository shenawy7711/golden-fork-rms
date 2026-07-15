# Contract — UI Screens (View / Controller)

One **FXML + one controller per screen** (Principle II). Controllers are thin: capture input,
call services, map results/typed exceptions to messages. All screens share
`view/css/app.css` (adapted from `design/app.css` — tokens/typography/spacing, **reference
only**, no web code reused).

## Screen inventory

| Screen | FXML | Controller | Services used | Min permission | FRs |
|---|---|---|---|---|---|
| Auth (Login) | `auth.fxml` | `AuthController` | AuthService | — (pre-auth) | FR-01 |
| Dashboard | `dashboard.fxml` | `DashboardController` | (reads across services) | LOGIN | FR-02 |
| User Accounts | `users.fxml` | `UserController` | UserService | MANAGE_USERS | FR-03 |
| System Config | `system-config.fxml` | `SystemConfigController` | SystemConfigService | CONFIGURE_SYSTEM | FR-31 |
| Menu & Prices | `menu.fxml` | `MenuController` | MenuService | MANAGE_MENU | FR-05…FR-07 |
| Tables | `tables.fxml` | `TableController` | TableService | UPDATE_TABLE_STATUS | FR-08, FR-09 |
| POS / Orders | `orders.fxml` | `OrderController` | OrderService, BillingService, ReceiptService | CREATE_ORDER | FR-10…FR-17 |
| Reservations | `reservations.fxml` | `ReservationController` | ReservationService | MANAGE_RESERVATION | FR-24…FR-26 |
| Inventory | `inventory.fxml` | `InventoryController` | InventoryService | MANAGE_STOCK | FR-18, FR-22 |
| Suppliers | `suppliers.fxml` | `SupplierController` | SupplierService | MANAGE_SUPPLIERS | FR-19 |
| Purchasing | `purchasing.fxml` | `PurchasingController` | PurchasingService | MANAGE_PURCHASING | FR-20, FR-21 |
| Staff | `staff.fxml` | `StaffController` | StaffService | MANAGE_STAFF | FR-23 |
| Reports | `reports.fxml` | `ReportController` | ReportService, ReportExporter | VIEW_REPORTS | FR-27…FR-30 |

## Visual match (Login + Dashboard) — `design/` reference

- **Login** (`auth.fxml`) matches `design/screenshots/auth-login.png`: centered white card
  (radius 22, soft shadow) on a warm cream radial gradient; left navy brand panel (gold "GF"
  tile, wordmark), right form panel (Username, Password with Show/Hide, Remember/Forgot,
  navy primary button). **Sign-up tab is out of scope** (FR-03 — accounts are Admin-created;
  see research D-12). Colors/typography/spacing come from `app.css` tokens.
- **Dashboard** (`dashboard.fxml`) matches `dashboard-admin.png` / `dashboard-cashier.png`:
  `BorderPane` = navy topbar (62px) + white side nav (250px) + cream scrolling content + status
  bar (32px). Nav sections are **RBAC-filtered from the session role**, not hard-coded:
  Operations (all), Management (Manager/Admin), Administration (Admin). The design's "Viewing
  as" role switcher is **demo-only and removed** — role comes from `Session`.

## Controller rules (all screens)

- No SQL, no money math, no business rule in controllers (Principles I, III).
- Every action calls the service; the service performs its own `RbacGuard.require(...)` — the UI
  additionally hides/disables controls the role cannot use (defence-in-depth, FR-02).
- Map exceptions to FRD messages: `ValidationException`→field/inline error;
  `AuthorizationException`→"You do not have permission…"/generic login error;
  `ConflictException`→specific conflict (e.g. "Username already exists.", overlap, open-order);
  `PersistenceException`→"service unavailable".
- Logout with an Open order prompts finalise/park/discard before ending the session (FR-04).
