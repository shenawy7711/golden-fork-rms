# Feature Specification: Golden Fork RMS

**Feature Branch**: `001-golden-fork-rms`

**Created**: 2026-07-14

**Status**: Draft

**Input**: User description: "Build the spec for Golden Fork RMS from FRD.md and RMS_Business_Use_Cases.md. Cover what the system does, not how. Three roles (Administrator, Manager, Cashier) with the FRD role-permission matrix. Six modules with all 30 functional requirements. For each FR use its actor, inputs/validation, business rules, and acceptance criteria as written in the docs. Carry BR-01 to BR-30 and NFR-01 to NFR-08. Keep it implementation-agnostic; mark anything undecided as NEEDS CLARIFICATION."

**Source documents**: BRD v1.0, FRD v1.1 (FR-01…FR-31, BR-01…BR-31, NFR-01…NFR-08), RMS Business Use Cases v1.1.

---

## Overview

Golden Fork RMS is a single-location restaurant management system that automates the core
daily operations of taking orders, producing accurate bills and receipts, maintaining the
menu and dining tables, tracking inventory and supplier purchases, recording reservations,
administering staff and user accounts, and producing management reports. It replaces manual,
paper-based processes to speed up service, eliminate billing arithmetic errors, give
management on-demand visibility, and keep all operational data in one consistent store.

This specification describes **what** the system must do and the rules it must enforce. It is
technology-agnostic: it names no framework, language, database engine, or screen layout.

### Roles (Actors)

Three application roles authenticate and use the system. Two external parties appear as data
only (Suppliers, Customers) and never log in.

- **Administrator** — full control: manages user accounts and roles, configures reference/
  system data (e.g. tax rate), and can access every module and report. Implicitly holds all
  Manager and Cashier permissions.
- **Manager** — operational control: menu & prices, tables, inventory & suppliers, staff
  records, reservations, and all reports. Implicitly holds all Cashier permissions. Cannot
  manage system-level user accounts unless also an Administrator.
- **Cashier** — front-line: creates and manages orders, produces bills/receipts, records
  payments, views menu and table status, and records reservations. Cannot change prices,
  manage stock, or view management reports.

### Role–Permission Matrix (authoritative; enforced by FR-02)

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

Permissions are cumulative (Administrator ⊇ Manager ⊇ Cashier for the areas each holds). A dash
means the function is denied to that role and MUST be blocked in the interface **and** re-checked
when invoked.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Take an order and produce an accurate bill (Priority: P1)

A cashier serves a seated party (or a takeaway customer) from order entry through to a paid,
printed receipt, with every figure calculated automatically. This is the system's primary
revenue journey and the main reason it exists (speed up service, eliminate billing errors).

**Why this priority**: This is the core operational value of the system (BO-1, BO-2). Without
correct, fast order-to-payment, nothing else matters.

**Independent Test**: With at least one active user, one available menu item, and one free
table already present, a cashier can open an order, add items, apply a discount, see tax and
total computed, record payment, finalise atomically, print a receipt whose figures match the
bill, and see the table released — all without manual arithmetic.

**Acceptance Scenarios**:

1. **Given** a free table and available menu items, **When** the cashier opens a dine-in order
   and adds items with quantities, **Then** the table becomes Occupied and the subtotal equals
   Σ(unit price × quantity) to two decimals, updating on every change.
2. **Given** an open order with a 100.00 subtotal, **When** a 10% discount is applied and a 10%
   tax rate is in force, **Then** the discount is 10.00, the pre-tax base is 90.00, the tax is
   9.00, and the total is 99.00.
3. **Given** an open order with items and a chosen payment method, **When** the cashier
   finalises, **Then** the payment, order status (Paid/Closed), and stored figures commit
   together; a receipt reproduces those exact figures; and a dine-in table becomes Needs
   Cleaning.
4. **Given** finalisation fails partway, **When** the transaction aborts, **Then** the order
   stays Open with no payment written and the table stays Occupied (no partial data).
5. **Given** an order already finalised, **When** anyone later edits a menu price or the tax
   rate, **Then** that finalised bill and its reprinted receipt are unchanged.

---

### User Story 2 - Authenticate and enforce role-based access (Priority: P1)

Every user signs in before doing anything, and the system restricts each function to the
permitted roles, checked in the business layer, not just hidden in the interface.

**Why this priority**: Authentication and RBAC are the security foundation that gates all
other functionality; financial and personal data must not be exposed to unauthorised roles.

**Independent Test**: With accounts for each role, a user can log in with valid credentials and
land on a role-appropriate home; invalid credentials are refused with a generic message and no
session; a deactivated account cannot log in; and a role-forbidden function is refused even if
the interface control were bypassed.

**Acceptance Scenarios**:

1. **Given** valid credentials for an active account, **When** the user logs in, **Then** a
   session bound to their role is created, the login is recorded, and only permitted functions
   are reachable.
2. **Given** any invalid credential or a deactivated account, **When** login is attempted,
   **Then** access is denied with a generic message and no session is created.
3. **Given** a Cashier session, **When** a price-edit, stock, or report function is invoked by
   any path, **Then** the business layer refuses it and records the attempt.
4. **Given** a logged-in user with an unsaved open order, **When** they log out, **Then** the
   system requires the order to be finalised, parked, or discarded first, then ends the session.

---

### User Story 3 - Maintain the menu and dining tables (Priority: P2)

A manager keeps categories, items, prices, availability, and the dining-room table layout up to
date so cashiers always order against current information.

**Why this priority**: Menu and tables are prerequisites for taking orders and reservations,
but setup happens less frequently than order-taking.

**Independent Test**: A manager can create a category and an available item at a valid price and
immediately order it; mark an item unavailable so it disappears from ordering yet keeps its
history; define a uniquely-labelled table with capacity; and see table status change with order
and reservation events.

**Acceptance Scenarios**:

1. **Given** the menu screen, **When** a manager creates a category and then an available item
   with a valid non-negative price, **Then** both are immediately selectable when ordering.
2. **Given** an item with order history, **When** the manager attempts to delete it, **Then** the
   system marks it Unavailable/discontinued instead of hard-deleting, preserving history.
3. **Given** a defined table, **When** orders open/close and reservations are made, **Then** its
   status moves only through the allowed transitions (Free, Occupied, Reserved, Needs Cleaning).

---

### User Story 4 - Manage inventory, suppliers, and purchasing (Priority: P2)

A manager records stock items and reorder levels, registers suppliers, raises purchase orders,
records deliveries that increase stock, and is alerted when items run low.

**Why this priority**: Controls stock and cost (BO-4); important for management but not on the
critical order-to-payment path.

**Independent Test**: A manager can create a stock item with a reorder level, register a
supplier, raise a purchase order that does not change stock, record a delivery that increases
on-hand atomically, and see low-stock items flagged.

**Acceptance Scenarios**:

1. **Given** an active supplier and stock items, **When** a manager creates a purchase order with
   at least one valid line, **Then** it is saved with status Ordered and stock on hand is
   unchanged.
2. **Given** an Ordered purchase order, **When** the manager records received quantities (≤
   ordered), **Then** each item's on-hand increases by the received amount in one transaction and
   the PO status becomes Received or Partially Received.
3. **Given** an item whose on-hand is at or below its reorder level, **When** inventory is
   viewed, **Then** the item is flagged; receiving stock above the level clears the flag.

---

### User Story 5 - Manage staff records and reservations (Priority: P2)

A manager maintains staff records; a cashier or manager books tables for customers without
double-booking, and moves reservations through their lifecycle.

**Why this priority**: Supports operations and customer experience; independent of the billing
path.

**Independent Test**: A manager can create and deactivate a staff record (keeping history); a
cashier can book a future reservation on a free time-slot that marks the table Reserved, cannot
create an overlapping booking on the same table, and can seat/complete/cancel it.

**Acceptance Scenarios**:

1. **Given** the staff roster, **When** a manager deactivates a staff record linked to history,
   **Then** the person is removed from active lists but their historical records remain.
2. **Given** a table free for a future time window, **When** a valid reservation is created,
   **Then** it is saved as Booked and the table shows Reserved for that window.
3. **Given** an active reservation on a table/time window, **When** an overlapping reservation on
   the same table is attempted, **Then** it is rejected with a conflict message.

---

### User Story 6 - Generate and export management reports (Priority: P3)

A manager or administrator produces sales, inventory, and staff-activity reports over a chosen
date range and exports them for records or sharing.

**Why this priority**: Delivers management visibility (BO-3) but depends on operational data
having first been captured by the other journeys.

**Independent Test**: With finalised orders and stock data present, a manager can generate a
sales report whose totals reconcile to those orders, an inventory report that flags low stock, a
staff-activity report per cashier, and export any of them with their parameters recorded.

**Acceptance Scenarios**:

1. **Given** finalised orders in a date range, **When** a sales report is generated, **Then** its
   totals and per-item/category breakdown reconcile to those orders and derive from stored
   figures (never current menu prices).
2. **Given** any displayed report, **When** the user exports it, **Then** the output matches the
   on-screen report and records the title, generating user, timestamp, and applied range/filters.
3. **Given** a Cashier session, **When** any report is requested, **Then** it is denied (RBAC).

### Edge Cases

- Login while the data store is unreachable → no session created; a service-unavailable message
  is shown.
- Repeated failed logins → temporarily throttled after a configurable number of consecutive
  failures (default 5).
- Idle authenticated terminal → automatic logout after a configurable idle timeout (default 15
  minutes).
- Attempt to remove or deactivate the last active Administrator → refused.
- Delete of a category that still contains items, or an item/supplier/stock item with history →
  blocked or converted to deactivate/mark-unavailable.
- Opening a dine-in order on a table that already has an open order → prevented (offer to open
  the existing order).
- Finalising an order with zero items → prevented.
- Cash tendered less than the total → rejected; change is computed as tendered − total (≥ 0).
- Discount that would drive the total negative (percentage > 100, or fixed > subtotal) → rejected.
- Received quantity greater than ordered on a delivery → rejected.
- Reservation in the past, or party size exceeding table capacity → rejected / warned with
  possible override.
- Report requested with start date after end date → rejected; empty range → empty report with a
  clear note.
- Illegal table, order, reservation, or purchase-order state transition → rejected.

---

## Requirements *(mandatory)*

Requirements are grouped into the six modules. Each functional requirement lists its **Actor(s)**,
**Inputs/Validation**, **Business Rules**, and **Acceptance Criteria** exactly as derived from the
FRD. Field-level validation rules are consolidated in *Appendix A*; enumerations in *Appendix B*.
Cross-cutting business rules (BR-01…BR-31) are catalogued after the FRs; non-functional
requirements (NFR-01…NFR-08) follow.

### Module 1 — Authentication & Administration (FR-01 … FR-04, FR-31)

#### FR-01 — User Login & Authentication
- **Actor(s)**: All roles (Administrator, Manager, Cashier).
- **Inputs/Validation**: Username (required, 3–50 chars, case-insensitive match). Password
  (required, entered masked, compared against the stored salted one-way hash; never stored or
  logged in plain text).
- **Business Rules**: BR-01, BR-02. Credentials validated by comparing salted hashes. On success,
  create a session bound to the user's role and record the login event (feeds FR-29). Deactivated
  accounts are refused even with correct credentials. After a configurable number of consecutive
  failures (default 5), login is temporarily throttled.
- **Acceptance Criteria**: Valid credentials for an active account → authenticated and routed to
  the role-appropriate home. Any invalid credential → denied with a generic "Invalid username or
  password" and no session. Passwords never appear readable in the store, logs, or screens.
  Unreachable data store → "Unable to sign in — service unavailable"; no session.

#### FR-02 — Role-Based Access Control
- **Actor(s)**: System (enforced for every authenticated user).
- **Inputs/Validation**: The invoked function and the caller's role.
- **Business Rules**: BR-03; the §Role–Permission Matrix. Every function is tagged with permitted
  roles; the business layer verifies the caller's role before executing. Denied functions are
  hidden/disabled in the interface **and** re-checked on execution (defence in depth). Permissions
  are cumulative. Cashiers cannot access price changes, stock management, or management reports.
- **Acceptance Criteria**: A Cashier cannot reach price-edit, stock, or report functions by any
  path. A Manager can reach all operational functions but not user-account management (unless also
  Administrator). A role-forbidden call is rejected even if the interface control were bypassed;
  the attempt is recorded.

#### FR-03 — Manage User Accounts & Roles
- **Actor(s)**: Administrator.
- **Inputs/Validation**: Username (required, unique, 3–50 chars). Full name (required, 2–100).
  Role (required; one of Administrator/Manager/Cashier). Initial password (required on create;
  meets password policy — Appendix A; stored hashed). Status (Active/Inactive, default Active).
- **Business Rules**: BR-04, BR-05, BR-06. Usernames unique. **Deactivate** preserves the account
  and its history but blocks login; **Delete** is permitted only when there are no dependent
  historical records, otherwise deactivation is required. The last remaining active Administrator
  cannot be removed or deactivated. Role changes take effect on next login.
- **Acceptance Criteria**: A new account with a valid unique username and role can log in
  immediately. A deactivated account cannot log in but its past records remain intact. The system
  never allows zero active Administrators. Duplicate username → "Username already exists."

#### FR-04 — Logout & Session Termination
- **Actor(s)**: All roles.
- **Inputs/Validation**: Logout action (or idle timeout).
- **Business Rules**: BR-07. On logout the session is invalidated and the logout event recorded
  (feeds FR-29). If an order is open and unsaved, the system warns and requires finalise/park/
  discard first, so no partial financial data is orphaned. Optional idle timeout (default 15 min)
  triggers automatic logout.
- **Acceptance Criteria**: After logout, re-invoking a protected function requires
  re-authentication. Logout with an open order prompts for resolution before ending the session.

#### FR-31 — Configure Reference / System Data
- **Actor(s)**: Administrator (only).
- **Inputs/Validation**: **Tax rate** (required; decimal ≥ 0, ≤ 1.0000 i.e. 0–100%, stored to
  four decimals). **Payment methods** (reference list; each name unique, 2–20 chars; activated/
  deactivated rather than hard-deleted once referenced by a payment). **Idle timeout minutes**
  (integer ≥ 0; default 15; 0 disables). **Login max attempts** (integer ≥ 1; default 5).
  **Reservation slot minutes** (integer ≥ 1; default 90). **Discount approval threshold**
  (a fixed monetary amount in the configured currency, decimal ≥ 0 — the discount *amount* above
  which a discount requires Manager/Administrator authorisation, FR-13).
- **Business Rules**: BR-31, BR-09, BR-18, BR-03. Reference/system data is Administrator-only,
  enforced in the business layer (`CONFIGURE_SYSTEM`) independent of the interface. Each change
  is persisted with the changing administrator and a timestamp for audit. Billing (FR-14) and
  validation consume the **current** values; the tax rate in force at finalisation is
  snapshotted onto each order (BR-18), so changing it later never alters a finalised bill
  (BR-09). Reference data (a tax rate and at least one active payment method) must exist before
  an order can be finalised (dependency for FR-14/FR-15).
- **Acceptance Criteria**: Only an Administrator can view or change reference/system data; a
  non-Administrator attempt is rejected in the business layer even if the interface control were
  bypassed, and the attempt is recorded. Changing the tax rate does not alter any previously
  finalised bill. Every change records who changed it and when. An order cannot be finalised
  before a tax rate and a payment method are configured.

### Module 2 — Menu & Table Management (FR-05 … FR-09)

#### FR-05 — Manage Menu Categories
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Category name (required, unique, 2–50 chars). Display order (optional
  integer).
- **Business Rules**: BR-08. Category names unique. A category that still contains items cannot be
  hard-deleted; items must be reassigned or removed first. Changes appear immediately in ordering
  screens.
- **Acceptance Criteria**: A new category is immediately selectable when adding an item and when
  ordering. Deleting a non-empty category is prevented. Duplicate name rejected.

#### FR-06 — Manage Menu Items
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Item name (required, 2–80 chars, unique within its category). Category
  (required, existing). Price (required, decimal ≥ 0.00, two decimals, within currency limits).
  Availability (Available/Unavailable, default Available). Description (optional, ≤ 255 chars).
- **Business Rules**: BR-09, BR-10. Price non-negative, stored to two decimals; price changes apply
  only to future orders — finalised bills are never retro-priced. An item referenced by any
  historical order is not hard-deleted; it is marked Unavailable/discontinued.
- **Acceptance Criteria**: A new available item at a valid price is orderable immediately at that
  price. Editing a price does not alter any previously finalised bill. Negative/invalid price
  rejected.

#### FR-07 — Toggle Menu Item Availability
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Availability toggle on an existing item.
- **Business Rules**: BR-10. Unavailable items are not selectable in new orders but remain visible
  (flagged) in administration. Marking unavailable does not affect items already on open orders.
- **Acceptance Criteria**: An item toggled Unavailable disappears from order-entry but its record
  and history persist; re-enabling makes it immediately orderable again.

#### FR-08 — Define Dining Tables
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Table number/label (required, unique, 1–10 chars). Seating capacity
  (required, integer ≥ 1).
- **Business Rules**: BR-11. Table labels unique; capacity supports reservation party-size checks.
  A table currently occupied or holding future reservations cannot be deleted until free and
  un-booked. New tables default to status Free.
- **Acceptance Criteria**: A defined table appears for order and reservation selection with its
  capacity. Duplicate labels rejected.

#### FR-09 — Display & Update Table Status
- **Actor(s)**: All roles (view/update per matrix).
- **Inputs/Validation**: Status change events (order/reservation) or manual change. Valid statuses:
  Free, Occupied, Reserved, Needs Cleaning.
- **Business Rules**: BR-12; Appendix B state model. Opening a dine-in order on a Free/Reserved
  table sets it Occupied. Closing a dine-in order sets it Needs Cleaning (FR-17); staff mark it
  Free when cleaned. A confirmed reservation sets/keeps Reserved until seated or cancelled. Only
  defined transitions are allowed.
- **Acceptance Criteria**: Table status visibly changes in response to order open/close and
  reservation events. An invalid manual transition is prevented.

### Module 3 — Orders & Billing / POS (FR-10 … FR-17)

> **Financial-integrity note**: All monetary calculation (FR-12…FR-16) is centralised in one
> billing engine, computed in a single transaction, and rounded consistently (BR-13, BR-16). This
> mitigates the "incorrect financial calculations" risk and satisfies BO-2.

#### FR-10 — Open a New Order
- **Actor(s)**: Cashier, Manager, Administrator.
- **Inputs/Validation**: Order type (required; Dine-in or Takeaway). Table (required if Dine-in;
  must be a Free or own-Reserved table; omitted for Takeaway).
- **Business Rules**: BR-14. A new order is created with a unique order number, status Open,
  timestamp, and creating user. For dine-in the selected table cannot already have an open order;
  on open, the table becomes Occupied.
- **Acceptance Criteria**: A dine-in order can only be opened on a table without an active order,
  and that table becomes Occupied. A takeaway order opens with no table. Table already has an open
  order → prevented (with option to open the existing order).

#### FR-11 — Add / Remove Order Items
- **Actor(s)**: Cashier, Manager, Administrator.
- **Inputs/Validation**: Menu item (required; currently Available). Quantity (required, integer ≥ 1).
- **Business Rules**: BR-15. Each order line captures item, quantity, and the unit price at time of
  ordering (price snapshot). Adding an existing item increments quantity (or adds a line, per
  configuration). Items may be added/removed only while the order is Open; a finalised order is
  immutable. Subtotal recomputes on every change (FR-12).
- **Acceptance Criteria**: Adding/removing lines updates the subtotal immediately and correctly.
  Only available items can be added; finalised orders cannot be edited. Quantity < 1 → rejected.

#### FR-12 — Automatic Subtotal Calculation
- **Actor(s)**: System.
- **Inputs/Validation**: Current order lines and quantities.
- **Business Rules**: BR-13, BR-16. line total = unit-price snapshot × quantity; subtotal = Σ line
  totals. Two-decimal rounding applied consistently; computed in the business layer.
- **Acceptance Criteria**: For any set of lines, the displayed subtotal equals Σ(price × qty) to
  two decimals and updates within the performance target (NFR-02) on each edit.

#### FR-13 — Apply Discount
- **Actor(s)**: Cashier, Manager, Administrator.
- **Inputs/Validation**: Discount type (Percentage or Fixed amount). Discount value (percentage
  0–100; or fixed 0 ≤ value ≤ subtotal).
- **Business Rules**: BR-17. Discount applies to the subtotal before tax; tax is computed on the
  discounted amount. The discounted amount can never be negative (percentage capped at 100, fixed
  capped at subtotal). **Discount approval is in scope**: a discount whose **amount** exceeds a
  configurable threshold — a fixed monetary amount in the configured currency (set via FR-31) —
  requires Manager (or Administrator) authorisation before it is applied, and the authorisation is
  recorded for audit. Discounts at or below the threshold may be applied by any role permitted to
  discount, without a separate approval step.
- **Acceptance Criteria**: A 10% discount on a 100.00 subtotal yields a 90.00 pre-tax base. No
  discount can produce a negative total. Percentage > 100 or fixed > subtotal → rejected. A discount
  above the configured threshold cannot be applied without Manager/Administrator authorisation, and
  the authorisation is recorded.

#### FR-14 — Automatic Tax & Final Total
- **Actor(s)**: System.
- **Inputs/Validation**: Subtotal, discount, and the configured tax rate (reference data set by
  Administrator).
- **Business Rules**: BR-16, BR-18. tax = round((subtotal − discount) × tax_rate); total =
  (subtotal − discount) + tax. The tax rate is a single configurable reference value; the rate in
  force at finalisation is stored on the order for audit and accurate reprinting. One consistent
  rounding step.
- **Acceptance Criteria**: With a 10% tax rate, a 90.00 pre-tax base yields tax 9.00 and total
  99.00. Changing the reference tax rate does not alter any previously finalised bill.

#### FR-15 — Record Payment & Finalise Order
- **Actor(s)**: Cashier, Manager, Administrator.
- **Inputs/Validation**: Payment method (required; e.g. Cash, Card, Other — reference list). Amount
  tendered (optional, for Cash; system computes change = tendered − total ≥ 0).
- **Business Rules**: BR-19; NFR-03. Finalisation records the payment, sets status Paid/Closed, and
  stores the final figures (subtotal, discount, tax rate, tax, total) in one atomic transaction. An
  order with zero items cannot be finalised. Once closed, the order is immutable. **Each order is
  settled by exactly one payment** — split billing and multiple payments per order are out of scope
  for v1.
- **Acceptance Criteria**: Finalising with a valid method closes the order and locks its figures. A
  failed finalisation leaves the order fully Open with no orphaned payment. No method → cannot
  finalise. Cash tendered < total → rejected.

#### FR-16 — Generate Receipt
- **Actor(s)**: Cashier, Manager, Administrator.
- **Inputs/Validation**: A finalised order.
- **Business Rules**: BR-20. The receipt includes: restaurant name/header, receipt/order number,
  date-time, table or "Takeaway", itemised lines (name, qty, unit price, line total), subtotal,
  discount, tax rate & tax, grand total, payment method (and change if cash), and the serving
  cashier. It reproduces the stored finalised figures exactly (no recomputation) and may be
  re-printed/re-exported without altering data.
- **Acceptance Criteria**: Receipt totals match the finalised bill exactly. Re-printing later
  yields an identical receipt regardless of later menu/tax changes.

#### FR-17 — Release Table on Order Close
- **Actor(s)**: System.
- **Inputs/Validation**: Closure of a dine-in order (FR-15).
- **Business Rules**: BR-12. On close of a dine-in order the table transitions Occupied → Needs
  Cleaning automatically; then Free once staff mark it cleaned. Takeaway orders trigger no table
  change.
- **Acceptance Criteria**: Closing a dine-in order changes its table to Needs Cleaning without
  manual action; the table becomes available for a new order once marked Free.

### Module 4 — Inventory & Suppliers (FR-18 … FR-22)

#### FR-18 — Manage Stock Items
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Stock item name (required, unique, 2–80 chars). Unit of measure (required;
  e.g. kg, L, unit, bottle). Reorder level (required, number ≥ 0). Quantity on hand
  (system-maintained; opening value ≥ 0 on create).
- **Business Rules**: BR-21. Stock item names unique; quantity on hand is never edited directly to
  fabricate stock — it changes only via recorded deliveries (FR-21) or explicit logged adjustments.
  A stock item referenced by purchase-order history cannot be hard-deleted; it is deactivated.
- **Acceptance Criteria**: A new stock item appears in inventory with its unit and reorder level and
  participates in low-stock flagging. Duplicate name / negative reorder level → rejected.

#### FR-19 — Register & Maintain Suppliers
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Supplier name (required, unique, 2–100 chars). Contact person / phone /
  email (optional; format-validated when present). Address (optional, ≤ 255 chars). Status
  (Active/Inactive).
- **Business Rules**: BR-22. Supplier names unique. A supplier referenced by purchase orders cannot
  be deleted; it is set Inactive.
- **Acceptance Criteria**: A registered active supplier can be chosen on a new purchase order.
  Deleting a supplier with PO history is prevented.

#### FR-20 — Create Purchase Order
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Supplier (required; active). PO lines (≥ 1; each = stock item + quantity >
  0, optional unit cost). Expected date (optional; today or later).
- **Business Rules**: BR-23. A PO is created with a unique number, status Ordered, creating user, and
  timestamp. Creating a PO does not change stock on hand; stock increases only on receipt (FR-21). A
  PO must contain at least one valid line.
- **Acceptance Criteria**: A valid PO is saved with status Ordered and does not alter stock on hand.
  No lines / quantity ≤ 0 → rejected.

#### FR-21 — Receive Delivery & Increase Stock
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: PO reference (required; an Ordered/partially-received PO). Received
  quantities per line (0 ≤ received ≤ ordered; partial receipts allowed).
- **Business Rules**: BR-24; NFR-03. On receipt, each stock item's on-hand increases by the received
  quantity in a single transaction. PO status updates to Received (all lines fulfilled) or Partially
  Received. Receipt recomputes reorder flags (FR-22).
- **Acceptance Criteria**: Receiving 10 units raises on-hand by exactly 10. A failed receipt leaves
  stock and PO status unchanged. Received > ordered → rejected.

#### FR-22 — Flag Low Stock
- **Actor(s)**: System (surfaced to Manager/Administrator).
- **Inputs/Validation**: Stock quantity changes or inventory view.
- **Business Rules**: BR-25. An item is flagged when quantity on hand ≤ reorder level. Flagged items
  are highlighted in inventory and listed in the inventory report (FR-28).
- **Acceptance Criteria**: An item at or below its reorder level is flagged; receiving stock above
  the level clears the flag.

### Module 5 — Staff & Reservations (FR-23 … FR-26)

#### FR-23 — Manage Staff Records
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Name (required, 2–100 chars). Role/position (required; e.g. Cashier,
  Waiter, Chef). Contact (phone/email, format-validated). Status (Active/Inactive).
- **Business Rules**: BR-26. Staff records are distinct from system user accounts (FR-03); a staff
  record may or may not have a login. A staff record linked to historical activity is deactivated,
  not deleted.
- **Acceptance Criteria**: A new staff record is stored and appears in the roster; deactivation
  hides them from active lists but keeps history.

#### FR-24 — Create Reservation
- **Actor(s)**: Cashier, Manager, Administrator.
- **Inputs/Validation**: Customer name (required, 2–100 chars). Contact (required; phone/email,
  format-validated). Date & time (required; future, valid service time). Party size (required,
  integer ≥ 1). Table (required; existing).
- **Business Rules**: BR-27, BR-28. Party size should not exceed table capacity; if it does, warn
  and possibly require override or a larger table. The chosen table/time must not overlap an
  existing reservation (FR-26). A confirmed reservation sets the table Reserved for the service
  window. New reservations start as Booked.
- **Acceptance Criteria**: A valid future reservation on a free time-slot is saved and marks the
  table Reserved. Past date/time → rejected. Overlap → rejected. Party > capacity → warning/override.

#### FR-25 — Reservation Lifecycle (Seat / Complete / Cancel)
- **Actor(s)**: Cashier, Manager, Administrator.
- **Inputs/Validation**: Lifecycle action on an existing reservation.
- **Business Rules**: BR-29; Appendix B state model. Booked → Seated (guest arrives; table becomes
  Occupied, an order may be opened), Seated → Completed (visit ends), or Booked/Seated → Cancelled;
  an optional No-Show state may close out un-arrived bookings. Cancelling or completing frees the
  table's reserved hold. Only defined transitions permitted.
- **Acceptance Criteria**: Seating a reservation frees the reserved hold and marks the table
  Occupied; cancelling frees the slot for rebooking.

#### FR-26 — Prevent Double-Booking
- **Actor(s)**: System.
- **Inputs/Validation**: A reservation being created or rescheduled.
- **Business Rules**: BR-27. Before saving, the system checks the target table for any active
  (Booked/Seated) reservation whose time window overlaps the requested window (default slot duration
  configurable). Overlaps are rejected; alternatives may be suggested. Cancelled/completed
  reservations do not block new bookings.
- **Acceptance Criteria**: Two overlapping reservations on the same table cannot both exist.
  Back-to-back non-overlapping bookings are allowed.

### Module 6 — Reporting (FR-27 … FR-30)

#### FR-27 — Sales Report
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Date range (required; start ≤ end; valid dates). Filters (optional; order
  type, payment method, cashier, category).
- **Business Rules**: BR-30. Aggregates only finalised orders within the range: total sales, order
  count, average order value, tax collected, discounts given, and a per-item/per-category
  quantity-and-revenue breakdown. Figures derive from stored finalised order data (never current
  menu prices).
- **Acceptance Criteria**: Totals reconcile to the sum of finalised orders in the range; item
  breakdown matches the underlying orders. start > end → rejected. No data → empty report with a
  clear note.

#### FR-28 — Inventory / Stock Report
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Optional filter "below reorder level only".
- **Business Rules**: BR-25. Lists every active stock item with unit, quantity on hand, reorder
  level, and a low-stock indicator (quantity ≤ reorder level).
- **Acceptance Criteria**: Every item at/below its reorder level is listed as low; quantities match
  live stock on hand at generation time.

#### FR-29 — Staff Activity Report
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: Date range.
- **Business Rules**: BR-30 (finalised data); FR-02 (RBAC). Summarises, per user/cashier:
  login/logout activity, number of orders processed, total sales handled, and discounts applied —
  derived from session events (FR-01/FR-04) and finalised orders. Only Manager/Administrator may
  view it.
- **Acceptance Criteria**: Order counts and sales totals per cashier reconcile to finalised orders
  in the range.

#### FR-30 — Export Reports
- **Actor(s)**: Manager, Administrator.
- **Inputs/Validation**: A displayed report (FR-27…FR-29).
- **Business Rules**: The export reproduces the on-screen report exactly, with title, generated-by
  user, generation timestamp, and the applied date range/filters in the header.
- **Acceptance Criteria**: The exported document matches the displayed report and records its
  parameters.

### Business Rules Catalogue (BR-01 … BR-31)

These cross-cutting constraints are enforced consistently across the functions above (and in the
business layer per the constitution).

| ID | Rule | Enforced by |
|---|---|---|
| BR-01 | No function is accessible without a valid authenticated session. | FR-01, FR-02 |
| BR-02 | Passwords are stored only as salted one-way hashes; never plain text in the store, logs, or UI. | FR-01, FR-03; NFR-04 |
| BR-03 | Every function checks the caller's role in the business layer, independent of the UI. | FR-02 |
| BR-04 | Usernames are unique across the system. | FR-03 |
| BR-05 | Records with historical references are deactivated, not deleted. | FR-03, FR-06, FR-18, FR-19, FR-23 |
| BR-06 | At least one active Administrator must always exist. | FR-03 |
| BR-07 | Logout with an unsaved open order requires finalise/park/discard first. | FR-04 |
| BR-08 | Menu category names are unique. | FR-05 |
| BR-09 | Price changes apply to future orders only; finalised bills are never retro-priced. | FR-06, FR-14 |
| BR-10 | Items with order history are made Unavailable rather than deleted. | FR-06, FR-07 |
| BR-11 | Table labels are unique. | FR-08 |
| BR-12 | Table status follows the defined state model; illegal transitions are rejected. | FR-09, FR-17 |
| BR-13 | line total = unit-price snapshot × quantity; subtotal = Σ line totals. | FR-12 |
| BR-14 | Each order has a unique number, creating user, and timestamp. | FR-10 |
| BR-15 | Order lines capture the unit price at time of ordering (price snapshot). | FR-11 |
| BR-16 | Monetary values use two-decimal precision with one consistent rounding step. | FR-12, FR-14 |
| BR-17 | Discount applies to subtotal before tax; result can never be negative. | FR-13 |
| BR-18 | tax = (subtotal − discount) × tax_rate; total = (subtotal − discount) + tax; tax rate stored on the order. | FR-14 |
| BR-19 | Finalisation is atomic: payment + status + stored figures commit together or not at all. | FR-15; NFR-03 |
| BR-20 | Receipts reproduce stored finalised figures; re-prints are identical. | FR-16 |
| BR-21 | Stock item names are unique; on-hand changes only via receipts/logged adjustments. | FR-18, FR-21 |
| BR-22 | Supplier names are unique. | FR-19 |
| BR-23 | A PO has a unique number and ≥ 1 valid line; creating it does not change stock. | FR-20 |
| BR-24 | Receiving a delivery increases on-hand by received qty within a transaction. | FR-21 |
| BR-25 | An item is low when quantity on hand ≤ reorder level. | FR-22, FR-28 |
| BR-26 | Staff records are distinct from user accounts; deactivate to retain history. | FR-23 |
| BR-27 | No two active reservations may overlap on the same table. | FR-24, FR-26 |
| BR-28 | Party size exceeding table capacity triggers a warning/override. | FR-24 |
| BR-29 | Reservation lifecycle follows the defined state model and frees table holds on complete/cancel. | FR-25 |
| BR-30 | Reports aggregate only finalised data over the selected range/filters. | FR-27, FR-29 |
| BR-31 | Reference/system data (tax rate, payment methods, system constants) is Administrator-configurable only; every change is audited (who/when) and the tax rate in force is snapshotted onto each finalised order. | FR-31, FR-14 |

### Non-Functional Requirements (NFR-01 … NFR-08)

| ID | Category | Requirement & measurable target |
|---|---|---|
| NFR-01 | Usability | Front-line ordering/billing screens are learnable by a cashier in a single short training session, with minimal clicks for common actions. Target: a trained cashier completes an order-to-receipt cycle without a manual. |
| NFR-02 | Performance | Common actions (open an order, add an item, generate a bill) respond within ≈2 seconds under normal single-restaurant load. |
| NFR-03 | Reliability & Data Integrity | Constraints and transactions keep financial and stock data consistent; a failed operation leaves no partial data. |
| NFR-04 | Security | Passwords stored hashed (not plain text); access enforced by role; financial and personal data not exposed to unauthorised roles. |
| NFR-05 | Maintainability | Presentation, business logic, and data are separated so the interface can be replaced (desktop → web) without rewriting business logic. |
| NFR-06 | Portability / Migration | Business logic and data access are kept independent of the desktop UI so they can be reused by the future web interface on the same shared database. |
| NFR-07 | Data Persistence | All operational data is stored in a relational database normalised to at least third normal form (3NF), shared across both phases. |
| NFR-08 | Availability | For the desktop phase, the system functions on the restaurant's local machine/network without requiring internet access. |

### Key Entities

- **User Account** — login identity: username, password hash, role, status. Holds one role;
  creates orders, purchase orders, reservations, stock movements; generates login events.
- **Role** — Administrator / Manager / Cashier; governs permissions.
- **Staff** — employee record: name, position, contact, status. Optionally linked to a user
  account; distinct from it.
- **Login Event** — session audit entry (login/logout, user, time); source for the staff-activity
  report.
- **Menu Category** — grouping: name, display order; has many menu items.
- **Menu Item** — sellable item: name, price, availability, description; belongs to a category;
  referenced by order lines.
- **Dining Table** — label, capacity, live status (Free/Occupied/Reserved/Needs Cleaning);
  referenced by orders and reservations.
- **Order** — header: number, type, status, timestamps, and stored final figures (subtotal,
  discount type/value/amount, tax rate, tax, total); belongs to a table for dine-in; has many order
  items and one payment.
- **Order Item** — line: menu item, quantity, unit-price snapshot, line total.
- **Payment** — method, amount, tendered/change, timestamp; recorded (not processed); one per
  finalised order.
- **Payment Method** — reference list (Cash, Card, Other).
- **Stock Item** — name, unit of measure, reorder level, quantity on hand, status.
- **Stock Movement** — immutable ledger entry: item, type (receipt/adjustment), signed quantity
  change, timestamp, user; on-hand is the running sum of these.
- **Supplier** — name, contact, status; has many purchase orders.
- **Purchase Order** — header: number, supplier, status, dates, creating user.
- **Purchase Order Item** — line: stock item, ordered qty, received qty, optional unit cost.
- **Reservation** — customer name/contact, date-time, duration, party size, table, status
  (Booked/Seated/Completed/Cancelled/No-Show), creating user.
- **Reference / System Data** — tax rate, payment methods, and other configurable constants (e.g.
  idle timeout, login-attempt limit, reservation slot, discount-approval threshold) administered
  via FR-31 (Administrator-only, audited) and consumed by billing and validation.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A trained cashier completes a full order-to-receipt cycle (open order → add items →
  discount → tax/total → payment → receipt → table released) without manual arithmetic and without
  consulting a manual.
- **SC-002**: 100% of bills are arithmetically correct — subtotal, discount, tax, and total match
  the defined worked examples and edge cases every time (e.g. 100.00 subtotal, 10% discount, 10%
  tax → 99.00 total).
- **SC-003**: Common actions (open an order, add an item, generate a bill) complete within about 2
  seconds under normal single-restaurant load.
- **SC-004**: 0 finalised bills change after later menu-price or tax-rate edits (finalised figures
  are immutable and reprints are identical).
- **SC-005**: 0 partial writes occur on failure — a failed finalisation or stock receipt leaves no
  orphaned payment or altered stock.
- **SC-006**: 100% of role-restricted functions are denied to unauthorised roles, including attempts
  that bypass the interface; and no password is ever stored, logged, or displayed in readable form.
- **SC-007**: Management can generate sales, inventory, and staff-activity reports on demand whose
  totals reconcile exactly to the underlying finalised data, and export them with parameters
  recorded.
- **SC-008**: Reservations cannot double-book a table for overlapping times, and low-stock items are
  flagged whenever on-hand is at or below the reorder level.
- **SC-009**: The system operates on the restaurant's local machine/network with no internet
  connection required.
- **SC-010**: Reference/system data (tax rate, payment methods, and system constants) is
  changeable only by an Administrator, every change is recorded with the changing user and
  timestamp, and no such change alters any previously finalised bill.

---

## Assumptions

- **Scope is fixed to FR-01 … FR-31.** The BRD out-of-scope list is excluded from this phase:
  online customer ordering/delivery, third-party payment-gateway/card processing, kitchen display
  screens, loyalty/rewards, accounting/payroll integration, multi-branch consolidation, and a
  native mobile app.
- **Single location.** The restaurant operates as one location for the initial phases.
- **Payment is recorded, not processed.** No card-gateway integration; the system records how the
  customer paid.
- **Two delivery phases share one database.** Phase 1 is an on-premise desktop application; Phase 2
  re-uses the same business logic and data behind a web interface. This spec describes behaviour
  common to both; it does not prescribe the interface technology.
- **Reference data provided at setup.** Menu, tax rate, and other reference/system data are
  configured by the restaurant before billing (governed by **FR-31**, Administrator-only); the
  tax rate is a single configurable value.
- **Currency.** A single currency is used and configured at setup; amounts are shown to two
  decimals. (No multi-currency handling in scope.)
- **Configurable defaults.** Login throttle after 5 consecutive failures; idle-logout after 15
  minutes; reservation slot duration configurable — all adjustable by an Administrator.
- **Staff records vs. user accounts are separate.** Not every staff member has a login; not every
  login corresponds to a staff record.
- **Order voiding.** The order lifecycle allows an Open order to be cancelled/voided before payment;
  a finalised order is never edited or deleted (only reprinted).
- **Basic computer familiarity.** Cashier-role staff can operate a simple point-of-sale interface.
- **Single payment per order (FR-15).** Confirmed out of scope for v1: each order is settled by
  exactly one payment; split billing / multiple payments per order is deferred to a future phase.
- **Discount approval (FR-13).** Confirmed in scope: discounts above a configurable threshold
  require Manager/Administrator authorisation, recorded for audit; the specific threshold value is
  reference/system data configured at setup by an Administrator.

## Resolved Decisions

Items that were undecided in the source documents and have been resolved for v1:

1. **Split billing (FR-15)** → **Out of scope.** One payment per order; the BRD "split a bill"
   reference is deferred to a future phase.
2. **Discount approval policy (FR-13)** → **In scope.** Manager/Administrator authorisation is
   required for discounts above a configurable threshold; the threshold is admin-configured
   reference data.
3. **Reference/system-data configuration** → **Promoted to a first-class requirement (FR-31).**
   Previously only implied (the Role–Permission Matrix "Configure reference/system data" row and
   FR-14's consumed tax rate), configuration of the tax rate, payment methods, and system
   constants is now specified as **FR-31** (Administrator-only, audited), closing the
   traceability gap in which the System Config screen traced to no functional requirement. This
   expands the fixed scope from FR-01…FR-30 to **FR-01…FR-31** and is reflected in the
   constitution (v2.0.0), plan, and tasks.
