<!--
SYNC IMPACT REPORT
==================
Version change: 2.0.0 → 3.0.0
Bump rationale: MAJOR (stack change). The pinned Technology Constraints are amended: build
  toolchain **Eclipse project → Maven (`pom.xml`)**; **IDE made editor-agnostic** (Cursor / VS
  Code with the Java extension, or Eclipse); JDK pinned to a **Full JDK 8** that bundles JavaFX
  (Oracle / Zulu FX / Liberica Full — plain OpenJDK 8 excluded). Per this document's versioning
  policy, "a scope/stack change" is a MAJOR bump. No core principle (I–VII) changed; the JavaFX
  reuse boundary, layering, billing, RBAC, and integrity rules are all unaffected — only the
  editor/build tooling and JDK-distribution wording moved. Also reconciles the shared database
  name to **`rms`** (was `goldenfork_rms`) to match the built database.

Prior amendment retained (v2.0.0): Principle VI scope FR-01…FR-30 → FR-01…FR-31 (FR-31
  "Configure Reference / System Data"; BR-31). Rationale: closed the traceability gap where the
  System Config screen had no owning FR; "a scope change" is MAJOR under the versioning policy.

Principles defined (7) — unchanged in substance:
  I.   Strict Layered MVC & UI-Framework Independence
  II.  Canonical Package Structure
  III. Single Billing Engine & Immutable Finalised Bills
  IV.  Business-Layer RBAC & Password Security
  V.   Relational Integrity — 3NF, Constrained, Deactivate-Not-Delete
  VI.  Requirement Traceability & Fixed Scope (FR-01…FR-31)   ← scope extended (+FR-31)
  VII. Fail-Safe Transactions & Data Integrity

Sections: Technology Constraints, Development Workflow & Quality Gates, Governance (unchanged).

Templates & docs updated for the v3.0.0 stack change:
  ✅ plan.md — Technical Context (Maven build, editor-agnostic, Full JDK 8, db `rms`), Constitution
     Check Technology-Constraints row, and constitution ref → v3.0.0.
  ✅ tasks.md — T001/T002 (Maven project + `pom.xml` instead of Eclipse build path), T004 (db
     `rms`), constitution ref → v3.0.0.
  ✅ TDD.md §2.5 — Build=Maven, IDE=editor-agnostic, JDK=Full JDK 8 rows.
  ✅ research.md D-10 — JavaFX packaging note re-worded (Maven + Full JDK 8, not Eclipse).
  ✅ quickstart.md — prerequisites & build/run steps re-worded for Maven / any editor; db `rms`.
  ✅ README.md — planned-technology list (Maven; Full JDK 8; any editor).

Docs updated for the v2.0.0 scope change (retained):
  ✅ spec.md — FR-31 in Module 1; BR-31; Assumptions/Resolved Decisions/SC-010/Key Entities.
  ✅ plan.md / tasks.md — SystemConfigService added; FR-31 traced.
  ✅ data-model.md — `system_config` re-traced to FR-31.
  ✅ contracts/auth-admin.md — SystemConfigService contract.
  ✅ FRD.md — FR-31 in §3.1; BR-31; §9 + Appendix A; rev → v1.1.
  ✅ RMS_Business_Use_Cases.md — UC-31; BUC-10 map; BR-31.
  ✅ .specify/templates/*.md, .claude/skills/speckit-*/SKILL.md — generic; no stale refs.

Prior (v1.0.0) TODOs remain reconciled:
  ✅ TDD.md §2.3/§2.5/§7.4 — prefix-free layout, Full JDK 8 / bundled jfxrt.jar / Maven.
  No unresolved bracket tokens remain.
-->

# Golden Fork RMS Constitution

Golden Fork RMS is a JavaFX desktop Restaurant Management System (Phase 1) designed so its
core is reused unchanged behind a web front end (Phase 2). This constitution is the
non-negotiable engineering charter for that goal. It governs BRD v1.0, FRD v1.1 (FR-01…FR-31,
BR-01…BR-31, NFR-01…NFR-08), and TDD v1.0. Where a lower document conflicts with this one,
this constitution wins and the lower document MUST be amended.

## Core Principles

### I. Strict Layered MVC & UI-Framework Independence

Dependencies flow in exactly one direction: **View → Controller → Service → DAO → Database**.
No layer may call "upward" or skip a layer to reach the database.

- The `service`, `dao`, `domain`, and `util` layers MUST NOT import, reference, or depend on
  any JavaFX type (`javafx.*`), FXML, or any other presentation-framework type. This is
  mechanically checkable: a grep for `javafx` under those packages MUST return nothing.
- ALL business behaviour — calculation, validation, state transitions, and access-control
  checks — MUST live in the Service (business) layer. Controllers are thin: they translate UI
  events into service calls and map results/exceptions back to the View. No SQL and no
  business rule may live in View or Controller code.
- DAOs contain only SQL/CRUD and row↔object mapping; they hold no business rules.

**Rationale:** Phase 2 replaces `view` + `controller` with a web tier and reuses
`service`/`dao`/`domain`/`util` verbatim against the same MySQL schema (NFR-05, NFR-06, BO-6).
Any JavaFX leak into the core, or any rule stranded in the UI, breaks that reuse and is a
defect — not a style preference.

### II. Canonical Package Structure

The Java codebase MUST use exactly these top-level packages, with **no `com.rms` (or other)
prefix**:

- `app` — application bootstrap / entry point and wiring.
- `controller` — one controller class per screen, bound to that screen's FXML.
- `view` — FXML, CSS, and view-side resources.
- `service` — business logic (one service per functional module; plus RBAC/session).
- `dao` — DAO interfaces and their JDBC implementations, connection management.
- `domain` — POJOs and enums (no framework dependencies).
- `util` — money, validation, date/time, export, and other framework-free helpers.
- `config` — application/database configuration and reference-data loading.

Each screen has **exactly one** FXML file and **exactly one** controller. New code MUST be
placed in the package matching its responsibility; a class that would need to sit in two
layers is a design smell to be split.

**Rationale:** A fixed, prefix-free package map makes the layer of every class obvious,
keeps the reusable core (`service`/`dao`/`domain`/`util`/`config`) physically separable from
the swappable UI (`view`/`controller`), and enforces the one-controller-per-screen discipline
FXML expects.

### III. Single Billing Engine & Immutable Finalised Bills

All monetary arithmetic MUST be performed by **one `BillingService`** in the Service layer.
No other class — and never a controller or view — may compute subtotals, discounts, tax, or
totals.

- Every order line MUST store a **unit-price snapshot** taken at the moment the item is added
  (BR-15). Each finalised order MUST store the **tax-rate snapshot** in force at finalisation
  (BR-18). Changing a menu price or the configured tax rate afterwards MUST NOT alter any
  already-finalised bill (BR-09).
- Money is computed as: `line_total = unit_price_snapshot × quantity`;
  `subtotal = Σ line_total`; `discountable = subtotal − discount_amount` (never < 0, BR-17);
  `tax = round(discountable × tax_rate)`; `total = discountable + tax`. All money uses
  two-decimal precision with one consistent HALF-UP rounding step per figure (BR-13, BR-16).
- Finalisation MUST be **atomic**: payment record, order status, and stored figures
  (subtotal, discount, tax rate, tax, total) commit together in a single transaction or not at
  all (BR-19). An order with zero items MUST NOT be finalisable. A finalised order is
  immutable; receipts reproduce the stored figures exactly and reprint identically (BR-20).

**Rationale:** Centralised, snapshotted, atomic money math is the direct mitigation of the
BRD's "Incorrect financial calculations" risk and the foundation of BO-2; it is also the part
of the core most heavily unit-tested and most costly to get wrong.

### IV. Business-Layer RBAC & Password Security

Access control is a business-layer responsibility, not a UI convenience.

- Every protected operation MUST verify the caller's role in the Service layer (e.g. via an
  `RbacGuard` check) **before** acting, independent of whether the UI hid or disabled the
  control (BR-03, FR-02). Hiding a button in the View is defence-in-depth, never the only
  gate — a role-forbidden call MUST be rejected even if the UI were bypassed.
- The authoritative permission set is the FRD §2.4 Role–Permission Matrix (Administrator ⊇
  Manager, Cashier as listed). Cashiers MUST NOT reach price changes, stock management, or
  management reports by any path.
- Passwords MUST be stored **only** as salted one-way hashes (e.g. BCrypt/PBKDF2). Plain-text
  passwords MUST NEVER be stored, logged, or displayed anywhere — DB, log files, or screens
  (BR-02, NFR-04). Authentication failures return a generic message that does not disclose
  which field was wrong. At least one active Administrator MUST always exist (BR-06).

**Rationale:** Security enforced only in the UI is not enforced at all once the same core is
exposed over the web in Phase 2; both RBAC and credential handling must hold at the layer that
survives the migration (NFR-04, NFR-06).

### V. Relational Integrity — 3NF, Constrained, Deactivate-Not-Delete

All operational data lives in one shared MySQL database normalised to at least **Third Normal
Form** (NFR-07), used unchanged by both phases.

- The schema MUST enforce integrity in the database, not only in code: PRIMARY KEYs, FOREIGN
  KEYs, UNIQUE constraints (usernames, category names, table labels, supplier/stock names —
  BR-04/08/11/21/22), NOT NULL, and CHECK constraints for non-negative money/quantities.
- Records that carry history — user accounts, staff, menu items, suppliers, stock items —
  MUST be **deactivated (status flag), never hard-deleted**, preserving referential integrity
  and audit history (BR-05, BR-10, BR-26). Hard delete is permitted only for a row with no
  dependent historical references.
- Stock on hand MUST change only through recorded `stock_movement` ledger rows (receipts or
  logged adjustments), never by arbitrary direct edits (BR-21).

**Rationale:** A constrained 3NF schema is the last line of defence for financial and stock
correctness (NFR-03) and the shared contract both phases depend on; soft-delete keeps reports
and receipts reproducible after staff, menus, or suppliers change.

### VI. Requirement Traceability & Fixed Scope (FR-01…FR-31)

Scope is fixed to **FR-01 through FR-31** as specified in FRD v1.1 (FR-31 "Configure Reference /
System Data" was added by the v2.0.0 amendment). The out-of-scope list (online ordering,
payment-gateway processing, kitchen displays, loyalty, payroll, multi-branch, mobile app) MUST
NOT be built in Phase 1 or the first web migration.

- Every unit of work — spec, plan, task, class, table, and test — MUST trace to at least one
  FR, BR, or NFR identifier. Code or schema that traces to nothing is out of scope and MUST be
  removed or justified by an approved amendment.
- New capability that is not covered by FR-01…FR-31 requires a constitution/requirements
  amendment (see Governance) before implementation; it is not added silently.

**Rationale:** Fixed, traceable scope is the mitigation for the BRD's "scope creep" risk and
guarantees the delivered system maps cleanly back to agreed business objectives (BO-1…BO-6).

### VII. Fail-Safe Transactions & Data Integrity

Every multi-row financial or stock mutation MUST run inside a single ACID transaction.

- Order finalisation (Principle III), delivery receipt (stock movement + on-hand + PO status,
  BR-24), and logged stock adjustments MUST commit fully or roll back fully. A failed
  operation MUST leave **no partial data** (NFR-03) — the order stays Open, stock and PO
  status unchanged.
- Services validate inputs (FRD Appendix A rules) before persistence; the database provides
  the second line of defence via constraints. Services MUST raise typed exceptions
  (validation / authorization / conflict / persistence) that controllers translate into the
  user-facing messages defined in the FRD.
- All DAO SQL MUST use parameterised prepared statements; string-concatenated queries are
  prohibited (SQL-injection defence).

**Rationale:** Partial writes to money or stock destroy trust and reconciliation; atomicity
plus constraint-backed validation is what makes NFR-03 real rather than aspirational.

## Technology Constraints

The Phase 1 stack is pinned. Changes require an amendment.

- **Language/Runtime:** Java **JDK 8**.
- **JDK distribution:** a **Full JDK 8** that bundles JavaFX (Oracle JDK 8, Azul Zulu FX 8, or
  BellSoft Liberica Full 8). Plain OpenJDK 8 omits JavaFX and MUST NOT be used.
- **Desktop UI:** JavaFX loaded via the **bundled `jfxrt.jar`** (not a separate module path);
  screens defined in **FXML**, one FXML + one controller per screen.
- **Build:** **Maven** (`pom.xml`) for dependency management; **no ORM**. JavaFX is provided by
  the Full JDK 8, not declared as a Maven dependency.
- **IDE / Editor:** **editor-agnostic** — any editor with Java support (Cursor / VS Code with the
  Extension Pack for Java, or Eclipse). Because the build is Maven, no IDE-specific project files
  are required or committed.
- **Persistence:** **MySQL** accessed via **JDBC** (hand-written DAOs + prepared statements;
  no ORM). Schema normalised to ≥ 3NF with full constraints (Principle V).
- **Offline:** The desktop phase MUST run on the local machine/LAN with no internet dependency
  (NFR-08).
- **Reuse boundary:** Only `view` and `controller` are JavaFX-aware and swappable in Phase 2;
  `service`, `dao`, `domain`, `util`, `config`, and the MySQL schema are reused unchanged.

Note: TDD v1.0 §2.3, §2.5, and §7.4 have been reconciled to this section and Principle II
(Full JDK 8 / bundled `jfxrt.jar` / Maven / editor-agnostic; prefix-free `view` + `controller`
layout).

## Development Workflow & Quality Gates

- **Constitution Check (planning gate).** Every implementation plan MUST evaluate Principles
  I–VII before design work proceeds and again after design. A violation MUST be removed or
  recorded in the plan's Complexity Tracking with an explicit, approved justification.
- **Layer/JavaFX-leak gate.** A change MUST NOT introduce any `javafx.*` import under
  `service`, `dao`, `domain`, or `util`; reviewers reject such changes.
- **Billing gate.** Any change touching money math MUST go through `BillingService` and MUST
  ship with/against the worked-example unit tests (TDD §8.3: subtotal, % discount, fixed-cap,
  tax & total, immutability). These tests MUST pass.
- **RBAC & security gate.** New protected operations MUST include a business-layer role check
  and MUST NOT store or log plain-text passwords.
- **Traceability gate.** Each PR/change references the FR/BR/NFR it satisfies. Untraceable
  scope is rejected.
- **Data-integrity gate.** New financial/stock mutations MUST be transactional and MUST use
  prepared statements; new schema MUST carry the constraints required by Principle V.

## Governance

This constitution supersedes all other engineering practices and lower documents (BRD, FRD,
TDD) where they conflict; the lower document is then amended to match.

- **Amendments** MUST be proposed in writing with rationale and the affected FR/BR/NFR
  identifiers, reviewed and approved by the Development Lead (and the Owner/Sponsor for any
  scope change to FR-01…FR-31 or the pinned stack), and recorded here with a version bump.
- **Versioning policy (semantic):** **MAJOR** = backward-incompatible principle removal or
  redefinition, or a scope/stack change; **MINOR** = a new principle/section or materially
  expanded guidance; **PATCH** = clarifications and wording that do not change obligations.
- **Compliance review:** Every plan and PR MUST verify compliance with the applicable gates
  above. Complexity or deviation MUST be justified in the plan's Complexity Tracking, not
  merged silently. Recurring violations trigger a review of whether the code or the
  constitution must change — via amendment, never by quiet drift.

**Version**: 3.0.0 | **Ratified**: 2026-07-14 | **Last Amended**: 2026-07-15
