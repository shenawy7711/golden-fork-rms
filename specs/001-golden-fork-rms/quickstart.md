# Quickstart — Golden Fork RMS (build, run, validate)

A validation/run guide for the Phase-1 JavaFX desktop app. Implementation detail lives in the
code and in `tasks.md` (created by `/speckit-tasks`). Paths reference the layout in
[plan.md](./plan.md).

## Prerequisites

- **Full JDK 8** with bundled JavaFX (Oracle JDK 8, Azul Zulu FX 8, or BellSoft Liberica Full 8 —
  plain OpenJDK 8 omits JavaFX). Verify: `java -version` → `1.8.x`.
- **Maven** (`mvn -v`) for the build; dependencies (Connector/J, jBCrypt, OpenPDF, JUnit 5,
  Mockito) are declared in `pom.xml` and resolved automatically. JavaFX is **not** a Maven
  dependency — it comes from the Full JDK 8.
- **MySQL 8.x** running on `localhost:3306` (or the LAN host), with the `rms` database.
- Any editor with Java support — **Cursor / VS Code** (Extension Pack for Java) or Eclipse; open
  the folder as a Maven project.

## 1. Create the database

```sql
-- run db/schema.sql then db/seed.sql against the `rms` database
mysql -u root -p rms < db/schema.sql
mysql -u root -p rms < db/seed.sql
```

Expected: 18 tables created (see [data-model.md](./data-model.md)); seed inserts the 3 roles,
3 payment methods, baseline `system_config` (tax rate, timeouts, discount threshold), and **one
active Administrator** account (BR-06 satisfied from first boot). *(If the schema was built by
hand in DBeaver, export its DDL to `db/schema.sql` first so the file and the live DB match.)*
Ensure all tables are **InnoDB** (foreign keys + transactions depend on it — NFR-03) and
`utf8mb4`.

## 2. Configure the connection

Set DB host/user/password in `config/db.properties` (read by `AppConfig` / `DbSettings` →
`ConnectionFactory`). Use `localhost:3306`, database `rms`, and a non-root app user. No internet
access is required (NFR-08) — the app talks to MySQL on the LAN only.

## 3. Build & run

```bash
mvn compile        # or `mvn package` for the deployable JAR
mvn exec:java -Dexec.mainClass=app.Main    # or run app.Main from your editor
```

`app.Main` (extends `javafx.application.Application`) loads `view/auth.fxml`. A single deployable
JAR + a Full JDK 8 is the on-prem artifact.

## 4. Guard rails to verify before shipping

- **JavaFX-leak gate** (Principle I) — must return nothing:
  ```bash
  grep -rn "javafx" src/service src/dao src/domain src/util
  ```
- **Prepared statements only** (Principle VII) — no string-concatenated SQL in `src/dao`.
- **Billing suite** (Principle III) — `test/service/BillingServiceTest` implements TDD §8.3 and
  must pass.

## 5. Acceptance walkthroughs (map to spec User Stories & Success Criteria)

Run in the recommended build order; each is independently testable.

1. **Auth & RBAC (US2, SC-006)** — log in as the seeded Administrator → create a Cashier and a
   Manager → log in as each. Verify: invalid/inactive login is refused with a generic message
   and no session; a Cashier cannot reach Menu prices, Inventory, or Reports **by any path**
   (business-layer refusal, not just hidden controls); the attempt is recorded.
2. **Menu & Tables (US3)** — as Manager create a category + an Available item at a valid price →
   it is immediately orderable; mark it Unavailable → it leaves order-entry but keeps history;
   define a uniquely-labelled table (capacity ≥ 1).
3. **POS / Billing (US1, SC-002, SC-004, SC-005)** — as Cashier open a dine-in order on a Free
   table (→ Occupied) → add items → apply a 10% discount on a 100.00 subtotal → verify discount
   10.00, base 90.00, tax 9.00 (10% rate), **total 99.00** → finalise with Cash → payment +
   status + figures commit atomically, table → Needs Cleaning, receipt reproduces the figures.
   Then edit the item price / tax rate → the finalised bill and its reprint are **unchanged**.
   Force a mid-finalise failure → order stays Open, no payment written (SC-005).
4. **Inventory & Purchasing (US4, SC-008)** — create a stock item with a reorder level + an
   active supplier → raise a PO (status Ordered, stock unchanged) → receive 10 units → on-hand
   rises by exactly 10 atomically, PO → Received/Partially Received; an item at/below reorder is
   flagged; receiving above the level clears it.
5. **Staff & Reservations (US5, SC-008)** — create/deactivate a staff record (history kept);
   book a future reservation on a free slot (table → Reserved); attempt an overlapping booking
   on the same table → rejected; seat/complete/cancel through the lifecycle.
6. **Reporting (US6, SC-007)** — with finalised orders present, as Manager generate a sales
   report whose totals reconcile to those orders (from stored figures, not current prices), an
   inventory report flagging low stock, and a staff-activity report per cashier → export any of
   them (header records title/user/timestamp/parameters). As Cashier, any report is denied.

## Done when

All six walkthroughs pass, the three guard-rail checks are clean, and the billing suite is
green — i.e. the Success Criteria SC-001…SC-010 hold (SC-010: reference/system data is
Administrator-only, audited, and never alters a finalised bill — FR-31).
