# Golden Fork RMS

A single-location **Restaurant Management System** — a JavaFX desktop application (Phase 1)
designed so its business core can be reused unchanged behind a web front end (Phase 2).

The system automates the core day-to-day operations of a restaurant: taking orders, producing
accurate bills and receipts, maintaining the menu and dining tables, tracking inventory and
supplier purchases, recording reservations, administering staff and user accounts, and
producing management reports.

## Status

Specification & design phase. This repository currently holds the requirements, design, and
Spec Kit artifacts; application code follows.

## Scope

Six functional modules covering functional requirements **FR-01 … FR-30**:

1. **Authentication & Administration** (FR-01–FR-04)
2. **Menu & Table Management** (FR-05–FR-09)
3. **Orders & Billing / POS** (FR-10–FR-17)
4. **Inventory & Suppliers** (FR-18–FR-22)
5. **Staff & Reservations** (FR-23–FR-26)
6. **Reporting** (FR-27–FR-30)

Three roles — **Administrator, Manager, Cashier** — with access enforced by a role–permission
matrix in the business layer.

## Planned technology (Phase 1)

- Java **JDK 8**
- JavaFX (bundled `jfxrt.jar`) with **FXML**, one controller per screen
- **MySQL** via JDBC
- Eclipse project
- Strict layered MVC: **View → Controller → Service → DAO → Database** (one direction); the
  service/DAO/domain/util core carries no UI-framework dependency so it is reusable in Phase 2.

## Repository layout

| Path | Contents |
|---|---|
| `BRD.md` | Business Requirements Document |
| `FRD.md` | Functional Requirements Document (FR-01…FR-30, BR-01…BR-30, NFR-01…NFR-08) |
| `TDD.md` | Technical Design Document (architecture, ERD, schema) |
| `RMS_Business_Use_Cases.md` | Business use cases |
| `design/` | UI design specs, stylesheet, and screen mockups |
| `specs/` | Spec Kit feature specifications |
| `.specify/` | Project constitution, templates, and Spec Kit governance |

## Documentation

Source `.md` documents have matching `.pdf` exports for sharing. The project constitution
(engineering principles and constraints) lives at `.specify/memory/constitution.md`.
