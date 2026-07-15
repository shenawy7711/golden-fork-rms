# Phase 0 — Research & Decisions: Golden Fork RMS

**Feature**: 001-golden-fork-rms | **Date**: 2026-07-14 | **Input**: TDD.md, FRD.md,
Constitution v3.0.0, `design/`.

All Technical Context items are resolved; there are **no open NEEDS CLARIFICATION**. The two
formerly-open spec items are closed (see D-1, D-2). Each decision below records what was chosen,
why, and what was rejected.

---

## D-1 — Split billing / multiple payments per order

- **Decision**: **Out of scope for v1.** Each order is settled by exactly **one** payment
  (`payment.order_id` is `UNIQUE`, 1:1 with `orders`).
- **Rationale**: FR-15 and the spec's Resolved Decisions fix single-payment settlement; the BRD
  "split a bill" reference is deferred to a future phase. A 1:1 constraint keeps finalisation
  atomic and the schema simple.
- **Alternatives rejected**: A `payment` N:1 to `orders` (multiple tenders) — adds
  reconciliation complexity and settlement-state logic not required by FR-01…FR-31.

## D-2 — Discount approval policy

- **Decision**: **In scope.** Discounts **above a configurable threshold** require
  Manager/Administrator authorisation before they apply; the authorisation is recorded for
  audit. The threshold is admin-configured reference data (`system_config`).
- **Rationale**: FR-13 + Resolved Decisions. Keeps front-line discounting fast at/below the
  threshold while protecting margin above it.
- **Implementation**: `BillingService.applyDiscount()` consults the threshold from
  `system_config`; above it, `RbacGuard.require(APPROVE_DISCOUNT)` must pass (Manager/Admin) and
  the approving user is captured. Threshold key: `discount_approval_threshold`.
- **Alternatives rejected**: Hard-coded threshold (not tunable per restaurant); no approval at
  all (fails FR-13 audit requirement).

## D-3 — Password hashing algorithm

- **Decision**: **BCrypt** (jBCrypt) as primary; PBKDF2 (`javax.crypto`, JDK 8 built-in) as an
  acceptable fallback. Store only the salted one-way hash in `user_account.password_hash`
  (`VARCHAR(100)`).
- **Rationale**: BR-02, NFR-04. BCrypt is salted + adaptive; jBCrypt is a tiny, well-known,
  JDK-8-compatible library. PBKDF2 needs no third-party jar if dependency-free hashing is
  preferred. `VARCHAR(100)` comfortably holds a 60-char BCrypt hash.
- **Alternatives rejected**: Plain SHA-256 (no adaptive cost, weaker); Argon2 (no simple JDK-8
  library, overkill for on-prem single-site).

## D-4 — Money type & rounding

- **Decision**: `java.math.BigDecimal` in the service layer, `DECIMAL(10,2)` columns; **one
  HALF-UP rounding step per figure** (discount_amount, tax_amount, total). Tax rate stored as
  `DECIMAL(5,4)`.
- **Rationale**: BR-13, BR-16, BR-18. `double` is unsafe for currency. Centralising rounding in
  `BillingService` and `util/Money` guarantees the worked examples (TDD §8.3, e.g. 100.00 → 10%
  discount → 10% tax → 99.00) reproduce exactly (SC-002).
- **Alternatives rejected**: `double`/`float` (rounding drift); rounding at display time only
  (figures would not persist consistently).

## D-5 — Snapshots for immutable finalised bills

- **Decision**: Copy `menu_item.price` into `order_item.unit_price` when a line is added; copy
  the in-force tax rate into `orders.tax_rate` at finalisation. Reports and receipts read stored
  figures, **never** current menu prices.
- **Rationale**: BR-09, BR-15, BR-18; SC-004. Later menu/tax edits must not alter finalised
  bills or reprints.
- **Alternatives rejected**: Join to live `menu_item.price` at read time (would retro-price
  history — a defect).

## D-6 — Stock changes only via the movement ledger

- **Decision**: `stock_item.quantity_on_hand` is only ever changed inside a transaction that
  also writes a `stock_movement` row (`Receipt` or `Adjustment`). No arbitrary direct edits.
- **Rationale**: BR-21, BR-24, NFR-03. On-hand is the running sum of movements — auditable and
  reconcilable. Receipt is atomic (movement + on-hand + PO line + PO status commit together).
- **Alternatives rejected**: Direct `UPDATE quantity_on_hand` from the UI (no audit trail,
  breaks BR-21).

## D-7 — Transaction & connection strategy (JDBC, no ORM)

- **Decision**: Hand-written DAOs with prepared statements. A `ConnectionFactory` provides
  connections and a transaction helper (`autoCommit=false`; commit on success, rollback on any
  exception). Multi-DAO business transactions (finalise, receive-delivery, adjust) are
  orchestrated in the **service** layer, sharing one `Connection`.
- **Rationale**: NFR-03, NFR-06, Principle VII, Technology Constraints (no ORM). Explicit SQL
  and transaction control; DAOs stay reusable and framework-free for Phase 2.
- **Alternatives rejected**: ORM (JPA/Hibernate) — prohibited by the stack; hides transaction
  boundaries. Per-DAO independent connections — cannot span a multi-row transaction atomically.

## D-8 — RBAC enforcement point

- **Decision**: Every protected service method calls `RbacGuard.require(session, permission)`
  **before** acting. UI hides/disables forbidden controls as defence-in-depth only. Permissions
  are modelled as an enum keyed to the FRD §2.4 matrix; Administrator ⊇ Manager ⊇ Cashier.
- **Rationale**: BR-03, FR-02, NFR-04, NFR-06. Security must survive the Phase-2 web swap, so it
  cannot live in the View. Bypassing the UI must not bypass the check.
- **Alternatives rejected**: UI-only gating (fails FR-02 acceptance — a forbidden call by any
  path must be rejected and recorded).

## D-9 — Reservation overlap detection

- **Decision**: Application check in `ReservationService.hasOverlap(table, window)` using the
  TDD §5.3 predicate (`r.start < req.end AND r.start + r.duration > req.start`, status in
  Booked/Seated, excluding self), inside the create/reschedule transaction.
- **Rationale**: BR-27, FR-26. Interval-overlap test is exact; scoping to active statuses lets
  cancelled/completed rows not block rebooking.
- **Alternatives rejected**: A DB-only unique constraint (cannot express interval overlap in
  MySQL portably); ignoring duration (back-to-back bookings would false-conflict).

## D-10 — JavaFX packaging (Full JDK 8, bundled `jfxrt.jar`) & Maven build

- **Decision**: Target JavaFX from a **Full JDK 8's bundled `jfxrt.jar`** (Oracle JDK 8, Azul
  Zulu FX 8, or BellSoft Liberica Full 8 — no separate module path, no OpenJFX modules). Build
  with **Maven** (`pom.xml`); JavaFX is provided by the JDK, so it is **not** a Maven dependency.
  Editor-agnostic (Cursor/VS Code or Eclipse). FXML per screen, one controller per screen, styled
  by `view/css/app.css`.
- **Rationale**: Technology Constraints (Constitution v3.0.0). A Full JDK 8 ships JavaFX in the
  JRE, so no extra runtime distribution; Maven gives declarative dependency management that works
  in any editor. Plain OpenJDK 8 is excluded because it omits JavaFX.
- **Alternatives rejected**: JDK 11+ with modular OpenJFX (violates the pinned JDK-8 stack);
  hand-managed jars + Eclipse build path (superseded by Maven; editor-locked); Swing (not
  mandated; weaker CSS/FXML story).

## D-11 — Reports & receipts (PDF/export)

- **Decision**: Generate receipts (FR-16) and report exports (FR-30) via a PDF library
  (**OpenPDF** recommended; JasperReports acceptable). `util/ReportExporter` and
  `ReceiptService` expose framework-free signatures (no `javafx.*`).
- **Rationale**: FR-16, FR-30, BR-20. Receipts reproduce stored figures exactly and reprint
  identically; exports carry title/user/timestamp/parameters. Keeping export in `util`/service
  preserves Phase-2 reuse.
- **Alternatives rejected**: Rendering receipts from JavaFX nodes (would couple output to the
  UI framework — breaks reuse and idempotent reprint guarantees).

## D-12 — Design reconciliation: auth Sign-up vs. FR-03

- **Decision**: Match the Login screen to `design/screenshots/auth-login.png` and `app.css`
  visually. The **Sign-up** tab in the reference is a **prototype affordance and is out of
  scope**: account creation is Administrator-only via the Users screen (FR-03). The dashboard
  "Viewing as" role switcher is likewise **demo-only and removed** — role comes from the
  authenticated `Session`.
- **Rationale**: FR-03 (only Administrators create accounts), FR-02 (role from session, checked
  in the business layer). The `design/` folder is explicitly reference-only; its web/HTML source
  is not reused. Building self-signup would add out-of-scope capability (Principle VI).
- **Alternatives rejected**: Implementing self-service sign-up (violates FR-03 and adds scope
  outside FR-01…FR-31); reusing the design's HTML/JS (prohibited — reference only).

## D-13 — First-run seed data

- **Decision**: `db/seed.sql` inserts the three `role` rows (Administrator/Manager/Cashier),
  the `payment_method` rows (Cash/Card/Other), baseline `system_config` (`tax_rate`,
  `idle_timeout_min`=15, `login_max_attempts`=5, `reservation_slot_minutes`=90,
  `discount_approval_threshold`), and **one initial active Administrator** with a hashed
  password. Guarantees BR-06 (≥1 active admin) from first boot.
- **Rationale**: The app cannot bootstrap RBAC, billing, or login without roles, methods, tax
  rate, and an admin to create other users (FR-01/FR-03/FR-14).
- **Alternatives rejected**: First-run wizard (more code; not required by FRD); empty DB (no way
  to log in — chicken-and-egg).

---

## Best-practice notes carried into Phase 1

- **JavaFX-leak gate**: CI/local check — `grep -r "javafx" src/service src/dao src/domain
  src/util` MUST return nothing (Principle I).
- **Billing gate**: `BillingServiceTest` implements TDD §8.3 cases and must pass before any
  money-touching change merges.
- **Prepared statements only**: no string-concatenated SQL anywhere in `dao/` (Principle VII).
- **Soft-delete**: users, staff, menu items, suppliers, stock items deactivate via a `status`
  flag; hard delete only when no dependent history (Principle V, BR-05).
- **Indexes**: FKs and lookup columns (`orders.status`, `orders.created_at`, `dining_table
  .status`, `reservation.table_id`+`reservation_datetime`, `stock_item.status`) indexed for the
  NFR-02 2-second target.
