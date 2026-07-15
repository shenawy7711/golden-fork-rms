# Implementation Plan: Golden Fork RMS

**Branch**: `001-golden-fork-rms` | **Date**: 2026-07-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-golden-fork-rms/spec.md`; Technical Design
Document `TDD.md`; Functional Requirements `FRD.md`; Constitution v3.0.0.

## Summary

Golden Fork RMS is a single-location JavaFX desktop Restaurant Management System (Phase 1)
built so its business and data core is reused unchanged behind a web front end (Phase 2). It
delivers all 31 functional requirements across six modules — Authentication & Administration
(including FR-31 reference/system-data configuration), Menu & Table Management, Orders & Billing
(POS), Inventory & Suppliers, Staff & Reservations, and Reporting — over one shared MySQL
database.

**Technical approach**: strict one-directional layering **View → Controller → Service → DAO →
Database** using the prefix-free packages `app`, `controller`, `view`, `service`, `dao`,
`domain`, `util`, `config`. All calculation, validation, state, and access-control logic lives
in the Service layer; the `service`/`dao`/`domain`/`util` packages contain **zero** JavaFX
imports. All money math is centralised in one `BillingService` using unit-price and tax-rate
snapshots, with atomic order finalisation and atomic stock receipt. RBAC is enforced in the
business layer via an `RbacGuard`; passwords are stored only as salted BCrypt/PBKDF2 hashes.
Persistence is hand-written JDBC DAOs (no ORM) against the 18-table 3NF schema defined in the
TDD data dictionary, with full PK/FK/UNIQUE/CHECK constraints and a seed script. The Login and
Dashboard screens visually match the `design/` reference (`app.css` tokens, `screenshots/`
layout) without reusing its web/HTML source.

**Recommended build order** (module by module): **Auth & Admin → Menu & Tables → POS/Billing →
Inventory & Purchasing → Staff & Reservations → Reporting.**

## Technical Context

**Language/Version**: Java **Full JDK 8** — a JDK 8 distribution that bundles JavaFX (Oracle JDK 8,
Azul Zulu FX 8, or BellSoft Liberica Full 8). Plain OpenJDK 8 omits JavaFX and is excluded.

**Build / Editor**: **Maven** (`pom.xml`) for dependency management; **no ORM**. Editor-agnostic —
Cursor / VS Code (Extension Pack for Java) or Eclipse; no IDE-specific project files are committed.
JavaFX is provided by the Full JDK 8, **not** declared as a Maven dependency.

**Primary Dependencies** (Maven-managed unless noted):
- JavaFX via the **bundled `jfxrt.jar`** (FXML + CSS; one FXML + one controller per screen) —
  from the Full JDK 8, not a Maven artifact.
- **MySQL Connector/J** (JDBC driver) — hand-written DAOs, prepared statements, no ORM.
- **BCrypt** (jBCrypt) or PBKDF2 (JDK-built-in `javax.crypto`) for password hashing.
- A PDF/export library (e.g. **OpenPDF**) for receipts (FR-16) and report export (FR-30).
- **JUnit 5 + Mockito** for unit tests (billing engine and business rules).

**Storage**: **MySQL 8.x**, single shared database `rms`, schema normalised to ≥ 3NF
(18 tables), full referential + CHECK constraints. Reused unchanged in Phase 2.

**Testing**: JUnit 5 (worked billing examples from TDD §8.3 as the finance regression suite),
Mockito for DAO isolation, plus a DAO integration slice against a MySQL test schema.

**Target Platform**: On-premise Windows/Linux desktop terminals on the restaurant LAN;
**offline-capable** (NFR-08) — no internet dependency. Single deployable JAR + JRE + MySQL on
the LAN.

**Project Type**: Desktop application (layered MVC + Service/DAO core) with a swappable UI tier.

**Performance Goals**: Common POS actions (open order, add item, generate bill) respond within
≈2 seconds under normal single-restaurant load (NFR-02, SC-003). Billing is in-memory and O(n)
in line count; DB reads are indexed on FKs and lookup columns.

**Constraints**:
- `service`, `dao`, `domain`, `util` MUST NOT import any `javafx.*`/FXML type (grep-checkable).
- All money uses `DECIMAL(10,2)` with one HALF-UP rounding step per figure.
- All financial/stock mutations run in a single JDBC transaction (no partial writes).
- All DAO SQL uses parameterised prepared statements (no string concatenation).
- Build is **Maven**; editor-agnostic (Cursor/VS Code or Eclipse); JDK is a **Full JDK 8** (bundled JavaFX).
- Scope is fixed to **FR-01 … FR-31**; the BRD out-of-scope list is excluded.

**Scale/Scope**: One restaurant, ~12 dining tables, 3 roles, 6 modules, 31 FRs, 18 tables,
~12 services + ~15 DAOs, ~13 FXML screens (Auth, Dashboard, Users, System Config, Menu, Tables,
POS/Orders, Reservations, Inventory, Suppliers, Purchasing, Staff, Reports).

**No NEEDS CLARIFICATION remain** — the two previously-open items (split billing, discount
approval) are closed in the spec's *Resolved Decisions* (single payment per order; discount
approval in scope, threshold is admin-configured reference data).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

Evaluated against Constitution v2.0.0 (Principles I–VII + Technology Constraints).

| # | Principle | Plan compliance | Status |
|---|---|---|---|
| I | Strict Layered MVC & UI-framework independence | One-directional View→Controller→Service→DAO→DB; `service`/`dao`/`domain`/`util` are JavaFX-free (grep gate); all logic in Service; DAOs only SQL. | ✅ PASS |
| II | Canonical package structure (no `com.rms` prefix) | Exactly `app`, `controller`, `view`, `service`, `dao`, `domain`, `util`, `config`; one FXML + one controller per screen. | ✅ PASS |
| III | Single Billing Engine & immutable finalised bills | One `BillingService`; `order_item.unit_price` + `orders.tax_rate` snapshots; atomic finalise; zero-item orders non-finalisable; receipts reproduce stored figures. | ✅ PASS |
| IV | Business-layer RBAC & password security | `RbacGuard.require()` before every protected op; FRD §2.4 matrix authoritative; salted BCrypt/PBKDF2 only; last-active-admin protected; generic auth error. | ✅ PASS |
| V | Relational integrity — 3NF, constrained, deactivate-not-delete | 18-table 3NF schema with PK/FK/UNIQUE/NOT NULL/CHECK; status-flag soft-delete for users/staff/menu/suppliers/stock; on-hand only via `stock_movement`. | ✅ PASS |
| VI | Requirement traceability & fixed scope (FR-01…FR-31) | Every service/table/screen traces to an FR/BR/NFR (see data-model + contracts); `SystemConfigService`/`system_config`/System Config screen now trace to **FR-31**; out-of-scope list excluded. | ✅ PASS |
| VII | Fail-safe transactions & data integrity | Finalise, receive-delivery, stock-adjust each in one ACID transaction; typed exceptions; prepared statements everywhere. | ✅ PASS |
| — | Technology Constraints | Full JDK 8 / bundled `jfxrt.jar` / Maven build / editor-agnostic / MySQL-JDBC (no ORM) / offline. | ✅ PASS |

**Initial gate: PASS — no violations.** No entries required in Complexity Tracking.

**Design note (reconciliation)**: the `design/` reference includes an auth **Sign-up** tab with
a role picker. Self-service account creation conflicts with **FR-03** (only an Administrator
creates accounts). Resolution for v1: the Login screen matches the reference visually; the
Sign-up tab is a **prototype affordance and is out of scope** — account creation is an
Administrator-only function on the Users screen (FR-03). This is recorded in `research.md` and
does not add scope beyond FR-01…FR-31.

**Post-Phase-1 re-check: PASS** — the data model, contracts, and quickstart introduce no new
violations; billing/RBAC/transaction boundaries are preserved in the designed interfaces.

## Project Structure

### Documentation (this feature)

```text
specs/001-golden-fork-rms/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output — decisions & rationale
├── data-model.md        # Phase 1 output — 18-table schema, entities, state models
├── quickstart.md        # Phase 1 output — build/run/validate guide
├── contracts/           # Phase 1 output — service + UI (screen) contracts
│   ├── README.md
│   ├── auth-admin.md
│   ├── menu-tables.md
│   ├── pos-billing.md
│   ├── inventory-purchasing.md
│   ├── staff-reservations.md
│   ├── reporting.md
│   └── ui-screens.md
├── spec.md              # Feature specification (input)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

Prefix-free packages sitting at the source root (Principle II). Only `view` + `controller` are
JavaFX-aware and swapped in Phase 2; everything else is reused unchanged. **Maven** (`pom.xml`
at the repo root) manages dependencies; its `sourceDirectory`/`testSourceDirectory` point at
`src` and `test` so the prefix-free package layout below is preserved (no `com.rms` prefix).

```text
pom.xml                          # Maven build — deps: Connector/J, jBCrypt, OpenPDF, JUnit 5, Mockito
src/
├── app/                         # bootstrap / entry point / wiring
│   └── Main.java                # extends javafx.application.Application; loads Auth FXML
│
├── controller/                  # ONE controller per screen (JavaFX-aware)
│   ├── AuthController.java
│   ├── DashboardController.java
│   ├── UserController.java
│   ├── SystemConfigController.java
│   ├── MenuController.java
│   ├── TableController.java
│   ├── OrderController.java          # POS
│   ├── ReservationController.java
│   ├── InventoryController.java
│   ├── SupplierController.java
│   ├── PurchasingController.java
│   ├── StaffController.java
│   └── ReportController.java
│
├── view/                        # FXML + CSS + view resources (JavaFX-aware)
│   ├── auth.fxml
│   ├── dashboard.fxml
│   ├── users.fxml
│   ├── system-config.fxml
│   ├── menu.fxml
│   ├── tables.fxml
│   ├── orders.fxml
│   ├── reservations.fxml
│   ├── inventory.fxml
│   ├── suppliers.fxml
│   ├── purchasing.fxml
│   ├── staff.fxml
│   ├── reports.fxml
│   └── css/app.css              # copied/adapted from design/app.css (tokens/typography)
│
├── service/                     # BUSINESS LAYER — no javafx.* imports
│   ├── AuthService.java
│   ├── UserService.java
│   ├── SystemConfigService.java         # FR-31 — Administrator-only reference/system data
│   ├── MenuService.java
│   ├── TableService.java
│   ├── OrderService.java
│   ├── BillingService.java           # SINGLE money engine
│   ├── ReceiptService.java
│   ├── InventoryService.java
│   ├── SupplierService.java
│   ├── PurchasingService.java
│   ├── StaffService.java
│   ├── ReservationService.java
│   ├── ReportService.java
│   ├── exception/                    # ValidationException, AuthorizationException,
│   │                                 #   ConflictException, PersistenceException
│   └── security/
│       ├── RbacGuard.java
│       ├── Permission.java           # enum of permissions ← FRD §2.4 matrix
│       ├── PasswordHasher.java       # BCrypt/PBKDF2
│       └── Session.java              # authenticated user + role (no javafx)
│
├── dao/                         # DATA ACCESS — JDBC only, prepared statements
│   ├── ConnectionFactory.java        # + transaction helper (begin/commit/rollback)
│   ├── RoleDAO.java
│   ├── UserDAO.java
│   ├── StaffDAO.java
│   ├── LoginEventDAO.java
│   ├── MenuCategoryDAO.java
│   ├── MenuItemDAO.java
│   ├── DiningTableDAO.java
│   ├── OrderDAO.java
│   ├── OrderItemDAO.java
│   ├── PaymentDAO.java
│   ├── PaymentMethodDAO.java
│   ├── SupplierDAO.java
│   ├── StockItemDAO.java
│   ├── StockMovementDAO.java
│   ├── PurchaseOrderDAO.java
│   ├── PurchaseOrderItemDAO.java
│   ├── ReservationDAO.java
│   └── SystemConfigDAO.java
│
├── domain/                      # POJOs + enums — no framework deps
│   ├── Role.java  User.java  Staff.java  LoginEvent.java
│   ├── MenuCategory.java  MenuItem.java  DiningTable.java
│   ├── Order.java  OrderItem.java  Payment.java  PaymentMethod.java
│   ├── Supplier.java  StockItem.java  StockMovement.java
│   ├── PurchaseOrder.java  PurchaseOrderItem.java  Reservation.java
│   ├── SystemConfig.java
│   └── enums/                        # OrderStatus, OrderType, DiscountType, TableStatus,
│                                     #   ReservationStatus, PoStatus, MovementType, Status,
│                                     #   Availability, LoginEventType, RoleName
│
├── util/                        # framework-free helpers
│   ├── Money.java                    # DECIMAL(10,2) HALF-UP helpers
│   ├── Validation.java               # FRD Appendix A field rules
│   ├── DateTimeUtil.java
│   └── ReportExporter.java           # PDF/print (no javafx types in signatures)
│
└── config/                      # configuration + reference loading
    ├── AppConfig.java                # reads db + app settings
    ├── DbSettings.java
    └── ReferenceDataLoader.java      # tax_rate, idle_timeout, login_max_attempts

db/
├── schema.sql                   # 18 tables, all keys/FK/UNIQUE/CHECK (3NF)
└── seed.sql                     # roles, payment methods, system_config, first admin

test/
├── service/
│   ├── BillingServiceTest.java       # TDD §8.3 worked examples (finance regression)
│   ├── OrderServiceTest.java
│   ├── ReservationServiceTest.java   # overlap algorithm
│   └── RbacGuardTest.java
└── dao/
    └── (integration slice against a MySQL test schema)
```

**Structure Decision**: Single desktop project with the constitution's canonical prefix-free
package map. The reuse boundary is physical: `view` + `controller` (JavaFX) vs. the reusable
core `service`/`dao`/`domain`/`util`/`config` + the MySQL schema. `db/` holds the DDL + seed;
`test/` holds JUnit tests with the billing suite first-class; `pom.xml` at the root drives the
Maven build and dependency resolution (JavaFX excepted — it comes from the Full JDK 8). This
layout is the direct realisation of Principles I and II and TDD §2.3, and is editor-agnostic
(Cursor/VS Code or Eclipse open it as a Maven project).

## Complexity Tracking

> No constitutional violations — this section is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(none)_ | — | — |
