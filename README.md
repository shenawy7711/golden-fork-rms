# Golden Fork RMS

A single-location **Restaurant Management System** — a JavaFX desktop application (Phase 1)
designed so its business core can be reused unchanged behind a web front end (Phase 2).

The system automates the core day-to-day operations of a restaurant: taking orders, producing
accurate bills and receipts, maintaining the menu and dining tables, tracking inventory and
supplier purchases, recording reservations, administering staff and user accounts, and
producing management reports.

## Status

**Phase 1 implementation complete.** All six user stories are built on a strict layered core
(View → Controller → Service → DAO → MySQL) with the money engine, RBAC, and transactional
integrity enforced in the business layer. See `specs/001-golden-fork-rms/tasks.md` for the
task-by-task record.

## Build & run

Full instructions, database setup, and the acceptance walkthroughs live in
[`specs/001-golden-fork-rms/quickstart.md`](specs/001-golden-fork-rms/quickstart.md). In short:

```bash
# 1. Prerequisites: Full JDK 8 (with JavaFX), Maven, MySQL 8 on localhost:3306.
# 2. Create the database (once):
mysql -u root -p rms < db/schema.sql
mysql -u root -p rms < db/seed.sql
#    Then apply any migrations in db/migrations/ in order (e.g. 001, 002).
# 3. Configure credentials: copy config/db.properties and set db.user / db.password.
# 4. Build and test:
mvn test
# 5. Run the desktop app:
mvn exec:java
```

Default first login is `admin` / `admin123` (change it after first sign-in). The JavaFX-free
service/DAO/domain/util core is exercised by the JUnit suite; the constitution gates
(`grep -rn "import javafx" src/service src/dao src/domain src/util` returns nothing;
DAOs use prepared statements only) hold.

## Scope

Six functional modules covering functional requirements **FR-01 … FR-31**:

1. **Authentication & Administration** (FR-01–FR-04, FR-31)
2. **Menu & Table Management** (FR-05–FR-09)
3. **Orders & Billing / POS** (FR-10–FR-17)
4. **Inventory & Suppliers** (FR-18–FR-22)
5. **Staff & Reservations** (FR-23–FR-26)
6. **Reporting** (FR-27–FR-30)

Three roles — **Administrator, Manager, Cashier** — with access enforced by a role–permission
matrix in the business layer.

## Planned technology (Phase 1)

- **Full JDK 8** with bundled JavaFX (Oracle JDK 8 / Azul Zulu FX 8 / BellSoft Liberica Full 8 —
  plain OpenJDK 8 omits JavaFX)
- JavaFX (bundled `jfxrt.jar`) with **FXML**, one controller per screen
- **MySQL** via JDBC (database `rms`)
- **Maven** build (`pom.xml`); editor-agnostic — Cursor / VS Code (Extension Pack for Java) or Eclipse
- Strict layered MVC: **View → Controller → Service → DAO → Database** (one direction); the
  service/DAO/domain/util core carries no UI-framework dependency so it is reusable in Phase 2.

## Repository layout

| Path | Contents |
|---|---|
| `BRD.md` | Business Requirements Document |
| `FRD.md` | Functional Requirements Document (FR-01…FR-31, BR-01…BR-31, NFR-01…NFR-08) |
| `TDD.md` | Technical Design Document (architecture, ERD, schema) |
| `RMS_Business_Use_Cases.md` | Business use cases |
| `design/` | UI design specs, stylesheet, and screen mockups |
| `specs/` | Spec Kit feature specifications |
| `.specify/` | Project constitution, templates, and Spec Kit governance |

## Documentation

Source `.md` documents have matching `.pdf` exports for sharing. The project constitution
(engineering principles and constraints) lives at `.specify/memory/constitution.md`.
