# Tasks: Golden Fork RMS

**Input**: Design documents from `specs/001-golden-fork-rms/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, constitution v3.0.0

**Tests**: Targeted tests are included **only** where the constitution mandates a quality gate —
the **Billing** suite (TDD §8.3 worked examples, Principle III) and **RBAC** checks (Principle
IV). UI/controller tests are out of scope for this phase.

**Organization**: Tasks are grouped by user story. Phase order follows the plan's recommended
**build order** (Auth & Admin → Menu & Tables → POS/Billing → Inventory & Purchasing → Staff &
Reservations → Reporting), which is why the P1 POS story (US1) comes after its prerequisites
US2 (auth) and US3 (menu/tables).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on incomplete tasks)
- **[Story]**: US1…US6 (maps to spec.md user stories); Setup/Foundational/Polish have no label
- All paths are relative to the repo root (see plan.md source tree)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven project, toolchain, and shared resources.

- [x] T001 Create the **Maven** project and prefix-free source layout per plan.md: `pom.xml` at the repo root, `src/` with packages `app`, `controller`, `view`, `service`, `service/exception`, `service/security`, `dao`, `domain`, `domain/enums`, `util`, `config`; plus `db/` and `test/`. Configure `sourceDirectory=src` and `testSourceDirectory=test` so the prefix-free layout is preserved (no `com.rms` prefix). Open in any editor (Cursor/VS Code with the Extension Pack for Java, or Eclipse).
- [x] T002 Configure `pom.xml`: **Full JDK 8** target (`maven.compiler.source/target=1.8`); JavaFX comes from the JDK's bundled `jfxrt.jar` (not a Maven dependency); add dependencies for MySQL Connector/J, jBCrypt, OpenPDF, JUnit 5, and Mockito.
- [x] T003 [P] Add `src/view/css/app.css` adapted from `design/app.css` (colors, typography, spacing tokens) — reference only, no web code reused. _(Dropped the styles for UI the spec removes: `.seg-track`/`.role-pick` (Sign-up, D-12), `.role-switcher` (FR-02), and `-fx-letter-spacing` (unsupported in JavaFX 8).)_
- [x] T004 [P] Create `config/db.properties` template (host `localhost`, port `3306`, db `rms`, user, password) and document it in the project README.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure every user story depends on — schema, domain, DB access,
security primitives, utils, and app bootstrap.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T005 [P] Create all domain enums in `src/domain/enums/`: `RoleName`, `Status` (Active/Inactive), `Availability`, `TableStatus`, `OrderType`, `OrderStatus`, `DiscountType`, `PoStatus`, `MovementType`, `ReservationStatus`, `LoginEventType`.
- [x] T006 [P] Create auth/admin domain POJOs in `src/domain/`: `Role`, `User`, `Staff`, `LoginEvent`, `SystemConfig`.
- [x] T007 [P] Create menu/table domain POJOs in `src/domain/`: `MenuCategory`, `MenuItem`, `DiningTable`.
- [x] T008 [P] Create order/billing domain POJOs in `src/domain/`: `Order`, `OrderItem`, `Payment`, `PaymentMethod`.
- [x] T009 [P] Create inventory domain POJOs in `src/domain/`: `Supplier`, `StockItem`, `StockMovement`, `PurchaseOrder`, `PurchaseOrderItem`.
- [x] T010 [P] Create reservation domain POJO in `src/domain/Reservation.java`.
- [x] T011 [P] Create typed exceptions in `src/service/exception/`: `ValidationException`, `AuthorizationException`, `ConflictException`, `PersistenceException` (plus a shared `RmsException` base).
- [x] T012 [P] Implement `src/util/Money.java` — `BigDecimal` DECIMAL(10,2) helpers with a single HALF-UP rounding step per figure (BR-13, BR-16).
- [x] T013 [P] Implement `src/util/Validation.java` — FRD Appendix A field rules (lengths, non-negative, email/phone format, required-contact).
- [x] T014 [P] Implement `src/util/DateTimeUtil.java` — timestamps, future-date checks, interval helpers for reservations.
- [x] T015 Write `db/schema.sql` — all 18 tables in 3NF with PK/FK/UNIQUE/NOT NULL/CHECK constraints and indexes on FKs and lookup columns, exactly per data-model.md.
- [x] T016 Write `db/seed.sql` — insert 3 roles, 3 payment methods, baseline `system_config` (`tax_rate`, `idle_timeout_min`=15, `login_max_attempts`=5, `reservation_slot_minutes`=90, `discount_approval_threshold`), and one active Administrator with a BCrypt-hashed password (BR-06).
- [x] T017 Implement `src/dao/ConnectionFactory.java` — JDBC connection provisioning plus a transaction helper (`autoCommit=false`, commit on success, rollback on exception) shared across DAOs within a service transaction. _(`inTransaction` returns business failures unchanged and wraps `SQLException` as `PersistenceException`. Each DAO method has a `Connection`-taking form so a service can enlist several in one transaction; first real use lands with `OrderService` (T050).)_
- [x] T018 [P] Implement `src/config/AppConfig.java`, `src/config/DbSettings.java`, and `src/config/ReferenceDataLoader.java` (loads tax rate and tunables from `system_config`). _(Typed tunables with data-model.md §6 defaults, so a partially-seeded DB still boots. Reads via `SystemConfigDAO` rather than duplicating SQL in the config layer.)_
- [x] T019 [P] Implement `src/service/security/PasswordHasher.java` — salted BCrypt hash + verify (never logs/echoes plain text) (BR-02, NFR-04).
- [x] T020 [P] Implement `src/service/security/Session.java` — authenticated user + role holder, no `javafx.*` imports.
- [x] T021 Implement `src/service/security/Permission.java` — the permission enum and role→permission grants keyed to the FRD §2.4 matrix (Administrator ⊇ Manager ⊇ Cashier).
- [x] T022 Implement `src/service/security/RbacGuard.java` — `require(session, permission)` throwing `AuthorizationException` and recording denied attempts (FR-02, BR-03). (depends on T020, T021)
- [x] T023 Implement `src/app/Main.java` (extends `javafx.application.Application`) plus a screen-navigation/FXML loader that swaps center content and applies `app.css`. _(`AppContext` composition root, `Screen` enum (nav entry + required permission + FXML), `Navigator` (scene swap, centre swap, `app.css` applied once), `ContextAware` for controller wiring. Verified: `mvn exec:java` starts clean.)_

**Checkpoint**: Foundation ready — user story implementation can begin.

**⚠️ Constitution gate**: after this phase, `grep -rn "javafx" src/service src/dao src/domain src/util` MUST return nothing (Principle I).

---

## Phase 3: User Story 2 — Authenticate & enforce role-based access (Priority: P1)

**Goal**: Every user signs in before acting; each function is restricted to permitted roles,
enforced in the business layer; accounts are administered by Administrators.

**Independent Test**: With accounts per role, valid credentials log in to a role-appropriate
home; invalid/inactive login is refused with a generic message and no session; a Cashier is
refused price/stock/report functions even if the UI is bypassed, and the attempt is recorded.

### Tests for User Story 2 (constitution RBAC gate)

- [x] T024 [P] [US2] `test/service/RbacGuardTest.java` — assert the FRD §2.4 matrix: Cashier denied `MANAGE_MENU`/`MANAGE_STOCK`/`VIEW_REPORTS`/`MANAGE_USERS`; Manager denied `MANAGE_USERS`/`CONFIGURE_SYSTEM`; Administrator allowed all. _(12 tests, incl. cumulative-grants and null-session (BR-01). Passing.)_
- [x] T025 [P] [US2] `test/service/UserServiceTest.java` — unique-username conflict, and the last-active-Administrator rule (BR-06) rejects deactivate/delete. _(12 tests; BR-06 covers all three routes out of the role — deactivate, delete, and demotion via update. Passing.)_

### Implementation for User Story 2

- [x] T026 [P] [US2] Implement `src/dao/RoleDAO.java` (read roles) with prepared statements.
- [x] T027 [P] [US2] Implement `src/dao/UserDAO.java` (CRUD, findByUsername case-insensitive, count active admins) with prepared statements. _(Case-insensitivity comes from the column's `utf8mb4_unicode_ci` collation, so plain equality still uses the unique index; `LOWER(username)` would force a scan.)_
- [x] T028 [P] [US2] Implement `src/dao/LoginEventDAO.java` (insert LOGIN/LOGOUT, query by user/range).
- [x] T029 [P] [US2] Implement `src/dao/SystemConfigDAO.java` (get/set config keys; list/activate payment methods) with prepared statements (FR-31). _(**Partial — needs a decision.** Config get/set + `listPaymentMethods` done. **Activate/deactivate is NOT implemented**: FRD Appendix/§FR-31 and `contracts/auth-admin.md` both require it, but `data-model.md` §2.10 and `db/schema.sql` define `payment_method` as a fixed set with no `status` column. Resolving this means an `ALTER TABLE` on the live DB — see T029a.)_
- [ ] T029a [US2] **Decision + migration**: reconcile `payment_method` activate/deactivate. FRD ("activated/deactivated rather than hard-deleted once referenced by a payment") and `contracts/auth-admin.md` (`setPaymentMethodActive`, "at least one active method must remain") require a status flag; `data-model.md` §2.10 and `schema.sql` omit it. Recommended: add `status ENUM('Active','Inactive') NOT NULL DEFAULT 'Active'` to `payment_method`, update `data-model.md`, ship an idempotent `ALTER`, then implement `SystemConfigDAO.setPaymentMethodActive` + `SystemConfigService.setPaymentMethodActive` (keep ≥1 active, FR-15).
- [x] T030 [US2] Implement `src/service/AuthService.java` — `login` (verify hash, refuse Inactive, throttle after `login_max_attempts`, write LOGIN event, return Session) and `logout` (invalidate, write LOGOUT, block on unsaved open order) (FR-01, FR-04; BR-01, BR-02, BR-07). (depends on T027, T028, T019, T020)
- [x] T031 [US2] Implement `src/service/UserService.java` — create/update/deactivate/delete with `RbacGuard.require(MANAGE_USERS)`, unique username, soft-delete-with-history, last-admin protection (FR-03; BR-04, BR-05, BR-06). (depends on T022, T027)
- [x] T032 [US2] Implement `src/controller/AuthController.java` + `src/view/auth.fxml` — Login screen visually matching `design/screenshots/auth-login.png` via `app.css`; Sign-up tab excluded (research D-12); maps typed exceptions to FRD messages (FR-01). _(Also omitted as unbacked by any FR: "Forgot password?" (no reset flow specified) and "Remember me" (cuts against BR-02). **Visual match not yet confirmed — needs your eyes.**)_
- [x] T033 [US2] Implement `src/controller/DashboardController.java` + `src/view/dashboard.fxml` — topbar + RBAC-filtered side nav (Operations/Management/Administration built from `Session` role) + status bar, matching the dashboard screenshots; "Viewing as" switcher removed (FR-02). _(Nav built from the `Screen` enum filtered by session permissions. Stat tiles are deliberately blank: their figures need US1/US3/US5 services, and inventing numbers would be indistinguishable from real data. **Visual match not yet confirmed.**)_
- [x] T034 [US2] Implement `src/controller/UserController.java` + `src/view/users.fxml` — Administrator-only account management UI over `UserService` (FR-03).
- [x] T035 [US2] Implement `src/service/SystemConfigService.java` — Administrator-only get/update of reference/system data (tax rate, payment methods, `idle_timeout_min`, `login_max_attempts`, `reservation_slot_minutes`, `discount_approval_threshold`) with `RbacGuard.require(CONFIGURE_SYSTEM)`, Appendix A validation, and audit (`updated_by`/`updated_at`); the tax rate is snapshotted onto orders at finalisation, never retro-applied (FR-31; BR-31, BR-03, BR-09, BR-18). (depends on T022, T029)
- [x] T035a [US2] Implement `src/controller/SystemConfigController.java` + `src/view/system-config.fxml` — Administrator-only reference/system-data screen over `SystemConfigService` (never the DAO directly); maps typed exceptions to FRD messages (FR-31). _(Payment methods are listed read-only pending T029a.)_

**Checkpoint**: Login, RBAC, account admin, and system config (FR-31) work independently.
_Reached._ Verified against the live DB: seeded `admin` signs in and resolves as ADMINISTRATOR;
username match is case-insensitive; a wrong password and an unknown username return byte-identical
messages (no enumeration); an admin session reaches config (5 keys) and the user list. `mvn exec:java`
starts clean and all 30 tests pass. Outstanding for this phase: **T029a** (payment-method decision)
and a visual pass over T032/T033 against the reference screenshots.

---

## Phase 4: User Story 3 — Maintain the menu and dining tables (Priority: P2)

**Goal**: Managers keep categories, items, prices, availability, and table layout current so
orders are taken against accurate information.

**Independent Test**: A Manager creates a category + an Available item at a valid price and can
immediately order it; marks it Unavailable (leaves ordering, keeps history); defines a
uniquely-labelled table with capacity; table status moves only through allowed transitions.

### Tests for User Story 3

- [x] T038a [P] [US3] `test/service/TableServiceTest.java` — the table state machine (BR-12) as data-model.md §Table defines it. Asserts the full 4×4 grid, not samples: the rule is "only defined transitions are allowed", so rejections carry equal weight. Notably `Occupied → Free` is illegal (must pass through Needs Cleaning, FR-17). _(8 tests, passing. Not constitution-mandated — added because the state machine is pure logic and cheap to pin down.)_

### Implementation for User Story 3

- [x] T036 [P] [US3] Implement `src/dao/MenuCategoryDAO.java` (CRUD, count items in category) with prepared statements.
- [x] T037 [P] [US3] Implement `src/dao/MenuItemDAO.java` (CRUD, unique-within-category, list orderable) with prepared statements.
- [x] T038 [P] [US3] Implement `src/dao/DiningTableDAO.java` (CRUD, status update, existence of open order/reservation) with prepared statements. _(A "future reservation" counts only Booked/Seated bookings — a Cancelled/Completed/No-Show one holds nothing and must not block a delete.)_
- [x] T039 [US3] Implement `src/service/MenuService.java` — categories (unique name, block delete of non-empty) and items (price ≥ 0, unique-in-category, availability toggle, soft-delete with history) with `RbacGuard.require(MANAGE_MENU)` (FR-05, FR-06, FR-07; BR-08, BR-09, BR-10). (depends on T022, T036, T037)
- [x] T040 [US3] Implement `src/service/TableService.java` — `defineTable` (unique label, capacity ≥ 1), `deleteTable` (only when free/un-booked), `changeStatus` validating the table state machine (FR-08, FR-09; BR-11, BR-12). (depends on T022, T038)
- [x] T041 [US3] Implement `src/controller/MenuController.java` + `src/view/menu.fxml` — categories/items/price/availability management (FR-05…FR-07). _(Deleting an item that appears on past orders soft-deletes it to Unavailable and says so, rather than refusing (BR-10).)_
- [x] T042 [US3] Implement `src/controller/TableController.java` + `src/view/tables.fxml` — table definition + live status board with the status color helpers from `app.css` (FR-08, FR-09). _(The status dropdown offers only legal transitions from the table's current state; `TableService` re-validates regardless (BR-12).)_

**Checkpoint**: Menu and tables are manageable and feed the POS.
_Reached, with a caveat._ Both screens load, compile, and are wired into the nav; all 40 tests pass and
both constitution gates (Principle I JavaFX-leak, Principle VII prepared-statements) are clean.
**Not yet exercised against live data** — no categories, items, or tables exist in the DB yet, so the
CRUD paths have not been driven end-to-end. Creating that data unattended would have written test rows
into your database; it is the natural first thing to do when you next open the app.

---

## Phase 5: User Story 1 — Take an order and produce an accurate bill (Priority: P1) 🎯 MVP

**Goal**: The core revenue journey — a cashier goes from order entry to a paid, printed receipt
with every figure calculated automatically, atomically finalised, immutable afterward.

**Prerequisites**: US2 (auth/session/RBAC) and US3 (menu items + tables) must exist — this is
why the P1 MVP is sequenced here per the plan's build order.

**Independent Test**: With an active user, an Available item, and a Free table present, a cashier
opens a dine-in order, adds items, applies a discount, sees tax/total computed, records payment,
finalises atomically, prints a receipt matching the bill, and sees the table released — no manual
arithmetic; later price/tax edits never change the finalised bill.

### Tests for User Story 1 (constitution Billing gate — MUST pass)

- [x] T043 [P] [US1] `test/service/BillingServiceTest.java` — TDD §8.3 worked cases: subtotal 2×12.50+1×8.00=33.00; 10% discount on 100.00 → 10.00/base 90.00; fixed cap 25.00 on 20.00 → 20.00; tax 10% on 90.00 → 9.00, total 99.00; percentage capped at 100 and fixed capped at subtotal (never negative) (BR-13, BR-16, BR-17, BR-18). _(18 tests, all passing. Adds: threshold measured against the discount **amount** not the percentage; a double-rounding guard (3 × 0.335 → 1.01, not 1.00); and both directions of BR-18 — a stored rate resists a later change, while a new rate does reach the next order.)_
- [x] T044 [P] [US1] `test/service/OrderServiceTest.java` — atomic finalise (payment + status + figures commit together; failure leaves order Open, no payment); zero-item order not finalisable; price/tax-rate immutability after finalisation (BR-19, BR-09/15/18; SC-004, SC-005). _(7 tests; ConnectionFactory stubbed to run the transactional lambda inline, real BillingService + real TableService over a mocked DAO so the finalised figures are genuinely computed. Passing.)_

### Implementation for User Story 1

- [x] T045 [P] [US1] Implement `src/dao/PaymentMethodDAO.java` (read methods) with prepared statements.
- [x] T046 [P] [US1] Implement `src/dao/OrderDAO.java` (insert Open, update status+figures, unique order_number, queries) with prepared statements. _(`order_number` = `ORD` + `%06d` of `MAX(order_id)+1`, minted inside the transaction. `updateFinalised` carries an `AND status='Open'` guard so a concurrent double-finalise updates zero rows and the service raises a conflict instead of writing a second payment.)_
- [x] T047 [P] [US1] Implement `src/dao/OrderItemDAO.java` (add/remove line, list by order) with prepared statements.
- [x] T048 [P] [US1] Implement `src/dao/PaymentDAO.java` (insert 1:1 payment, read by order) with prepared statements.
- [x] T049 [US1] Implement `src/service/BillingService.java` — the single money engine: `computeSubtotal`, `applyDiscount` (caps + `APPROVE_DISCOUNT` above threshold from `system_config`), `computeTaxAndTotal` (tax-rate snapshot), all via `util/Money` (FR-12, FR-13, FR-14; BR-13/16/17/18). (depends on T012, T022, T046, T047) _(Built as a pure calculator over `Money` — it needs no DAOs, which is what lets T043 run without a database. Takes a `Supplier<AppConfig>` so an FR-31 tax change reaches the next finalisation instead of waiting for a restart; `AppContext.refreshConfig()` is called when a setting is saved. **Divergence from `contracts/pos-billing.md`:** an over-cap discount is **capped**, not rejected — see T049a.)_
- [x] T049a [US1] **Decision (resolved): capping is authoritative.** Over-cap discounts are **capped**, not rejected — TDD §8.3 and the constitution-gated T043 both mandate capping, and it matches BR-17's "result never negative". `contracts/pos-billing.md` ("Over-cap value → `ValidationException`") is the outlier and is treated as superseded; a negative value is still rejected as nonsense input. No code change needed — `BillingService.computeDiscountAmount` already caps.
- [x] T050 [US1] Implement `src/service/OrderService.java` — `openOrder` (unique number, dine-in table→Occupied, block existing open order), `addLine`/`removeLine` (Available only, unit-price snapshot, Open only), `applyDiscount`, `voidOrder`, and `finalise` as one atomic transaction (payment + Paid/Closed + stored figures + table→Needs Cleaning), rejecting zero-item orders (FR-10, FR-11, FR-15, FR-17; BR-14/15/19). (depends on T017, T022, T040, T046, T047, T048, T049) _(All mutations run through `ConnectionFactory.inTransaction`. A voided dine-in order frees its table via the only legal path Occupied→Needs Cleaning→Free.)_
- [x] T051 [US1] Implement `src/service/ReceiptService.java` — generate a PDF that reproduces the stored finalised figures exactly and reprints identically (FR-16, BR-20). (depends on T046, T047, T048) _(OpenPDF, A6 receipt page; reads back stored figures with zero recomputation, so a reprint is byte-identical after any later menu/tax edit.)_
- [x] T052 [US1] Implement `src/controller/OrderController.java` + `src/view/orders.fxml` — POS: order entry, live subtotal/discount/tax/total (all from `BillingService`), payment + finalise, print receipt; maps typed exceptions to FRD messages (FR-10…FR-17). _(On finalise the receipt PDF is written to the temp dir and opened via `Desktop`. Screen enum flag flipped to implemented.)_

**Checkpoint**: 🎯 MVP — order-to-receipt works end-to-end with atomic, immutable billing.
_Reached and verified end-to-end against the live DB._ A dine-in order of 2×12.50 + 1×8.00 computed subtotal 33.00 → tax 4.62 → total 37.62; a 10% discount took it to discount 3.30 / tax 4.16 / total 33.86; finalising with 40.00 cash produced 6.14 change, flipped the order to Paid/Closed and the table to Needs Cleaning, and generated a receipt. Changing the tax rate to 25% afterward left the finalised bill unchanged (BR-18), and a zero-item order was refused. All 65 tests pass; both constitution gates clean.

---

## Phase 6: User Story 4 — Manage inventory, suppliers, and purchasing (Priority: P2)

**Goal**: Managers record stock items and reorder levels, register suppliers, raise POs, receive
deliveries that increase stock atomically, and are alerted on low stock.

**Independent Test**: A Manager creates a stock item with a reorder level, registers a supplier,
raises a PO (status Ordered, stock unchanged), records a delivery that raises on-hand atomically,
sees low-stock items flagged, and clears the flag by receiving above the level.

### Tests for User Story 4

- [x] T053 [P] [US4] `test/service/PurchasingServiceTest.java` — atomic receive (movement + on-hand + PO line + PO status commit together; failure leaves stock and PO unchanged); received > ordered rejected (BR-24, NFR-03).

### Implementation for User Story 4

- [x] T054 [P] [US4] Implement `src/dao/SupplierDAO.java` (CRUD, unique name, PO-reference check) with prepared statements.
- [x] T055 [P] [US4] Implement `src/dao/StockItemDAO.java` (CRUD, add-on-hand, low-stock query) with prepared statements.
- [x] T056 [P] [US4] Implement `src/dao/StockMovementDAO.java` (insert ledger row) with prepared statements.
- [x] T057 [P] [US4] Implement `src/dao/PurchaseOrderDAO.java` (CRUD, unique po_number, status update) with prepared statements.
- [x] T058 [P] [US4] Implement `src/dao/PurchaseOrderItemDAO.java` (lines, add-received) with prepared statements.
- [x] T059 [US4] Implement `src/service/SupplierService.java` — save (unique name, format-validated contacts) and deactivate-not-delete with `RbacGuard.require(MANAGE_SUPPLIERS)` (FR-19; BR-05, BR-22). (depends on T022, T054)
- [x] T060 [US4] Implement `src/service/InventoryService.java` — save stock item (unique name, reorder ≥ 0), deactivate with history, `adjustStock` (atomic movement + on-hand), `lowStockItems` with `RbacGuard.require(MANAGE_STOCK)` (FR-18, FR-22; BR-05, BR-21, BR-25). (depends on T017, T022, T055, T056)
- [x] T061 [US4] Implement `src/service/PurchasingService.java` — `createPO` (active supplier, ≥1 line, no stock change) and `receiveDelivery` (atomic per TDD §5.4: movements + on-hand + received_qty + PO status + refresh flags) with `RbacGuard.require(MANAGE_PURCHASING)` (FR-20, FR-21; BR-23, BR-24). (depends on T017, T022, T055, T056, T057, T058)
- [x] T062 [P] [US4] Implement `src/controller/SupplierController.java` + `src/view/suppliers.fxml` (FR-19).
- [x] T063 [P] [US4] Implement `src/controller/InventoryController.java` + `src/view/inventory.fxml` — stock list with low-stock flagging (FR-18, FR-22).
- [x] T064 [US4] Implement `src/controller/PurchasingController.java` + `src/view/purchasing.fxml` — create PO and receive delivery (FR-20, FR-21).

**Checkpoint**: Inventory, suppliers, and purchasing work with atomic stock receipt.
_Reached and verified end-to-end against the live DB._ A supplier and a stock item (opening 0, reorder 10)
were created; a PO for 20 units was raised (status Ordered, on-hand unchanged, BR-23); a partial receipt of
8 left the PO Partially Received with on-hand 8 and still low; an over-receipt of 13 (only 12 outstanding)
was refused with stock unchanged; receiving the remaining 12 closed the PO (Received) at on-hand 20, off the
low list; a −5 adjustment took it to 15, and a −100 adjustment was rejected by the `chk_onhand_nonneg`
constraint and rolled back (on-hand still 15). All 76 tests pass; JavaFX-leak and prepared-statement gates
clean. Note: `adjustStock` takes a signed quantity but stores no free-text reason — `stock_movement` has no
such column in the schema, so the contract's `reason` parameter is intentionally omitted.

---

## Phase 7: User Story 5 — Manage staff records and reservations (Priority: P2)

**Goal**: Managers maintain staff records; cashiers/managers book tables without double-booking
and move reservations through their lifecycle.

**Independent Test**: A Manager creates and deactivates a staff record (history kept); a Cashier
books a future reservation on a free slot (table→Reserved), cannot create an overlapping booking
on the same table, and can seat/complete/cancel it.

### Tests for User Story 5

- [x] T065 [P] [US5] `test/service/ReservationServiceTest.java` — overlap algorithm (TDD §5.3): overlapping active bookings on the same table rejected; back-to-back non-overlapping allowed; cancelled/completed don't block (BR-27, FR-26).

### Implementation for User Story 5

- [x] T066 [P] [US5] Implement `src/dao/StaffDAO.java` (CRUD, active list, optional user link) with prepared statements.
- [x] T067 [P] [US5] Implement `src/dao/ReservationDAO.java` (CRUD, active bookings by table/window for overlap, status update) with prepared statements.
- [x] T068 [US5] Implement `src/service/StaffService.java` — save (distinct from user accounts) and deactivate-not-delete with `RbacGuard.require(MANAGE_STAFF)` (FR-23; BR-05, BR-26). (depends on T022, T066)
- [x] T069 [US5] Implement `src/service/ReservationService.java` — `create` (future date, required contact, party ≤ capacity warn/override, `hasOverlap` check, table→Reserved) and `seat`/`complete`/`cancel`/`markNoShow` state machine freeing holds, with `RbacGuard.require(MANAGE_RESERVATION)` (FR-24, FR-25, FR-26; BR-27/28/29). (depends on T014, T022, T040, T067)
- [x] T070 [P] [US5] Implement `src/controller/StaffController.java` + `src/view/staff.fxml` (FR-23).
- [x] T071 [P] [US5] Implement `src/controller/ReservationController.java` + `src/view/reservations.fxml` — booking + lifecycle (FR-24…FR-26).

**Checkpoint**: Staff and reservations work with double-booking prevention.
_Reached and verified end-to-end against the live DB._ A staff record was created and deactivated
(history kept); a booking on a free table set it Reserved; an overlapping window was refused while a
back-to-back one was allowed (BR-27); a party over capacity was refused without an override and accepted
with one (BR-28); seating set the table Occupied and completing sent it to Needs Cleaning (BR-29); and an
illegal transition (seating a completed booking) was rejected. All 85 tests pass; both constitution gates
clean. Note: the reservation↔table coupling uses the single-status table model — a booking marks a Free
table Reserved and releases it once no active booking remains.

---

## Phase 8: User Story 6 — Generate and export management reports (Priority: P3)

**Goal**: Managers/Administrators produce sales, inventory, and staff-activity reports over a
date range and export them, reconciling to finalised data.

**Independent Test**: With finalised orders and stock present, a Manager generates a sales report
whose totals reconcile to those orders (from stored figures), an inventory report flagging low
stock, and a staff-activity report per cashier, and exports any with parameters recorded; a
Cashier is denied.

### Implementation for User Story 6

- [ ] T072 [US6] Implement `src/service/ReportService.java` — `salesReport` (finalised orders only; totals, count, AOV, tax, discounts, per-item/category from stored figures), `inventoryReport` (low-stock), `staffActivityReport` (from `login_event` + finalised orders) with `RbacGuard.require(VIEW_REPORTS)` and start ≤ end validation (FR-27, FR-28, FR-29; BR-25, BR-30). (depends on T022, T046, T047, T028, T055)
- [ ] T073 [US6] Implement `src/util/ReportExporter.java` — export a report to PDF reproducing the on-screen content with a header of title/user/timestamp/parameters, no `javafx.*` types (FR-30). (depends on T072)
- [ ] T074 [US6] Implement `src/controller/ReportController.java` + `src/view/reports.fxml` — report parameters, display, and export; Cashier denied (FR-27…FR-30).

**Checkpoint**: All six user stories are independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Cross-cutting guarantees and final validation.

- [ ] T075 Wire the idle-timeout auto-logout (default 15 min from `system_config`) into the app shell/session (FR-04 edge case).
- [ ] T076 Verify the JavaFX-leak gate: `grep -rn "javafx" src/service src/dao src/domain src/util` returns nothing (Principle I).
- [ ] T077 [P] Audit all `src/dao/*` for parameterised prepared statements only — no string-concatenated SQL (Principle VII).
- [ ] T078 [P] Verify `db/schema.sql` indexes on FKs and lookup columns support the NFR-02 ~2-second targets; add any missing indexes.
- [ ] T079 [P] Add a project `README.md` build/run section referencing `quickstart.md`.
- [ ] T080 Run the `quickstart.md` acceptance walkthroughs (all six) and confirm SC-001…SC-010 hold (including SC-010: reference/system data is Administrator-only, audited, and never alters a finalised bill — FR-31).

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1)**: no dependencies.
- **Foundational (P2)**: depends on Setup — **blocks all user stories**.
- **US2 Auth (P3)**: depends on Foundational.
- **US3 Menu/Tables (P4)**: depends on Foundational.
- **US1 POS/Billing (P5)**: depends on Foundational + **US2** (session/RBAC) + **US3** (menu items via `MenuService`, tables via `TableService`).
- **US4 Inventory (P6)**, **US5 Staff/Reservations (P7)**: depend on Foundational (+ US3's `TableService` for reservations); independent of US1.
- **US6 Reporting (P8)**: depends on Foundational + US1 (finalised orders) + US4 (stock) + US2 (login events) for meaningful data.
- **Polish (P9)**: depends on all targeted stories being complete.

### Within each user story

- DAOs (all `[P]`) → Service → Controller/FXML.
- Constitution-gated tests (billing/RBAC/overlap/receipt) should be written alongside their service and MUST pass.

### Parallel opportunities

- Setup: T003, T004 in parallel.
- Foundational: T005–T014 (domain/enums/exceptions/utils) in parallel; T018–T020 in parallel after their deps.
- Each story's DAOs (marked `[P]`) run in parallel; controllers marked `[P]` run in parallel.
- After Foundational, **US3, US4-DAOs, US5-DAOs** can progress in parallel with US2; US1 waits on US2+US3.

---

## Parallel Example: User Story 1 (POS/Billing)

```bash
# Constitution-gated tests (write alongside services, must pass):
Task: "BillingServiceTest — TDD §8.3 worked cases in test/service/BillingServiceTest.java"
Task: "OrderServiceTest — atomic finalise + immutability in test/service/OrderServiceTest.java"

# DAOs in parallel (different files):
Task: "PaymentMethodDAO in src/dao/PaymentMethodDAO.java"
Task: "OrderDAO in src/dao/OrderDAO.java"
Task: "OrderItemDAO in src/dao/OrderItemDAO.java"
Task: "PaymentDAO in src/dao/PaymentDAO.java"
```

---

## Implementation Strategy

### MVP scope

The headline MVP is **User Story 1 (order-to-receipt)**, but it requires **US2 (auth)** and
**US3 (menu & tables)** as prerequisites. Minimum shippable path:

1. Phase 1 Setup → Phase 2 Foundational.
2. Phase 3 US2 (Auth & Admin) → Phase 4 US3 (Menu & Tables) → Phase 5 US1 (POS/Billing).
3. **STOP and VALIDATE**: run the US1 walkthrough (SC-002, SC-004, SC-005). Demo the MVP.

### Incremental delivery

Add US4 (Inventory & Purchasing) → US5 (Staff & Reservations) → US6 (Reporting), validating each
independently, then complete Phase 9 polish.

---

## Notes

- `[P]` = different files, no dependency on incomplete tasks.
- `[Story]` label maps each task to its spec.md user story for traceability (Principle VI).
- Every task traces to an FR/BR/NFR; the DAO/Service split preserves the JavaFX-free reusable
  core (Principles I, II).
- Commit after each task or logical group; stop at any checkpoint to validate a story.
