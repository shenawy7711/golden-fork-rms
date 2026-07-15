# Business Use Cases Document (BUCD)
## Restaurant Management System (RMS)

| Field | Value | Field | Value |
|---|---|---|---|
| **Document** | Business Use Cases Document (BUCD) | **Version** | 1.0 (Draft for review) |
| **Date** | 14 July 2026 | **Classification** | Confidential |
| **Prepared by** | Project Team | **Traces to** | BRD v1.0 · FRD v1.0 · TDD v1.0 |
| **Phase** | Phase 1 (Desktop) → Phase 2 (Web) | **Status** | Draft for review |

> A comprehensive catalogue of business use cases for the RMS, derived from the approved BRD v1.0, FRD v1.0 and TDD v1.0. It describes **who** uses the system, **what** they are trying to achieve, and the **step-by-step interactions** — main flows, alternate flows and exceptions — that deliver each business outcome.

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Actors](#2-actors)
3. [Use Case Catalogue & Map](#3-use-case-catalogue--map)
4. [Business Workflow Use Cases (End-to-End)](#4-business-workflow-use-cases-end-to-end)
5. [Function-Level Use Cases (Detailed Catalogue)](#5-function-level-use-cases-detailed-catalogue)
6. [Use Case → Requirement Traceability](#6-use-case--requirement-traceability)
7. [Appendix A — Business Rules Referenced](#appendix-a--business-rules-referenced)
8. [Appendix B — Status & Enumeration Reference](#appendix-b--status--enumeration-reference)

---

## 1. Introduction

### 1.1 Purpose

This Business Use Cases Document (BUCD) presents the Restaurant Management System (RMS) from the point of view of the people who use it and the goals they pursue. Where the Business Requirements Document (BRD) explains *what the business needs and why*, the Functional Requirements Document (FRD) specifies *how the system must behave*, and the Technical Design Document (TDD) defines *how it is engineered*, this document ties those together into concrete, testable **use cases**: named units of interaction between an actor and the system that produce a result of value.

It is written to be usable by business stakeholders (owner, manager) to confirm the system supports real operations, by QA to derive test scenarios, and by the development team as a behavioural reference.

### 1.2 Scope

The document covers the six functional modules of the RMS — Authentication & Administration, Menu & Table Management, Orders & Billing (POS), Inventory & Suppliers, Staff & Reservations, and Reporting — across both delivery phases (on-premise desktop, then web) against one shared MySQL database.

**Out of scope** (per BRD §4.2), and therefore not covered by any use case here: online customer-facing ordering and delivery, third-party payment gateway / card processing, kitchen display screens, loyalty / rewards programs, accounting or payroll integration, multi-branch consolidation, and a native mobile app.

### 1.3 How to Read a Use Case

Two levels of use case are provided, and they complement each other:

- **Business Workflow Use Cases (BUC-nn)** in §4 describe complete, end-to-end operational journeys (for example, serving a table from order entry to paid receipt). They show how several functions combine to deliver a business outcome.
- **Function-Level Use Cases (UC-nn)** in §5 describe a single system function in full detail (for example, "Apply Discount"). There is one function-level use case for each functional requirement FR-01…FR-31, preserving end-to-end traceability.

Each **full detailed** use case is specified with the fields below. The keyword *shall* denotes mandatory behaviour.

| Field | Meaning |
|---|---|
| **Use Case ID / Name** | Unique identifier and short title. |
| **Module** | Owning functional module. |
| **Priority** | High = must-have for launch; Medium = important; Low = desirable. |
| **Primary Actor(s)** | The role(s) permitted to initiate the use case. |
| **Secondary Actor(s)** | Supporting roles or the system acting on their behalf. |
| **Goal / Description** | The value the actor is trying to obtain. |
| **Trigger** | The event that starts the use case. |
| **Pre-conditions** | What must already be true before it can begin. |
| **Main Flow (Basic Course)** | The normal, successful step-by-step interaction. |
| **Alternate Flows** | Valid variations that still succeed. |
| **Exception Flows** | Error handling and failure paths. |
| **Post-conditions** | The resulting system state on success. |
| **Business Rules** | The cross-cutting rules (BR-nn) enforced. |
| **Acceptance Criteria** | Objective, testable pass conditions. |
| **Traceability** | Business Objective(s), FR(s) and BR(s) satisfied. |

### 1.4 Definitions & Acronyms

| Term | Meaning |
|---|---|
| RMS | Restaurant Management System. |
| POS | Point of Sale — the ordering and billing module. |
| BUC-nn | Business (workflow) Use Case identifier defined in §4. |
| UC-nn | Function-level Use Case identifier defined in §5. |
| FR-nn / NFR-nn | Functional / Non-Functional Requirement (from BRD/FRD). |
| BR-nn | Business Rule (from FRD §6). |
| BO-n | Business Objective (from BRD §3). |
| RBAC | Role-Based Access Control. |
| Price snapshot | Unit price copied onto an order line at order time, so later menu edits never change a finalised bill (BR-15). |
| Reorder level | Stock threshold at or below which an item is flagged for re-purchase (BR-25). |

### 1.5 Business Objectives Served

Every use case ultimately serves one or more of the BRD business objectives, restated here for reference:

- **BO-1 — Speed up service.** Faster order-to-bill than a manual paper process.
- **BO-2 — Reduce billing errors.** Automatic, consistent calculation of totals, discount, tax.
- **BO-3 — Give management visibility.** On-demand sales, inventory and staff reports.
- **BO-4 — Control stock and cost.** Track stock against sales; catch shortages early.
- **BO-5 — Centralise data.** One consistent database for all operational data.
- **BO-6 — Prepare for growth.** A clear, low-cost path from desktop to web/multi-location.

---

## 2. Actors

Actors are the roles that interact with, or are represented in, the RMS. Human actors authenticate and operate the system; external actors are represented as data only and never log in.

### 2.1 Human (Primary) Actors

| Actor | Description | Core responsibilities in the system |
|---|---|---|
| **Administrator** | Full control of the system; typically the owner or an IT-responsible person. Implicitly holds all Manager and Cashier permissions. | User accounts & roles, reference/system data (e.g. tax rate), every module and report. |
| **Manager** | Operational control of the restaurant. | Menu & prices, tables, inventory & suppliers, purchase orders, staff records, reservations, and all reports. Cannot manage system-level user accounts unless also an Administrator. |
| **Cashier** | Front-line operator focused on speed and simplicity. | Create/manage orders, produce bills & receipts, record payments, view menu & table status, record reservations. Cannot change prices, manage stock, or view management reports. |

### 2.2 System Actor

| Actor | Description |
|---|---|
| **RMS (System)** | Acts autonomously to perform automatic calculations and state changes — subtotal, tax and total computation, table-status transitions, low-stock flagging, double-booking prevention, and transaction integrity. Appears as a primary or secondary actor in several use cases. |

### 2.3 External (Data-Only) Actors

| Actor | Description |
|---|---|
| **Supplier** | Provides stock that is recorded through purchase orders. Does not use the system. |
| **Customer** | Experiences faster service and accurate bills; may have a reservation recorded. Does not log in. |

### 2.4 Role–Permission Matrix (authoritative)

Access to every use case is governed by role (RBAC), enforced in the business layer (FR-02, BR-03). A dash means denied. Permissions are cumulative — an Administrator implicitly holds all Manager and Cashier permissions.

| Function area | Administrator | Manager | Cashier |
|---|:---:|:---:|:---:|
| Log in / log out | ✔ | ✔ | ✔ |
| Manage user accounts & roles | ✔ | — | — |
| Configure reference/system data (tax rate, etc.) | ✔ | — | — |
| Manage menu categories & items / prices | ✔ | ✔ | — |
| Define tables / capacity | ✔ | ✔ | — |
| View & update table status | ✔ | ✔ | ✔ |
| Create & finalise orders / bills / receipts | ✔ | ✔ | ✔ |
| Manage stock items, suppliers, purchase orders | ✔ | ✔ | — |
| Manage staff records | ✔ | ✔ | — |
| Create / manage reservations | ✔ | ✔ | ✔ |
| Generate & export reports | ✔ | ✔ | — |

---

## 3. Use Case Catalogue & Map

### 3.1 Business Workflow Use Cases (§4)

These are the end-to-end operational journeys. Each is built from several function-level use cases.

| ID | Workflow Use Case | Primary Actor | Module(s) | Serves |
|---|---|---|---|---|
| BUC-1 | Order-to-Payment (Dine-in) | Cashier | POS, Tables | BO-1, BO-2 |
| BUC-2 | Order-to-Payment (Takeaway) | Cashier | POS | BO-1, BO-2 |
| BUC-3 | Menu Maintenance | Manager | Menu | BO-1, BO-5 |
| BUC-4 | Dining-Room / Table Setup & Turnover | Manager, Cashier | Tables | BO-1, BO-5 |
| BUC-5 | Inventory & Purchasing (Reorder to Receipt) | Manager | Inventory, Suppliers, Purchasing | BO-4, BO-5 |
| BUC-6 | Reservation Management | Cashier, Manager | Reservations, Tables | BO-1, BO-5 |
| BUC-7 | Management Reporting & Export | Manager, Administrator | Reporting | BO-3, BO-4 |
| BUC-8 | User & Access Administration | Administrator | Admin | BO-5 |
| BUC-9 | Staff Roster Management | Manager | Staff | BO-3, BO-5 |
| BUC-10 | System Setup & Reference-Data Configuration | Administrator | Admin | BO-2, BO-5 |

### 3.2 Function-Level Use Cases (§5)

One use case per functional requirement, grouped by module. Each carries the same identifier suffix as its FR for traceability.

| Module | Use Cases (FR) |
|---|---|
| Authentication & Administration | UC-01 Login (FR-01), UC-02 Role-Based Access Control (FR-02), UC-03 Manage User Accounts (FR-03), UC-04 Logout & Session End (FR-04), UC-31 Configure Reference/System Data (FR-31) |
| Menu & Table Management | UC-05 Manage Menu Categories (FR-05), UC-06 Manage Menu Items (FR-06), UC-07 Toggle Item Availability (FR-07), UC-08 Define Tables (FR-08), UC-09 Display & Update Table Status (FR-09) |
| Orders & Billing (POS) | UC-10 Open Order (FR-10), UC-11 Add/Remove Order Items (FR-11), UC-12 Automatic Subtotal (FR-12), UC-13 Apply Discount (FR-13), UC-14 Automatic Tax & Total (FR-14), UC-15 Record Payment & Finalise (FR-15), UC-16 Generate Receipt (FR-16), UC-17 Release Table on Close (FR-17) |
| Inventory & Suppliers | UC-18 Manage Stock Items (FR-18), UC-19 Register Suppliers (FR-19), UC-20 Create Purchase Order (FR-20), UC-21 Receive Delivery (FR-21), UC-22 Flag Low Stock (FR-22) |
| Staff & Reservations | UC-23 Manage Staff Records (FR-23), UC-24 Create Reservation (FR-24), UC-25 Reservation Lifecycle (FR-25), UC-26 Prevent Double-Booking (FR-26) |
| Reporting | UC-27 Sales Report (FR-27), UC-28 Inventory Report (FR-28), UC-29 Staff Activity Report (FR-29), UC-30 Export Reports (FR-30) |

### 3.3 Use Case Map (Workflow → Functions)

| Workflow | Composed of function-level use cases |
|---|---|
| BUC-1 Order-to-Payment (Dine-in) | UC-10 → UC-11 → UC-12 → UC-13 → UC-14 → UC-15 → UC-16 → UC-17 (with UC-09) |
| BUC-2 Order-to-Payment (Takeaway) | UC-10 → UC-11 → UC-12 → UC-13 → UC-14 → UC-15 → UC-16 |
| BUC-3 Menu Maintenance | UC-05 → UC-06 → UC-07 |
| BUC-4 Table Setup & Turnover | UC-08 → UC-09 (← UC-10, UC-17) |
| BUC-5 Inventory & Purchasing | UC-19 → UC-18 → UC-22 → UC-20 → UC-21 → UC-22 |
| BUC-6 Reservation Management | UC-24 → UC-26 → UC-09 → UC-25 |
| BUC-7 Reporting & Export | UC-27 / UC-28 / UC-29 → UC-30 |
| BUC-8 User & Access Administration | UC-03 (with UC-01, UC-02, UC-04) |
| BUC-9 Staff Roster Management | UC-23 |
| BUC-10 System Setup & Reference Data | UC-31 (+ UC-03) — reference-data configuration (feeds UC-14, UC-15) |

---

## 4. Business Workflow Use Cases (End-to-End)

These use cases describe complete operational journeys. Steps in `[brackets]` reference the function-level use cases and FRs they exercise.

### BUC-1 — Order-to-Payment (Dine-in)

| Field | Detail |
|---|---|
| **Module** | Orders & Billing (POS), Tables |
| **Priority** | High |
| **Primary Actor** | Cashier |
| **Secondary Actors** | RMS (System) — calculation, table state, atomic finalisation; Customer (data) |
| **Goal / Description** | Serve a seated party from order entry to a paid, accurate receipt, releasing the table for the next party. This is the central revenue-producing workflow of the restaurant. |
| **Trigger** | A party is seated and ready to order. |
| **Pre-conditions** | Cashier is authenticated with a valid session (UC-01); at least one table is `Free` (or `Reserved` for this party); menu items and the tax rate are configured. |

**Main Flow (Basic Course):**

1. Cashier opens a new dine-in order and selects the table; the system sets the table `Occupied`. `[UC-10 / FR-10][UC-09 / FR-09]`
2. Cashier adds available menu items with quantities; the system snapshots each unit price and updates the subtotal live. `[UC-11 / FR-11][UC-12 / FR-12]`
3. Cashier may add or remove lines as the order evolves while it remains `Open`. `[UC-11 / FR-11]`
4. Customer requests the bill; the cashier optionally applies a discount (percentage or fixed). `[UC-13 / FR-13]`
5. The system computes tax on the discounted base and the grand total. `[UC-14 / FR-14]`
6. Cashier records the payment method (and cash tendered if applicable); the system finalises the order in one atomic transaction, storing subtotal, discount, tax rate, tax and total, and recording the payment. `[UC-15 / FR-15]`
7. The system generates a receipt for printing or export. `[UC-16 / FR-16]`
8. The system sets the table to `Needs Cleaning`; floor staff mark it `Free` once cleaned. `[UC-17 / FR-17][UC-09 / FR-09]`

**Alternate Flows:**

- **A1 — Seat an existing reservation.** The party holds a reservation; the table is `Reserved`. Seating the reservation (UC-25) sets it `Occupied` and the order opens on that table.
- **A2 — Split into multiple items / re-order rounds.** Additional items are added across the visit; each addition re-runs UC-11/UC-12 while the order stays `Open`.
- **A3 — No discount.** Step 4 is skipped; the pre-tax base equals the subtotal.

**Exception Flows:**

- **E1 — Item unavailable.** An item marked `Unavailable` cannot be added at step 2; the system blocks it and the cashier chooses another item. `[FR-07/FR-11]`
- **E2 — Invalid discount.** A percentage > 100 or fixed amount > subtotal is rejected at step 4 with a correction prompt. `[FR-13]`
- **E3 — Empty order.** An order with zero items cannot be finalised at step 6. `[FR-15]`
- **E4 — Finalisation failure.** If the finalisation transaction fails, it rolls back entirely: the order stays `Open`, no payment is written, and the table stays `Occupied`. `[FR-15, NFR-03]`

**Post-conditions:** The order is `Paid/Closed` with immutable stored figures; a payment record exists; a receipt is available; the table is `Needs Cleaning` → `Free`.

**Business Rules:** BR-13, BR-14, BR-15, BR-16, BR-17, BR-18, BR-19, BR-20, BR-12.

**Acceptance Criteria:**

- A dine-in order can be opened only on a table without an active order, and that table becomes `Occupied`.
- Subtotal, discount, tax and total match the worked examples (e.g. subtotal 100.00, 10% discount → 90.00 base, 10% tax → 9.00 tax → 99.00 total).
- Finalising with a valid payment method closes the order and locks its figures; a failed finalisation leaves the order fully `Open`.
- Closing the dine-in order sets its table to `Needs Cleaning` without manual action.

**Traceability:** BO-1, BO-2; FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17.

---

### BUC-2 — Order-to-Payment (Takeaway)

| Field | Detail |
|---|---|
| **Module** | Orders & Billing (POS) |
| **Priority** | High |
| **Primary Actor** | Cashier |
| **Secondary Actors** | RMS (System); Customer (data) |
| **Goal / Description** | Take and bill a takeaway order accurately, without any table involvement. |
| **Trigger** | A customer places a takeaway order at the counter. |
| **Pre-conditions** | Cashier is authenticated (UC-01); menu and tax rate configured. |

**Main Flow:**

1. Cashier opens a new order with type `Takeaway` (no table selected). `[UC-10 / FR-10]`
2. Cashier adds available items and quantities; subtotal updates live. `[UC-11 / FR-11][UC-12 / FR-12]`
3. Cashier optionally applies a discount. `[UC-13 / FR-13]`
4. The system computes tax and total. `[UC-14 / FR-14]`
5. Cashier records payment and finalises atomically. `[UC-15 / FR-15]`
6. The system generates the receipt. `[UC-16 / FR-16]`

**Alternate/Exception Flows:** Same item-availability, discount and finalisation exceptions as BUC-1 (E1–E4). No table steps and no `Release Table` step apply.

**Post-conditions:** Order `Paid/Closed`; payment recorded; receipt available; no table state changes.

**Business Rules:** BR-13…BR-20.

**Acceptance Criteria:** A takeaway order opens with no table; the order-to-receipt cycle completes with correct figures and produces a receipt marked "Takeaway".

**Traceability:** BO-1, BO-2; FR-10…FR-16.

---

### BUC-3 — Menu Maintenance

| Field | Detail |
|---|---|
| **Module** | Menu |
| **Priority** | High |
| **Primary Actor** | Manager (or Administrator) |
| **Secondary Actors** | RMS (System) |
| **Goal / Description** | Keep the menu, its structure, prices and availability current so ordering screens always reflect what can be sold and at what price. |
| **Trigger** | A menu change is needed (new dish, price change, sold-out item, seasonal change). |
| **Pre-conditions** | Manager is authenticated (UC-01) with menu permissions. |

**Main Flow:**

1. Manager opens Menu administration. `[UC-01 / FR-01]`
2. Manager creates or edits categories (e.g. Starters, Mains, Beverages). `[UC-05 / FR-05]`
3. Manager creates or edits items with name, category, price and availability; changes appear immediately in the ordering screens. `[UC-06 / FR-06]`
4. Manager marks sold-out or seasonal items `Unavailable` (retaining history) and re-enables them later. `[UC-07 / FR-07]`

**Alternate Flows:**

- **A1 — Price change.** Editing an item's price affects only *future* orders; finalised bills are never retro-priced (BR-09).
- **A2 — Discontinue an item with history.** Instead of hard-deleting an item referenced by past orders, it is marked `Unavailable`/discontinued to preserve order history (BR-10).

**Exception Flows:**

- **E1 — Duplicate category or item name.** Rejected (BR-08; item name unique within category).
- **E2 — Delete non-empty category.** Blocked with guidance to reassign or remove items first.
- **E3 — Invalid price.** Negative or non-numeric price is rejected.

**Post-conditions:** Category and item catalogue updated; ordering screens reflect current items, prices and availability.

**Business Rules:** BR-08, BR-09, BR-10.

**Acceptance Criteria:** A newly created available item with a valid price is orderable immediately at that price; editing a price does not alter any previously finalised bill; a non-empty category cannot be deleted.

**Traceability:** BO-1, BO-5; FR-05, FR-06, FR-07.

---

### BUC-4 — Dining-Room / Table Setup & Turnover

| Field | Detail |
|---|---|
| **Module** | Tables |
| **Priority** | High |
| **Primary Actor** | Manager (setup), Cashier / Floor staff (turnover) |
| **Secondary Actors** | RMS (System) — automatic status transitions |
| **Goal / Description** | Define the dining room's tables and keep each table's live status accurate so the floor view always reflects reality, supporting fast turnover. |
| **Trigger** | Initial configuration of the dining room, or an operational status change during service. |
| **Pre-conditions** | Manager is authenticated with table permissions (for setup). |

**Main Flow:**

1. Manager defines tables, each with a unique label and seating capacity. New tables default to `Free`. `[UC-08 / FR-08]`
2. During service the floor view shows each table colour-coded by status. `[UC-09 / FR-09]`
3. Opening a dine-in order sets a table `Occupied`; closing it sets `Needs Cleaning`; staff mark it `Free` once cleaned. `[UC-09 / FR-09][UC-17 / FR-17]`
4. A confirmed reservation sets/keeps a table `Reserved` until seated or cancelled. `[UC-24/UC-25]`

**Alternate Flows:**

- **A1 — Manual status change.** Staff manually move a table between valid statuses (e.g. `Needs Cleaning → Free`).

**Exception Flows:**

- **E1 — Duplicate table label.** Rejected (BR-11).
- **E2 — Delete an in-use table.** A table that is occupied or holds future reservations cannot be deleted until free and un-booked.
- **E3 — Illegal status transition.** Any transition outside the defined state model is rejected (BR-12).

**Post-conditions:** The table roster reflects definitions; the live floor view accurately shows each table's current status.

**Business Rules:** BR-11, BR-12.

**Acceptance Criteria:** A defined table appears for order and reservation selection with its capacity; table status visibly changes in response to order/reservation events; invalid transitions are prevented.

**Traceability:** BO-1, BO-5; FR-08, FR-09, FR-17.

---

### BUC-5 — Inventory & Purchasing (Reorder to Receipt)

| Field | Detail |
|---|---|
| **Module** | Inventory, Suppliers, Purchasing |
| **Priority** | High |
| **Primary Actor** | Manager (or Administrator) |
| **Secondary Actors** | RMS (System) — low-stock flagging, atomic stock updates; Supplier (data) |
| **Goal / Description** | Keep stock accurate and avoid shortages by registering suppliers and stock items, watching reorder levels, raising purchase orders, and recording deliveries so on-hand quantities stay correct. |
| **Trigger** | Initial inventory setup, or a stock item falling to/below its reorder level. |
| **Pre-conditions** | Manager is authenticated with inventory permissions. |

**Main Flow:**

1. Manager registers suppliers and stock items with units of measure and reorder levels. `[UC-19 / FR-19][UC-18 / FR-18]`
2. The system flags any item whose quantity on hand is at or below its reorder level. `[UC-22 / FR-22]`
3. Manager raises a purchase order (PO) to an active supplier, listing stock items and quantities. Creating the PO does not change stock on hand. `[UC-20 / FR-20]`
4. On delivery, the manager records received quantities against the PO; the system increases stock on hand in one atomic transaction and updates the PO to `Received` or `Partially Received`. `[UC-21 / FR-21]`
5. The system recomputes low-stock flags; items back above their reorder level clear the flag. `[UC-22 / FR-22]`

**Alternate Flows:**

- **A1 — Partial delivery.** Only some ordered quantity arrives; received ≤ ordered is accepted and the PO becomes `Partially Received`.
- **A2 — Logged adjustment.** On-hand is corrected via an explicit, logged adjustment movement rather than direct editing.

**Exception Flows:**

- **E1 — Duplicate stock/supplier name.** Rejected (BR-21, BR-22).
- **E2 — PO with no valid lines / non-positive quantity.** Rejected (BR-23).
- **E3 — Received greater than ordered.** Rejected.
- **E4 — Receipt transaction failure.** Rolls back; stock and PO status unchanged (NFR-03).
- **E5 — Delete item/supplier with history.** Converted to deactivate to preserve history (BR-05).

**Post-conditions:** Stock on hand reflects deliveries; PO statuses are current; low-stock flags are accurate; every on-hand change is recorded as a movement.

**Business Rules:** BR-21, BR-22, BR-23, BR-24, BR-25, BR-05.

**Acceptance Criteria:** Creating a PO does not alter stock; receiving 10 units raises on-hand by exactly 10; a failed receipt leaves stock and PO status unchanged; an item at/below its reorder level is flagged and the flag clears once restocked above it.

**Traceability:** BO-4, BO-5; FR-18, FR-19, FR-20, FR-21, FR-22.

---

### BUC-6 — Reservation Management

| Field | Detail |
|---|---|
| **Module** | Reservations, Tables |
| **Priority** | High |
| **Primary Actor** | Cashier or Manager |
| **Secondary Actors** | RMS (System) — overlap prevention, table state; Customer (data) |
| **Goal / Description** | Record and manage customer table bookings across their lifecycle without double-booking, keeping the table's reserved hold accurate. |
| **Trigger** | A customer requests a booking, or an existing booking changes state (arrives, dines, cancels). |
| **Pre-conditions** | User is authenticated with reservation permissions; at least one table is defined. |

**Main Flow:**

1. Cashier/Manager creates a reservation with customer name, contact, future date/time, party size and table. `[UC-24 / FR-24]`
2. The system rejects the booking if it overlaps an existing active reservation on the same table. `[UC-26 / FR-26]`
3. On success the table shows `Reserved` for the service window and the reservation appears in the day's list with status `Booked`. `[UC-09 / FR-09]`
4. When the guest arrives the reservation is `Seated` (table `Occupied`, an order may open); when the visit ends it is `Completed`; a booking may instead be `Cancelled` or marked `No-Show`. `[UC-25 / FR-25]`

**Alternate Flows:**

- **A1 — Party exceeds capacity.** The system warns and may require an override or a larger table (BR-28).
- **A2 — Reschedule.** Changing time/table re-runs the overlap check (UC-26).

**Exception Flows:**

- **E1 — Past date/time.** Rejected (must be today or future).
- **E2 — Overlap detected.** Save blocked with a clear conflict message; the system may suggest alternative tables/times (BR-27).

**Post-conditions:** Only non-conflicting reservations are stored; table status and reservation status reflect the current lifecycle stage; completing/cancelling frees the reserved hold.

**Business Rules:** BR-27, BR-28, BR-29, BR-12.

**Acceptance Criteria:** A valid future reservation on a free slot saves and marks the table `Reserved`; two overlapping reservations on the same table cannot both exist; seating frees the hold and marks the table `Occupied`; cancelling frees the slot for rebooking.

**Traceability:** BO-1, BO-5; FR-24, FR-25, FR-26, FR-09.

---

### BUC-7 — Management Reporting & Export

| Field | Detail |
|---|---|
| **Module** | Reporting |
| **Priority** | High |
| **Primary Actor** | Manager or Administrator |
| **Secondary Actors** | RMS (System) — aggregation |
| **Goal / Description** | Give management on-demand visibility of sales, inventory and staff activity, and produce shareable exports — replacing manual end-of-day tallies. |
| **Trigger** | Management needs a report (end of shift/day, stock review, staff review). |
| **Pre-conditions** | User is authenticated with reporting permissions (Manager/Administrator only — Cashiers are denied). |

**Main Flow:**

1. Manager selects a report — Sales, Inventory, or Staff Activity — and a date range and optional filters. `[UC-27 / FR-27][UC-28 / FR-28][UC-29 / FR-29]`
2. The system aggregates the underlying data (sales/staff reports use only *finalised* orders) and presents the results on screen.
3. Manager exports or prints the report; the export reproduces the on-screen report with title, generating user, timestamp and applied parameters in the header. `[UC-30 / FR-30]`

**Alternate Flows:**

- **A1 — Filtered sales report.** Manager filters by order type, payment method, cashier or category.
- **A2 — Below-reorder-only inventory view.** Manager filters the inventory report to items at/below reorder level to drive purchasing.

**Exception Flows:**

- **E1 — Invalid date range.** Start after end is rejected.
- **E2 — No data in range.** An empty report is shown with a clear note.
- **E3 — RBAC denial.** A Cashier cannot reach any report (FR-02).

**Post-conditions:** An on-screen report is displayed and an export/printout is produced that records its parameters.

**Business Rules:** BR-30, BR-25.

**Acceptance Criteria:** Sales totals reconcile to the sum of finalised orders in range; every stock item at/below its reorder level is listed as low; the exported document matches the displayed report and records its parameters.

**Traceability:** BO-3, BO-4; FR-27, FR-28, FR-29, FR-30.

---

### BUC-8 — User & Access Administration

| Field | Detail |
|---|---|
| **Module** | Admin |
| **Priority** | High |
| **Primary Actor** | Administrator |
| **Secondary Actors** | RMS (System) — RBAC enforcement |
| **Goal / Description** | Provision and govern who can use the system and what they can do, so access is correctly restricted by role and financial/personal data is protected. |
| **Trigger** | Onboarding, role change, or offboarding of a system user. |
| **Pre-conditions** | Administrator is authenticated (UC-01) with an active administrator account. |

**Main Flow:**

1. Administrator opens User Management. `[UC-03 / FR-03]`
2. Administrator creates an account with a unique username, full name, exactly one role, and an initial password meeting policy; the password is stored hashed. `[UC-03 / FR-03]`
3. The new user can log in immediately and sees only role-appropriate functions. `[UC-01 / FR-01][UC-02 / FR-02]`
4. Administrator edits, deactivates or (where no history exists) deletes accounts; role changes take effect on next login.
5. Users log out (or time out), ending their session. `[UC-04 / FR-04]`

**Alternate Flows:**

- **A1 — Deactivate instead of delete.** An account with dependent history is deactivated (login blocked, history retained) rather than deleted (BR-05).

**Exception Flows:**

- **E1 — Duplicate username.** Rejected (BR-04).
- **E2 — Delete blocked by history.** System offers deactivation instead.
- **E3 — Removing the last Administrator.** Refused with explanation (BR-06).

**Post-conditions:** The account list reflects changes; each user's permissions match the role matrix; at least one active Administrator always exists.

**Business Rules:** BR-02, BR-03, BR-04, BR-05, BR-06.

**Acceptance Criteria:** A new valid account can log in immediately; a deactivated account cannot log in but its past records remain intact; the system never allows zero active Administrators.

**Traceability:** BO-5; FR-01, FR-02, FR-03, FR-04.

---

### BUC-9 — Staff Roster Management

| Field | Detail |
|---|---|
| **Module** | Staff |
| **Priority** | Medium |
| **Primary Actor** | Manager (or Administrator) |
| **Secondary Actors** | RMS (System) |
| **Goal / Description** | Maintain employee records (distinct from login accounts) for roster and reporting purposes across hiring, role changes and offboarding. |
| **Trigger** | Hiring, a role change, or offboarding. |
| **Pre-conditions** | Manager is authenticated with staff permissions. |

**Main Flow:**

1. Manager creates a staff record with name, position/role, contact and status. `[UC-23 / FR-23]`
2. Manager edits records as roles or contacts change.
3. Manager deactivates a departing employee's record, retaining history.

**Alternate Flows:**

- **A1 — Link to a login account.** A staff record may optionally be linked to a user account (they are distinct entities).

**Exception Flows:**

- **E1 — Delete a record with history.** Converted to deactivate to preserve reporting integrity (BR-26).

**Post-conditions:** The roster reflects the change; inactive staff are excluded from active listings but retained for history.

**Business Rules:** BR-26, BR-05.

**Acceptance Criteria:** A new staff record appears in the roster; deactivation hides them from active lists while keeping history.

**Traceability:** BO-3, BO-5; FR-23.

---

### BUC-10 — System Setup & Reference-Data Configuration

| Field | Detail |
|---|---|
| **Module** | Admin |
| **Priority** | High |
| **Primary Actor** | Administrator |
| **Secondary Actors** | RMS (System) — consumes reference data in billing/validation |
| **Goal / Description** | Configure the reference/system data the rest of the system depends on — chiefly the tax rate and payment methods — so billing and validation are correct from day one. |
| **Trigger** | Initial system commissioning, or a change to a system-wide value (e.g. a new tax rate). |
| **Pre-conditions** | Administrator is authenticated; this configuration is exclusive to the Administrator role. |

**Main Flow:**

1. Administrator opens system configuration.
2. Administrator sets the tax rate, payment-method reference list, and other system constants (e.g. idle timeout, login attempt limit).
3. The system stores each value with the changing administrator and a timestamp for audit.
4. Billing (UC-14/UC-15) and validation subsequently use the configured values; the tax rate in force at finalisation is snapshotted onto each order.

**Alternate Flows:**

- **A1 — Change the tax rate later.** A new rate applies only to future finalisations; previously finalised bills keep their snapshotted rate (BR-09, BR-18).

**Exception Flows:**

- **E1 — Non-Administrator attempt.** Blocked by RBAC (FR-02).
- **E2 — Billing before configuration.** Reference data (tax rate, payment methods) must exist before orders can be finalised (dependency).

**Post-conditions:** Reference data is configured and available to billing and validation; changes are audited.

**Business Rules:** BR-09, BR-18, BR-03.

**Acceptance Criteria:** Changing the reference tax rate does not alter any previously finalised bill; only an Administrator can change reference data.

**Traceability:** BO-2, BO-5; FR-31 (reference-data configuration), FR-02, FR-14, FR-15.

---

## 5. Function-Level Use Cases (Detailed Catalogue)

One full detailed use case per functional requirement (FR-01…FR-31), grouped by module.

### 5.1 Authentication & Administration

#### UC-01 — User Login & Authentication

| Field | Detail |
|---|---|
| **Module** | Admin | **Priority** | High |
| **Primary Actor** | Administrator, Manager, Cashier (all roles) |
| **Secondary Actor** | RMS (System) — credential verification, session creation |
| **Goal / Description** | Authenticate a user with username and password before any other function is reachable, and start a role-bound session. |
| **Trigger** | User opens the application, or an unauthenticated action is attempted. |
| **Pre-conditions** | The user has an existing, *active* account; the application can reach the database. |

**Main Flow:**

1. The system presents the login screen.
2. User enters username (3–50 chars) and a masked password.
3. The system verifies the password against the stored salted one-way hash.
4. On success the system creates an authenticated session bound to the user's role and records a LOGIN event (feeds UC-29).
5. The user lands on the role-appropriate home screen; the available menu reflects their permissions (UC-02).

**Alternate Flows:**

- **A1 — Role-specific landing.** Administrator, Manager and Cashier are routed to different home screens per their permissions.

**Exception Flows:**

- **E1 — Unknown username or wrong password.** Generic message "Invalid username or password" (does not disclose which was wrong); no session created.
- **E2 — Deactivated account.** "This account is inactive. Contact an administrator."; login refused even with correct credentials.
- **E3 — Repeated failures.** After a configurable number of consecutive failures (default 5) login is temporarily throttled to deter brute force.
- **E4 — Database unreachable.** "Unable to sign in — service unavailable"; no session created.

**Post-conditions:** A valid session exists; the user is on their home screen; passwords never appear in plain text anywhere.

**Business Rules:** BR-01, BR-02.

**Acceptance Criteria:** Valid credentials for an active account authenticate and route to the home screen; any invalid credential denies access with the generic message and no session; passwords never appear readable in the database, logs or screens.

**Traceability:** BO-5; NFR-04; FR-01.

---

#### UC-02 — Role-Based Access Control

| Field | Detail |
|---|---|
| **Module** | Admin | **Priority** | High |
| **Primary Actor** | RMS (System) — enforced for every authenticated user |
| **Secondary Actor** | Administrator, Manager, Cashier |
| **Goal / Description** | Restrict every function to the roles permitted by the Role–Permission Matrix (§2.4), both in the UI and re-checked on execution (defence in depth). |
| **Trigger** | Any attempt to open a screen or invoke a function. |
| **Pre-conditions** | The caller has a valid authenticated session (UC-01). |

**Main Flow:**

1. The user invokes a function.
2. The business layer looks up the roles permitted for that function.
3. The system verifies the caller's role is permitted.
4. The permitted action proceeds; denied functions are hidden or disabled in the interface.

**Alternate Flows:**

- **A1 — Cumulative permissions.** An Administrator implicitly passes any check a Manager or Cashier would pass.

**Exception Flows:**

- **E1 — Unauthorised invocation.** The action is refused with "You do not have permission to perform this action" and the attempt is recorded — even if a UI control were bypassed.

**Post-conditions:** Permitted actions proceed; unauthorised actions are blocked and logged.

**Business Rules:** BR-03.

**Acceptance Criteria:** A Cashier cannot reach price-edit, stock or report functions by any path; a Manager can reach all operational functions but not user-account management (unless also Administrator); a role-forbidden call is rejected at the business layer even if the UI were bypassed.

**Traceability:** BO-5; NFR-04; FR-02.

---

#### UC-03 — Manage User Accounts & Roles

| Field | Detail |
|---|---|
| **Module** | Admin | **Priority** | High |
| **Primary Actor** | Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Create, edit, deactivate and delete user accounts and assign each exactly one role. |
| **Trigger** | Administrator opens User Management. |
| **Pre-conditions** | Administrator authenticated with an active administrator account. |

**Inputs:** Username (required, unique, 3–50 chars); Full name (required, 2–100); Role (required; Administrator/Manager/Cashier); Initial password (required on create; meets policy; stored hashed); Status (Active/Inactive, default Active).

**Main Flow:**

1. Administrator opens the account list.
2. Administrator creates an account with a unique username, name, role and initial password.
3. The system stores the account (password hashed) and it becomes usable on next login.
4. Administrator edits details, changes role, or deactivates an account.

**Alternate Flows:**

- **A1 — Deactivate.** Preserves the account and its historical links but blocks login.
- **A2 — Delete.** Permitted only when the account has no dependent historical records; otherwise the system requires deactivation instead (BR-05).
- **A3 — Role change.** Takes effect on the user's next login.

**Exception Flows:**

- **E1 — Duplicate username.** "Username already exists." (BR-04)
- **E2 — Delete blocked by history.** Offer to deactivate instead.
- **E3 — Removing/deactivating the last active Administrator.** Refused with explanation (BR-06).

**Post-conditions:** The account list reflects the change; affected permissions update per UC-02; at least one active Administrator remains.

**Business Rules:** BR-02, BR-04, BR-05, BR-06.

**Acceptance Criteria:** A new valid unique account can log in immediately; a deactivated account cannot log in but its past records remain intact; the system never allows zero active Administrators.

**Traceability:** BO-5; NFR-04; FR-03.

---

#### UC-04 — Logout & Session Termination

| Field | Detail |
|---|---|
| **Module** | Admin | **Priority** | High |
| **Primary Actor** | Administrator, Manager, Cashier (all roles) |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | End the user's session cleanly, returning to the login screen and clearing user context, without orphaning any open order. |
| **Trigger** | User selects Log Out, or the session times out. |
| **Pre-conditions** | The user has an active session. |

**Main Flow:**

1. User selects Log Out.
2. The system checks for any open, unsaved order.
3. The system invalidates the session, records a LOGOUT event (feeds UC-29), and returns to the login screen.

**Alternate Flows:**

- **A1 — Idle timeout.** An optional idle timeout (default 15 min) triggers automatic logout on an unattended terminal.

**Exception Flows:**

- **E1 — Open order at logout.** The system warns and requires the user to finalise, park, or discard the order first, so no partial financial data is orphaned (BR-07, NFR-03).

**Post-conditions:** No authenticated session remains; the login screen is shown; protected functions require re-authentication.

**Business Rules:** BR-07.

**Acceptance Criteria:** After logout, re-invoking a protected function requires re-authentication; logout with an open order prompts for resolution before ending the session.

**Traceability:** NFR-04; FR-04.

---

#### UC-31 — Configure Reference / System Data

| Field | Detail |
|---|---|
| **Module** | Admin | **Priority** | High |
| **Primary Actor** | Administrator (only) |
| **Secondary Actor** | RMS (System) — consumes reference data in billing/validation |
| **Goal / Description** | Configure the reference/system data the rest of the system depends on — the tax rate, payment-method list, and system constants (idle timeout, login-attempt limit, reservation slot, discount-approval threshold) — so billing and validation are correct from day one. |
| **Trigger** | Initial commissioning, or a change to a system-wide value (e.g. a new tax rate). |
| **Pre-conditions** | The caller is authenticated with an active Administrator account; this function is exclusive to the Administrator role. |

**Inputs:** Tax rate (required; 0 ≤ rate ≤ 1.0000, four decimals); Payment methods (unique names, 2–20 chars; activate/deactivate rather than hard-delete once used); Idle timeout minutes (integer ≥ 0, default 15); Login max attempts (integer ≥ 1, default 5); Reservation slot minutes (integer ≥ 1, default 90); Discount approval threshold (decimal ≥ 0).

**Main Flow:**

1. Administrator opens System Configuration.
2. Administrator sets/edits the tax rate, payment-method list, and other system constants.
3. The system validates each value and stores it with the changing administrator and a timestamp for audit.
4. Billing (UC-14/UC-15) and validation subsequently use the configured values; the tax rate in force at finalisation is snapshotted onto each order.

**Alternate Flows:**

- **A1 — Change the tax rate later.** A new rate applies only to future finalisations; previously finalised bills keep their snapshotted rate (BR-09, BR-18).
- **A2 — Retire a payment method.** A method referenced by past payments is deactivated (hidden from new payments) rather than deleted.

**Exception Flows:**

- **E1 — Non-Administrator attempt.** Blocked by RBAC in the business layer (FR-02), even if the UI were bypassed; the attempt is recorded.
- **E2 — Invalid value.** Tax rate outside 0–1, or a non-positive slot/attempt value, is rejected with a field-specific message.
- **E3 — Billing before configuration.** A tax rate and at least one active payment method must exist before any order can be finalised (dependency).

**Post-conditions:** Reference data is configured and available to billing and validation; every change is audited (who/when).

**Business Rules:** BR-31, BR-03, BR-09, BR-18.

**Acceptance Criteria:** Only an Administrator can view or change reference/system data; a role-forbidden attempt is rejected at the business layer; changing the tax rate does not alter any previously finalised bill; each change records the administrator and timestamp.

**Traceability:** BO-2, BO-5; NFR-04; FR-31.

---

### 5.2 Menu & Table Management

#### UC-05 — Manage Menu Categories

| Field | Detail |
|---|---|
| **Module** | Menu | **Priority** | High |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Create, edit and delete the categories used to organise menu items. |
| **Trigger** | Manager opens Menu → Categories. |
| **Pre-conditions** | Manager authenticated with menu permissions. |

**Inputs:** Category name (required, unique, 2–50 chars); Display order (optional integer).

**Main Flow:**

1. Manager opens the category list.
2. Manager creates or edits a category name and optional display order.
3. The system saves it; the change appears immediately in the ordering screens.

**Alternate Flows:**

- **A1 — Reorder categories.** Display order changes the on-screen sequence.

**Exception Flows:**

- **E1 — Duplicate name.** Rejected (BR-08).
- **E2 — Delete non-empty category.** Blocked; the system requires items to be reassigned or removed first (referential integrity).

**Post-conditions:** Updated category list; ordering screens reflect the change.

**Business Rules:** BR-08.

**Acceptance Criteria:** A new category is immediately selectable when adding an item and when ordering; a non-empty category cannot be deleted.

**Traceability:** BO-1, BO-5; FR-05.

---

#### UC-06 — Manage Menu Items

| Field | Detail |
|---|---|
| **Module** | Menu | **Priority** | High |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Create, edit and delete menu items, each with name, category, price and availability. |
| **Trigger** | Manager opens Menu → Items. |
| **Pre-conditions** | Manager authenticated; at least one category exists (UC-05). |

**Inputs:** Item name (required, 2–80 chars, unique within category); Category (required, existing); Price (required, decimal ≥ 0.00, two decimals); Availability (Available/Unavailable, default Available); Description (optional, ≤ 255 chars).

**Main Flow:**

1. Manager opens the item catalogue.
2. Manager creates or edits an item with name, category, price and availability.
3. The system validates and saves; changes appear immediately in the ordering screens.

**Alternate Flows:**

- **A1 — Price change.** Applies only to *future* orders; finalised bills are never retro-priced (BR-09).
- **A2 — Discontinue item with history.** An item referenced by any historical order is marked Unavailable/discontinued instead of hard-deleted (BR-10).

**Exception Flows:**

- **E1 — Negative/invalid price.** Rejected.
- **E2 — Delete item with order history.** Converted to "mark unavailable".
- **E3 — Duplicate name within category.** Rejected.

**Post-conditions:** Item catalogue updated; ordering screens show current items and prices.

**Business Rules:** BR-09, BR-10.

**Acceptance Criteria:** A new available item with a valid price is orderable immediately at that price; editing a price does not alter any previously finalised bill.

**Traceability:** BO-1, BO-2, BO-5; FR-06.

---

#### UC-07 — Toggle Menu Item Availability

| Field | Detail |
|---|---|
| **Module** | Menu | **Priority** | Medium |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Mark an item available or unavailable without deleting it, so sold-out or seasonal items are hidden from ordering yet retained for history and reactivation. |
| **Trigger** | An item runs out ("86" an item) or returns. |
| **Pre-conditions** | The item exists (UC-06). |

**Main Flow:**

1. Manager selects an item and toggles its availability.
2. The system updates availability; unavailable items are no longer selectable when building a new order but remain visible (greyed/flagged) in menu administration.

**Alternate Flows:**

- **A1 — Re-enable.** Marking an item Available again makes it immediately orderable.

**Exception Flows:**

- **E1 — Item already on an open order.** Marking unavailable does not affect items already on open orders.

**Post-conditions:** Ordering screens include only available items; the item can be re-enabled at any time; history persists.

**Business Rules:** BR-10.

**Acceptance Criteria:** An item toggled Unavailable disappears from the order-entry list but its record and history persist; re-enabling makes it immediately orderable.

**Traceability:** BO-1; FR-07.

---

#### UC-08 — Define Dining Tables

| Field | Detail |
|---|---|
| **Module** | Tables | **Priority** | High |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Define dining tables, each with a unique label and seating capacity. |
| **Trigger** | Manager configures the dining room. |
| **Pre-conditions** | Manager authenticated with table permissions. |

**Inputs:** Table number/label (required, unique, 1–10 chars); Seating capacity (required, integer ≥ 1).

**Main Flow:**

1. Manager opens the table configuration.
2. Manager defines a table with a unique label and capacity.
3. The system saves it; new tables default to status `Free` and become available for order and reservation selection.

**Alternate Flows:**

- **A1 — Edit capacity/label.** Updated where the table is free and un-booked.

**Exception Flows:**

- **E1 — Duplicate label.** Rejected (BR-11).
- **E2 — Delete an in-use table.** A table occupied or holding future reservations cannot be deleted until free and un-booked.

**Post-conditions:** The floor/table list reflects the definition; capacity supports reservation party-size checks (UC-24).

**Business Rules:** BR-11.

**Acceptance Criteria:** A defined table appears for order and reservation selection with its capacity; duplicate labels are rejected.

**Traceability:** BO-1, BO-5; FR-08.

---

#### UC-09 — Display & Update Table Status

| Field | Detail |
|---|---|
| **Module** | Tables | **Priority** | High |
| **Primary Actor** | All roles (view/update per matrix) |
| **Secondary Actor** | RMS (System) — automatic transitions |
| **Goal / Description** | Show and update each table's live status (`Free`, `Occupied`, `Reserved`, `Needs Cleaning`) so the floor view reflects reality. |
| **Trigger** | Order/reservation events, or a manual status change. |
| **Pre-conditions** | Tables are defined (UC-08). |

**Main Flow:**

1. The system displays a live, colour-coded floor view of all tables.
2. Opening a dine-in order on a Free/Reserved table sets it `Occupied`.
3. Closing a dine-in order sets the table `Needs Cleaning`; staff mark it `Free` when cleaned.
4. A confirmed reservation for the current service sets/keeps the table `Reserved` until seated or cancelled.

**Alternate Flows:**

- **A1 — Manual transition.** Staff manually move a table between valid statuses.

**Exception Flows:**

- **E1 — Illegal transition.** Any transition outside the defined state model is rejected (BR-12).

**Post-conditions:** The floor view shows current status per table, refreshed as events occur.

**Business Rules:** BR-12.

**Acceptance Criteria:** Table status visibly changes in response to order open/close and reservation events; an invalid manual transition is prevented.

**Traceability:** BO-1; FR-09.

---

### 5.3 Orders & Billing (POS)

> **Financial-integrity note:** All monetary calculation for UC-12 to UC-16 is centralised in one billing engine, computed in a single database transaction and rounded consistently (BR-13, BR-16). This directly mitigates the risk of incorrect financial calculations and satisfies BO-2.

#### UC-10 — Open a New Order

| Field | Detail |
|---|---|
| **Module** | POS | **Priority** | High |
| **Primary Actor** | Cashier, Manager, Administrator |
| **Secondary Actor** | RMS (System); Customer (data) |
| **Goal / Description** | Open a new order for a specific dine-in table or as takeaway. |
| **Trigger** | A customer is seated or places a takeaway order. |
| **Pre-conditions** | User authenticated (UC-01); for dine-in, a Free/Reserved (own) table exists. |

**Inputs:** Order type (required; Dine-in/Takeaway); Table (required if Dine-in — a Free or own-Reserved table; omitted for Takeaway).

**Main Flow:**

1. Cashier selects order type and, for dine-in, a table.
2. The system creates a new order with a unique order number, status `Open`, timestamp and creating user.
3. For dine-in, the table becomes `Occupied` (UC-09).

**Alternate Flows:**

- **A1 — Takeaway.** No table is selected; the order opens with no table.

**Exception Flows:**

- **E1 — Table already has an open order.** Prevented, with the option to open that existing order.

**Post-conditions:** An open, empty order exists and is ready to receive items; the table (if any) is `Occupied`.

**Business Rules:** BR-14.

**Acceptance Criteria:** A dine-in order can only be opened on a table without an active order, and that table becomes `Occupied`; a takeaway order opens with no table.

**Traceability:** BO-1; FR-10.

---

#### UC-11 — Add / Remove Order Items

| Field | Detail |
|---|---|
| **Module** | POS | **Priority** | High |
| **Primary Actor** | Cashier, Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Add available menu items with quantities to an open order and remove them before finalising, capturing the price at the time of ordering. |
| **Trigger** | Order is open; customer selects or changes items. |
| **Pre-conditions** | An `Open` order exists (UC-10). |

**Inputs:** Menu item (required; currently Available); Quantity (required, integer ≥ 1).

**Main Flow:**

1. Cashier selects an available item and a quantity.
2. The system adds an order line capturing item, quantity and the unit price *at the time of ordering* (price snapshot).
3. The order subtotal recomputes automatically (UC-12).
4. Cashier may remove or change lines while the order is `Open`.

**Alternate Flows:**

- **A1 — Repeat item.** Adding an item already on the order increments its quantity (or adds a new line, per configuration).

**Exception Flows:**

- **E1 — Quantity < 1 or non-integer.** Rejected.
- **E2 — Adding an unavailable item.** Blocked.
- **E3 — Editing a finalised order.** Blocked; a finalised order is immutable.

**Post-conditions:** The order reflects current lines and quantities with a live subtotal.

**Business Rules:** BR-15.

**Acceptance Criteria:** Adding/removing lines updates the subtotal immediately and correctly; only available items can be added; finalised orders cannot be edited.

**Traceability:** BO-1, BO-2; FR-11.

---

#### UC-12 — Automatic Subtotal Calculation

| Field | Detail |
|---|---|
| **Module** | POS | **Priority** | High |
| **Primary Actor** | RMS (System) |
| **Secondary Actor** | Cashier (observes result) |
| **Goal / Description** | Calculate the order subtotal automatically from item prices and quantities — no manual arithmetic. |
| **Trigger** | Any change to order lines/quantities. |
| **Pre-conditions** | An order with at least one line exists. |

**Main Flow:**

1. On any line change the system computes `line total = unit price (snapshot) × quantity`.
2. The system sums line totals into the subtotal, rounded to two decimals consistently, in the business layer.
3. The current subtotal is displayed on the order and carried into the bill.

**Exception Flows:**

- **E1 — No lines.** Subtotal is 0.00.

**Post-conditions:** A current subtotal is displayed and carried forward.

**Business Rules:** BR-13, BR-16.

**Acceptance Criteria:** For any set of lines the displayed subtotal equals Σ(price × qty) to two decimals and updates within the performance target on each edit.

**Traceability:** BO-2; FR-12.

---

#### UC-13 — Apply Discount

| Field | Detail |
|---|---|
| **Module** | POS | **Priority** | Medium |
| **Primary Actor** | Cashier, Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Apply a discount to an order as either a percentage of the subtotal or a fixed amount, before tax. |
| **Trigger** | A discount is agreed before finalising. |
| **Pre-conditions** | An open order with a subtotal exists. |

**Inputs:** Discount type (Percentage or Fixed); Discount value (percentage 0–100; or fixed 0 ≤ value ≤ subtotal).

**Main Flow:**

1. Cashier selects a discount type and value.
2. The system applies the discount to the subtotal *before* tax: `discountable = subtotal − discount`.
3. The bill shows subtotal, discount and the reduced pre-tax base.

**Alternate Flows:**

- **A1 — Manager authorisation.** Discounts above a configurable threshold may require Manager authorisation, recorded for audit.

**Exception Flows:**

- **E1 — Percentage > 100 or fixed > subtotal.** Rejected with a correction prompt; a discount can never make the total negative.

**Post-conditions:** The bill shows subtotal, discount and reduced pre-tax base.

**Business Rules:** BR-17.

**Acceptance Criteria:** A 10% discount on a 100.00 subtotal yields a 90.00 pre-tax base; no discount can produce a negative total.

**Traceability:** BO-2; FR-13.

---

#### UC-14 — Automatic Tax & Final Total

| Field | Detail |
|---|---|
| **Module** | POS | **Priority** | High |
| **Primary Actor** | RMS (System) |
| **Secondary Actor** | Cashier (observes result) |
| **Goal / Description** | Calculate tax and the final total automatically using the configured tax rate. |
| **Trigger** | The bill is requested / the order is being finalised. |
| **Pre-conditions** | A subtotal (and any discount) is established; the tax rate is configured (BUC-10). |

**Main Flow:**

1. The system computes `tax = round((subtotal − discount) × tax_rate)`.
2. The system computes `total = (subtotal − discount) + tax`, rounding to two decimals once, consistently.
3. The tax rate in force at finalisation is stored on the order for audit and accurate reprinting.

**Exception Flows:**

- **E1 — No tax rate configured.** Finalisation is dependent on configured reference data (BUC-10 dependency).

**Post-conditions:** The bill displays subtotal, discount, tax and grand total.

**Business Rules:** BR-16, BR-18.

**Acceptance Criteria:** With a 10% tax rate a 90.00 pre-tax base yields tax 9.00 and total 99.00; changing the reference tax rate does not alter any previously finalised bill.

**Traceability:** BO-2; FR-14.

---

#### UC-15 — Record Payment & Finalise Order

| Field | Detail |
|---|---|
| **Module** | POS | **Priority** | High |
| **Primary Actor** | Cashier, Manager, Administrator |
| **Secondary Actor** | RMS (System) — atomic transaction; Customer (data) |
| **Goal / Description** | Record the payment method and finalise (close) the order atomically. Payment is *recorded*, not processed (no gateway in scope). |
| **Trigger** | Customer pays. |
| **Pre-conditions** | An open order with at least one item and a computed total exists. |

**Inputs:** Payment method (required; Cash/Card/Other); Amount tendered (optional, for cash — the system computes change = tendered − total ≥ 0).

**Main Flow:**

1. Cashier selects the payment method (and enters cash tendered if applicable).
2. In one atomic transaction the system records the payment, sets the order `Paid/Closed`, and stores the final figures (subtotal, discount, tax rate, tax, total).
3. A receipt becomes available (UC-16) and a dine-in table is released (UC-17).

**Alternate Flows:**

- **A1 — Cash with change.** The system computes and displays change due.

**Exception Flows:**

- **E1 — No payment method.** Cannot finalise.
- **E2 — Cash tendered < total.** Rejected.
- **E3 — Zero-item order.** Cannot be finalised.
- **E4 — Transaction failure.** The order remains `Open` with no partial write (NFR-03).

**Post-conditions:** Order is `Paid/Closed` and payment recorded; figures are locked; the order is immutable.

**Business Rules:** BR-19.

**Acceptance Criteria:** Finalising with a valid method closes the order and locks its figures; a failed finalisation leaves the order fully `Open` with no orphaned payment.

**Traceability:** BO-1, BO-2; FR-15.

---

#### UC-16 — Generate Receipt

| Field | Detail |
|---|---|
| **Module** | POS | **Priority** | High |
| **Primary Actor** | Cashier, Manager, Administrator |
| **Secondary Actor** | RMS (System); Customer (recipient) |
| **Goal / Description** | Produce a printable/exportable receipt for a finalised order that reproduces the stored figures exactly. |
| **Trigger** | Order finalised (UC-15). |
| **Pre-conditions** | The order is `Paid/Closed`. |

**Main Flow:**

1. The system assembles the receipt from stored finalised figures.
2. The receipt includes: restaurant name/header, receipt/order number, date-time, table or "Takeaway", itemised lines (name, qty, unit price, line total), subtotal, discount, tax rate & tax, grand total, payment method (and change if cash), and the serving cashier.
3. The cashier prints or exports it (e.g. PDF).

**Alternate Flows:**

- **A1 — Reprint.** A finalised receipt may be re-printed/re-exported without altering data.

**Exception Flows:**

- **E1 — Order not finalised.** No receipt is generated for a non-finalised order.

**Post-conditions:** A receipt is displayed and can be printed or exported.

**Business Rules:** BR-20.

**Acceptance Criteria:** The receipt totals match the finalised bill exactly; re-printing later yields an identical receipt regardless of later menu/tax changes.

**Traceability:** BO-1, BO-2; FR-16.

---

#### UC-17 — Release Table on Order Close

| Field | Detail |
|---|---|
| **Module** | POS / Tables | **Priority** | High |
| **Primary Actor** | RMS (System) |
| **Secondary Actor** | Floor staff (mark cleaned) |
| **Goal / Description** | Release the table automatically when a dine-in order is closed, setting it to `Needs Cleaning` (then `Free` once cleaned). |
| **Trigger** | A dine-in order is closed (UC-15). |
| **Pre-conditions** | A dine-in order with an associated `Occupied` table is finalised. |

**Main Flow:**

1. On close of a dine-in order the system transitions the table `Occupied → Needs Cleaning` automatically.
2. Staff mark the table `Free` once cleaned; it becomes available for a new order.

**Alternate Flows:**

- **A1 — Takeaway.** Takeaway orders have no table and trigger no table change.

**Exception Flows:** None (system-driven, within the finalisation transaction).

**Post-conditions:** The table is freed for turnover; the floor view updates immediately.

**Business Rules:** BR-12.

**Acceptance Criteria:** Closing a dine-in order changes its table to `Needs Cleaning` without manual action; the table becomes available for a new order once marked `Free`.

**Traceability:** BO-1; FR-17.

---

### 5.4 Inventory & Suppliers

#### UC-18 — Manage Stock Items

| Field | Detail |
|---|---|
| **Module** | Inventory | **Priority** | High |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Create, edit and delete stock items, each with a unit of measure and reorder level; on-hand changes only via recorded movements. |
| **Trigger** | Manager sets up or maintains inventory. |
| **Pre-conditions** | Manager authenticated with inventory permissions. |

**Inputs:** Stock item name (required, unique, 2–80 chars); Unit of measure (required; e.g. kg, L, unit, bottle); Reorder level (required, ≥ 0 in the item's unit); Quantity on hand (system-maintained; opening value ≥ 0).

**Main Flow:**

1. Manager creates or edits a stock item with name, unit and reorder level.
2. The system saves it; the item participates in low-stock flagging (UC-22).

**Alternate Flows:**

- **A1 — Logged adjustment.** Quantity on hand changes only via recorded deliveries (UC-21) or explicit, logged adjustments — never edited directly to fabricate stock.

**Exception Flows:**

- **E1 — Duplicate name or negative reorder level.** Rejected (BR-21).
- **E2 — Delete item with PO history.** Converted to deactivate to preserve history (BR-05).

**Post-conditions:** The inventory catalogue reflects the change; reorder flags recompute.

**Business Rules:** BR-21, BR-05.

**Acceptance Criteria:** A new stock item appears in inventory with its unit and reorder level and participates in low-stock flagging.

**Traceability:** BO-4, BO-5; FR-18.

---

#### UC-19 — Register & Maintain Suppliers

| Field | Detail |
|---|---|
| **Module** | Suppliers | **Priority** | Medium |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System); Supplier (data) |
| **Goal / Description** | Register and maintain suppliers used on purchase orders. |
| **Trigger** | A new supplier relationship is established. |
| **Pre-conditions** | Manager authenticated with inventory/supplier permissions. |

**Inputs:** Supplier name (required, unique, 2–100 chars); Contact person / phone / email (optional; format-validated when present); Address (optional, ≤ 255 chars); Status (Active/Inactive).

**Main Flow:**

1. Manager creates or edits a supplier with name and optional contact details.
2. The system saves it; the supplier becomes selectable on new purchase orders (UC-20).

**Alternate Flows:**

- **A1 — Deactivate.** A supplier referenced by purchase orders is set Inactive rather than deleted.

**Exception Flows:**

- **E1 — Duplicate supplier name.** Rejected (BR-22).
- **E2 — Delete supplier with PO history.** Prevented; deactivate instead.

**Post-conditions:** The supplier is available (if active) for new purchase orders.

**Business Rules:** BR-22, BR-05.

**Acceptance Criteria:** A registered active supplier can be chosen on a new PO; deleting a supplier with PO history is prevented.

**Traceability:** BO-4, BO-5; FR-19.

---

#### UC-20 — Create Purchase Order

| Field | Detail |
|---|---|
| **Module** | Purchasing | **Priority** | High |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System); Supplier (data) |
| **Goal / Description** | Create a purchase order (PO) for a supplier, listing stock items and quantities to buy. |
| **Trigger** | Stock is low or restock is needed. |
| **Pre-conditions** | An active supplier and stock items exist (UC-19, UC-18). |

**Inputs:** Supplier (required, active); PO lines (≥ 1; each = stock item + quantity > 0, optional unit cost); Expected date (optional, today or later).

**Main Flow:**

1. Manager selects a supplier and adds one or more PO lines.
2. The system creates a PO with a unique number, status `Ordered`, creating user and timestamp.
3. Creating the PO does *not* change stock on hand.

**Alternate Flows:**

- **A1 — Cancel a PO.** An `Ordered` PO may be cancelled before receipt.

**Exception Flows:**

- **E1 — No lines / quantity ≤ 0.** Rejected (BR-23).

**Post-conditions:** An open PO awaiting delivery, visible in the purchasing list; stock unchanged.

**Business Rules:** BR-23.

**Acceptance Criteria:** A valid PO is saved with status `Ordered` and does not alter stock on hand.

**Traceability:** BO-4; FR-20.

---

#### UC-21 — Receive Delivery & Increase Stock

| Field | Detail |
|---|---|
| **Module** | Purchasing | **Priority** | High |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) — atomic update; Supplier (data) |
| **Goal / Description** | Increase stock on hand when a PO's delivery is recorded as received, in one atomic transaction. |
| **Trigger** | Goods arrive against a PO. |
| **Pre-conditions** | An `Ordered`/partially-received PO exists. |

**Inputs:** PO reference (required); Received quantities per line (0 ≤ received ≤ ordered; partial receipts allowed).

**Main Flow:**

1. Manager selects the PO and enters received quantities per line.
2. In one transaction the system records a stock movement per line, increases each item's on-hand, and updates the PO line's received quantity.
3. The PO status updates to `Received` (all lines fulfilled) or `Partially Received`.
4. The system recomputes reorder flags (UC-22).

**Alternate Flows:**

- **A1 — Partial receipt.** Received < ordered; PO becomes `Partially Received`.

**Exception Flows:**

- **E1 — Received > ordered.** Rejected.
- **E2 — Transaction failure.** No stock change and no PO status change (no partial write).

**Post-conditions:** Stock on hand reflects the delivery; PO status updated; low-stock flags refreshed; every change recorded as a movement.

**Business Rules:** BR-24, BR-21.

**Acceptance Criteria:** Receiving 10 units of an item raises its on-hand by exactly 10; a failed receipt leaves stock and PO status unchanged.

**Traceability:** BO-4; NFR-03; FR-21.

---

#### UC-22 — Flag Low Stock

| Field | Detail |
|---|---|
| **Module** | Inventory | **Priority** | High |
| **Primary Actor** | RMS (System) — surfaced to Manager/Administrator |
| **Secondary Actor** | Manager (acts on the flag) |
| **Goal / Description** | Flag stock items whose quantity on hand is at or below their reorder level, so shortages are caught early. |
| **Trigger** | Stock quantity changes or inventory is viewed. |
| **Pre-conditions** | Stock items with reorder levels exist (UC-18). |

**Main Flow:**

1. When stock changes or inventory is viewed, the system evaluates `quantity on hand ≤ reorder level` for each item.
2. Flagged items are visually highlighted in inventory and listed in the inventory report (UC-28).

**Alternate Flows:**

- **A1 — Flag cleared.** Receiving stock above the reorder level clears the flag.

**Exception Flows:** None.

**Post-conditions:** A visible low-stock indicator and a filterable list of items needing reorder.

**Business Rules:** BR-25.

**Acceptance Criteria:** An item at or below its reorder level is flagged; receiving stock above the level clears the flag.

**Traceability:** BO-4; FR-22.

---

### 5.5 Staff & Reservations

#### UC-23 — Manage Staff Records

| Field | Detail |
|---|---|
| **Module** | Staff | **Priority** | Medium |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Create, edit and deactivate staff records (name, role, contact, status), distinct from system user accounts. |
| **Trigger** | Hiring, role change, or offboarding. |
| **Pre-conditions** | Manager authenticated with staff permissions. |

**Inputs:** Name (required, 2–100 chars); Role/position (required; e.g. Cashier, Waiter, Chef); Contact (phone/email, format-validated); Status (Active/Inactive).

**Main Flow:**

1. Manager creates or edits a staff record.
2. The system saves it; the record appears in the roster.
3. Manager deactivates a departing employee's record, retaining history.

**Alternate Flows:**

- **A1 — Optional login link.** A staff record may or may not be linked to a user account (they are distinct — UC-03).

**Exception Flows:**

- **E1 — Delete a record with history.** A record linked to historical activity is deactivated, not deleted (BR-26).

**Post-conditions:** The roster reflects the change; inactive staff are excluded from active listings but retained.

**Business Rules:** BR-26, BR-05.

**Acceptance Criteria:** A new staff record is stored and appears in the roster; deactivation hides them from active lists but keeps history.

**Traceability:** BO-3, BO-5; FR-23.

---

#### UC-24 — Create Reservation

| Field | Detail |
|---|---|
| **Module** | Reservations | **Priority** | High |
| **Primary Actor** | Cashier, Manager, Administrator |
| **Secondary Actor** | RMS (System) — overlap/capacity checks; Customer (data) |
| **Goal / Description** | Create a reservation with customer name, contact, date, time, party size and table. |
| **Trigger** | A customer books a table. |
| **Pre-conditions** | User authenticated with reservation permissions; at least one table is defined (UC-08). |

**Inputs:** Customer name (required, 2–100 chars); Contact (required; phone/email, format-validated); Date & time (required; future, valid service time); Party size (required, integer ≥ 1); Table (required; an existing table).

**Main Flow:**

1. User enters the reservation details and selects a table.
2. The system checks the table/time does not overlap an existing active reservation (UC-26).
3. The system saves the reservation with status `Booked` and sets the table `Reserved` for the service window.
4. The reservation appears in the day's list.

**Alternate Flows:**

- **A1 — Party exceeds capacity.** The system warns and may require an override or a larger table (BR-28).

**Exception Flows:**

- **E1 — Past date/time.** Rejected.
- **E2 — Overlap detected.** Rejected (UC-26).

**Post-conditions:** A stored reservation; the table reflects the booking; the reservation appears in the day's list.

**Business Rules:** BR-27, BR-28.

**Acceptance Criteria:** A valid future reservation on a free time-slot is saved and marks the table `Reserved`.

**Traceability:** BO-1, BO-5; FR-24.

---

#### UC-25 — Reservation Lifecycle (Seat / Complete / Cancel)

| Field | Detail |
|---|---|
| **Module** | Reservations | **Priority** | Medium |
| **Primary Actor** | Cashier, Manager, Administrator |
| **Secondary Actor** | RMS (System) — table state; Customer (data) |
| **Goal / Description** | Move a reservation through its lifecycle — seat, complete, or cancel — keeping the table's reserved hold accurate. |
| **Trigger** | Guest arrives, dines, or cancels. |
| **Pre-conditions** | A reservation exists in a valid current state. |

**Main Flow:**

1. On arrival the user seats the reservation: `Booked → Seated`; the table becomes `Occupied` and an order may be opened (UC-10).
2. When the visit ends: `Seated → Completed`.
3. The system frees the table's reserved hold on complete/cancel per the table state model.

**Alternate Flows:**

- **A1 — Cancel.** `Booked/Seated → Cancelled` frees the slot for rebooking.
- **A2 — No-Show.** An optional `No-Show` state closes out un-arrived bookings.

**Exception Flows:**

- **E1 — Illegal transition.** Only defined transitions are permitted; others are rejected (BR-29).

**Post-conditions:** Reservation and table statuses reflect the lifecycle stage.

**Business Rules:** BR-29, BR-12.

**Acceptance Criteria:** Seating a reservation frees the reserved hold and marks the table `Occupied`; cancelling frees the slot for rebooking.

**Traceability:** BO-1; FR-25.

---

#### UC-26 — Prevent Double-Booking

| Field | Detail |
|---|---|
| **Module** | Reservations | **Priority** | High |
| **Primary Actor** | RMS (System) |
| **Secondary Actor** | Cashier/Manager (receives the result) |
| **Goal / Description** | Prevent double-booking the same table for overlapping reservation times. |
| **Trigger** | A reservation is created or rescheduled. |
| **Pre-conditions** | A target table and requested time window are specified (UC-24). |

**Main Flow:**

1. Before saving, the system checks the target table for any active (`Booked`/`Seated`) reservation whose time window overlaps the requested window (default slot duration configurable).
2. If no overlap exists, the reservation is saved.

**Alternate Flows:**

- **A1 — Suggest alternatives.** On conflict the system may suggest alternative free tables/times.
- **A2 — Cancelled/completed do not block.** Cancelled/completed reservations do not block new bookings.

**Exception Flows:**

- **E1 — Overlap detected.** Save blocked with a clear conflict message.

**Post-conditions:** Only non-conflicting reservations are stored.

**Business Rules:** BR-27.

**Acceptance Criteria:** Two overlapping reservations on the same table cannot both exist; back-to-back non-overlapping bookings are allowed.

**Traceability:** BO-1; FR-26.

---

### 5.6 Reporting

#### UC-27 — Sales Report

| Field | Detail |
|---|---|
| **Module** | Reporting | **Priority** | High |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) — aggregation |
| **Goal / Description** | Generate a sales report for a chosen date range showing totals and item breakdowns from finalised orders. |
| **Trigger** | Manager selects Reports → Sales. |
| **Pre-conditions** | Manager authenticated with reporting permissions. |

**Inputs:** Date range (required; start ≤ end); Filters (optional; order type, payment method, cashier, category).

**Main Flow:**

1. Manager selects a date range and optional filters.
2. The system aggregates only *finalised* orders in range: total sales, order count, average order value, tax collected, discounts given, and a per-item/per-category quantity-and-revenue breakdown.
3. The report is displayed on screen and is exportable (UC-30).

**Alternate Flows:**

- **A1 — Filtered view.** Results narrowed by the chosen filters.

**Exception Flows:**

- **E1 — start > end.** Rejected.
- **E2 — No data in range.** Empty report with a clear note.

**Post-conditions:** An on-screen sales report, exportable via UC-30.

**Business Rules:** BR-30.

**Acceptance Criteria:** Totals reconcile to the sum of finalised orders in range; item breakdown quantities and revenue match the underlying orders.

**Traceability:** BO-3; FR-27.

---

#### UC-28 — Inventory / Stock Report

| Field | Detail |
|---|---|
| **Module** | Reporting | **Priority** | High |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Generate an inventory report showing current stock levels and items below reorder level. |
| **Trigger** | Manager selects Reports → Inventory. |
| **Pre-conditions** | Manager authenticated with reporting permissions. |

**Main Flow:**

1. Manager opens the inventory report.
2. The system lists every active stock item with unit, quantity on hand, reorder level and a low-stock indicator (`quantity ≤ reorder level`).
3. Manager may filter to "below reorder level only" to drive purchasing.

**Alternate Flows:**

- **A1 — Below-reorder-only.** Filters to items needing reorder.

**Exception Flows:** None specific; an empty catalogue yields an empty report.

**Post-conditions:** An on-screen inventory report, exportable via UC-30.

**Business Rules:** BR-25.

**Acceptance Criteria:** Every item at/below its reorder level is listed as low; quantities match live stock on hand at generation time.

**Traceability:** BO-3, BO-4; FR-28.

---

#### UC-29 — Staff Activity Report

| Field | Detail |
|---|---|
| **Module** | Reporting | **Priority** | Medium |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Generate a staff activity report for a chosen date range, summarising per-user activity. |
| **Trigger** | Manager selects Reports → Staff Activity. |
| **Pre-conditions** | Manager authenticated; session events and finalised orders exist. |

**Inputs:** Date range (required; start ≤ end).

**Main Flow:**

1. Manager selects a date range.
2. The system summarises per user/cashier: login/logout activity, number of orders processed, total sales handled, and discounts applied — derived from session events (UC-01/UC-04) and finalised orders.
3. The report is displayed and is exportable (UC-30).

**Alternate Flows:** None specific.

**Exception Flows:**

- **E1 — RBAC.** Only Manager/Administrator may view it; a Cashier is denied (UC-02).

**Post-conditions:** An on-screen staff activity report, exportable via UC-30.

**Business Rules:** BR-30.

**Acceptance Criteria:** Order counts and sales totals per cashier reconcile to finalised orders in the range.

**Traceability:** BO-3; FR-29.

---

#### UC-30 — Export Reports

| Field | Detail |
|---|---|
| **Module** | Reporting | **Priority** | Medium |
| **Primary Actor** | Manager, Administrator |
| **Secondary Actor** | RMS (System) |
| **Goal / Description** | Export any report (UC-27…UC-29) to a shareable, printable format (e.g. PDF) or print it directly. |
| **Trigger** | A report is displayed. |
| **Pre-conditions** | A report has been generated on screen. |

**Main Flow:**

1. Manager chooses Export/Print on a displayed report.
2. The system produces a document reproducing the on-screen report exactly, with title, generated-by user, generation timestamp and the applied date range/filters in the header.

**Alternate Flows:**

- **A1 — Direct print.** The report is sent to a printer instead of a file.

**Exception Flows:** None specific.

**Post-conditions:** A file/printout suitable for records or sharing with the owner.

**Business Rules:** —

**Acceptance Criteria:** The exported document matches the displayed report and records its parameters.

**Traceability:** BO-3; FR-30.

---

## 6. Use Case → Requirement Traceability

Every function-level use case maps 1:1 to a functional requirement and up to at least one business objective; workflow use cases combine several. This gives full coverage from business need to verifiable behaviour.

### 6.1 Function-Level Use Case Traceability

| Use Case | FR | Business Objective(s) | Business Rule(s) | Workflow(s) | Priority |
|---|---|---|---|---|---|
| UC-01 Login & Authentication | FR-01 | BO-5 | BR-01, BR-02 | BUC-8 | High |
| UC-02 Role-Based Access Control | FR-02 | BO-5 | BR-03 | All | High |
| UC-03 Manage User Accounts | FR-03 | BO-5 | BR-02, BR-04, BR-05, BR-06 | BUC-8, BUC-10 | High |
| UC-04 Logout / Session End | FR-04 | BO-5 | BR-07 | BUC-8 | High |
| UC-05 Manage Menu Categories | FR-05 | BO-1, BO-5 | BR-08 | BUC-3 | High |
| UC-06 Manage Menu Items | FR-06 | BO-1, BO-2, BO-5 | BR-09, BR-10 | BUC-3 | High |
| UC-07 Toggle Item Availability | FR-07 | BO-1 | BR-10 | BUC-3 | Medium |
| UC-08 Define Tables | FR-08 | BO-1, BO-5 | BR-11 | BUC-4 | High |
| UC-09 Display & Update Table Status | FR-09 | BO-1 | BR-12 | BUC-1, BUC-4, BUC-6 | High |
| UC-10 Open Order | FR-10 | BO-1 | BR-14 | BUC-1, BUC-2 | High |
| UC-11 Add/Remove Items | FR-11 | BO-1, BO-2 | BR-15 | BUC-1, BUC-2 | High |
| UC-12 Subtotal Calculation | FR-12 | BO-2 | BR-13, BR-16 | BUC-1, BUC-2 | High |
| UC-13 Apply Discount | FR-13 | BO-2 | BR-17 | BUC-1, BUC-2 | Medium |
| UC-14 Tax & Total | FR-14 | BO-2 | BR-16, BR-18 | BUC-1, BUC-2, BUC-10 | High |
| UC-15 Record Payment / Finalise | FR-15 | BO-1, BO-2 | BR-19 | BUC-1, BUC-2 | High |
| UC-16 Generate Receipt | FR-16 | BO-1, BO-2 | BR-20 | BUC-1, BUC-2 | High |
| UC-17 Release Table | FR-17 | BO-1 | BR-12 | BUC-1, BUC-4 | High |
| UC-18 Manage Stock Items | FR-18 | BO-4, BO-5 | BR-21, BR-05 | BUC-5 | High |
| UC-19 Manage Suppliers | FR-19 | BO-4, BO-5 | BR-22, BR-05 | BUC-5 | Medium |
| UC-20 Create Purchase Order | FR-20 | BO-4 | BR-23 | BUC-5 | High |
| UC-21 Receive Delivery | FR-21 | BO-4 | BR-24, BR-21 | BUC-5 | High |
| UC-22 Flag Low Stock | FR-22 | BO-4 | BR-25 | BUC-5, BUC-7 | High |
| UC-23 Manage Staff | FR-23 | BO-3, BO-5 | BR-26, BR-05 | BUC-9 | Medium |
| UC-24 Create Reservation | FR-24 | BO-1, BO-5 | BR-27, BR-28 | BUC-6 | High |
| UC-25 Reservation Lifecycle | FR-25 | BO-1 | BR-29, BR-12 | BUC-6 | Medium |
| UC-26 Prevent Double-Booking | FR-26 | BO-1 | BR-27 | BUC-6 | High |
| UC-27 Sales Report | FR-27 | BO-3 | BR-30 | BUC-7 | High |
| UC-28 Inventory Report | FR-28 | BO-3, BO-4 | BR-25 | BUC-7 | High |
| UC-29 Staff Activity Report | FR-29 | BO-3 | BR-30 | BUC-7 | Medium |
| UC-30 Export Reports | FR-30 | BO-3 | — | BUC-7 | Medium |
| UC-31 Configure Reference/System Data | FR-31 | BO-2, BO-5 | BR-31, BR-03, BR-09, BR-18 | BUC-10 | High |

*BO-6 (Prepare for growth) is served by the architecture (NFR-05/06) rather than a single use case, and underpins the phased desktop→web delivery of all use cases above.*

### 6.2 Workflow → Business Objective Coverage

| Workflow | BO-1 | BO-2 | BO-3 | BO-4 | BO-5 |
|---|:---:|:---:|:---:|:---:|:---:|
| BUC-1 Order-to-Payment (Dine-in) | ✔ | ✔ | | | |
| BUC-2 Order-to-Payment (Takeaway) | ✔ | ✔ | | | |
| BUC-3 Menu Maintenance | ✔ | | | | ✔ |
| BUC-4 Table Setup & Turnover | ✔ | | | | ✔ |
| BUC-5 Inventory & Purchasing | | | | ✔ | ✔ |
| BUC-6 Reservation Management | ✔ | | | | ✔ |
| BUC-7 Reporting & Export | | | ✔ | ✔ | |
| BUC-8 User & Access Administration | | | | | ✔ |
| BUC-9 Staff Roster Management | | | ✔ | | ✔ |
| BUC-10 System Setup & Reference Data | | ✔ | | | ✔ |

---

## Appendix A — Business Rules Referenced

The use cases above enforce the following cross-cutting business rules (from FRD §6), applied consistently in the shared business layer.

| ID | Rule |
|---|---|
| BR-01 | No function is accessible without a valid authenticated session. |
| BR-02 | Passwords are stored only as salted one-way hashes; never in plain text in DB, logs, or UI. |
| BR-03 | Every function checks the caller's role in the business layer, independent of the UI. |
| BR-04 | Usernames are unique across the system. |
| BR-05 | Records with historical references are deactivated, not deleted. |
| BR-06 | At least one active Administrator must always exist. |
| BR-07 | Logout with an unsaved open order requires finalise/park/discard first. |
| BR-08 | Menu category names are unique. |
| BR-09 | Price changes apply to future orders only; finalised bills are never retro-priced. |
| BR-10 | Items with order history are made Unavailable rather than deleted. |
| BR-11 | Table labels are unique. |
| BR-12 | Table status follows the defined state model; illegal transitions are rejected. |
| BR-13 | line total = unit-price snapshot × quantity; subtotal = Σ line totals. |
| BR-14 | Each order has a unique number, creating user, and timestamp. |
| BR-15 | Order lines capture the unit price at time of ordering (price snapshot). |
| BR-16 | Monetary values use two-decimal precision with one consistent rounding step. |
| BR-17 | Discount applies to subtotal before tax; result can never be negative. |
| BR-18 | tax = (subtotal − discount) × tax_rate; total = (subtotal − discount) + tax; tax rate stored on the order. |
| BR-19 | Finalisation is atomic: payment + status + stored figures commit together or not at all. |
| BR-20 | Receipts reproduce stored finalised figures; re-prints are identical. |
| BR-21 | Stock item names are unique; on-hand changes only via receipts/logged adjustments. |
| BR-22 | Supplier names are unique. |
| BR-23 | A PO has a unique number and ≥ 1 valid line; creating it does not change stock. |
| BR-24 | Receiving a delivery increases on-hand by received qty within a transaction. |
| BR-25 | An item is low when quantity on hand ≤ reorder level. |
| BR-26 | Staff records are distinct from user accounts; deactivate to retain history. |
| BR-27 | No two active reservations may overlap on the same table. |
| BR-28 | Party size exceeding table capacity triggers a warning/override. |
| BR-29 | Reservation lifecycle follows the defined state model and frees table holds on complete/cancel. |
| BR-30 | Reports aggregate only finalised data over the selected range/filters. |
| BR-31 | Reference/system data (tax rate, payment methods, system constants) is Administrator-configurable only; every change is audited (who/when) and the tax rate in force is snapshotted onto each finalised order. |

---

## Appendix B — Status & Enumeration Reference

| Enumeration | Values |
|---|---|
| User Role | Administrator, Manager, Cashier |
| Account / Staff / Supplier / Stock Status | Active, Inactive |
| Menu Item Availability | Available, Unavailable |
| Table Status | Free, Occupied, Reserved, Needs Cleaning |
| Order Type | Dine-in, Takeaway |
| Order Status | Open, Paid/Closed, Cancelled |
| Discount Type | None, Percentage, Fixed |
| Payment Method | Cash, Card, Other |
| Purchase Order Status | Ordered, Partially Received, Received, Cancelled |
| Reservation Status | Booked, Seated, Completed, Cancelled, No-Show |

### State Models (summary)

**Order:** Open → Paid/Closed (finalise); Open → Cancelled (voided before payment).

**Table:** Free/Reserved → Occupied (open dine-in order / seat reservation); Occupied → Needs Cleaning (close order); Needs Cleaning → Free (cleaned); Free ↔ Reserved (create/cancel reservation).

**Reservation:** Booked → Seated → Completed; Booked/Seated → Cancelled; Booked → No-Show.

**Purchase Order:** Ordered → Partially Received → Received; Ordered → Cancelled.

---

*— End of Business Use Cases Document (RMS-BUCD v1.1) —*
