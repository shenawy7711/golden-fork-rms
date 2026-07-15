# Functional Requirements Document (FRD)
## Restaurant Management System (RMS)

**Document:** Functional Requirements Document (FRD)  **Version:** 1.0 (Draft for review)
**Date:** 7 July 2026  **Prepared by:** Project Team  **Classification:** Confidential
**Traces to:** BRD v1.0 (RMS)  **Phase:** Phase 1 (Desktop) → Phase 2 (Web)

> Detailed functional specification derived from the approved Business Requirements Document (BRD v1.0).

---

## Document Control

### Revision History

| Version | Date | Author | Description of change |
|---|---|---|---|
| 0.1 | 5 Jul 2026 | Project Team | Initial skeleton and section outline. |
| 0.9 | 6 Jul 2026 | Project Team | Full functional specifications drafted for all six modules. |
| 1.0 | 7 Jul 2026 | Project Team | Complete draft released for stakeholder review; traceability matrix added. |
| 1.1 | 15 Jul 2026 | Project Team | Added **FR-31 — Configure Reference / System Data** (Administrator-only, audited) and **BR-31**, closing the gap where reference/system-data configuration (§2.4 permission row, FR-14's consumed tax rate) had no owning FR. Scope extended FR-01…FR-30 → FR-01…FR-31. Aligns with Constitution v2.0.0. |

### Reviewers & Approvers

| Role | Name | Responsibility | Signature / Date |
|---|---|---|---|
| Project Sponsor / Owner | | Approves scope and business fit | |
| Restaurant Manager (Business Owner of requirements) | | Confirms functional correctness | |
| Development Lead | | Confirms technical feasibility | |
| QA / Test Lead | | Confirms testability of acceptance criteria | |

### Related Documents

| Ref | Document | Relationship |
|---|---|---|
| REF-1 | Business Requirements Document (BRD) — RMS v1.0 | Parent document; source of business objectives and FR-01…FR-30 (FR-31 added by this FRD's v1.1 amendment). |
| REF-2 | Development Plan (companion) | Captures technology and design decisions (JavaFX, MySQL, MVC). |
| REF-3 | Conceptual & Logical Database Design | Detailed entity attributes and 3NF schema (design phase). |
| REF-4 | Test Plan & Traceability Matrix | Maps each FR to test cases for verification. |

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [System Overview](#2-system-overview)
3. [Functional Requirements Specification](#3-functional-requirements-specification)
4. [Key Use Cases & Process Flows](#4-key-use-cases--process-flows)
5. [Data Requirements & State Models](#5-data-requirements--state-models)
6. [Business Rules Catalogue](#6-business-rules-catalogue)
7. [Non-Functional Requirements](#7-non-functional-requirements)
8. [Assumptions, Dependencies & Constraints](#8-assumptions-dependencies--constraints)
9. [Requirements Traceability Matrix](#9-requirements-traceability-matrix)
10. [Acceptance & Success Criteria](#10-acceptance--success-criteria)
- [Appendix A — Field Validation Reference](#appendix-a--field-validation-reference)
- [Appendix B — Status & Enumeration Reference](#appendix-b--status--enumeration-reference)

---

## 1. Introduction

### 1.1 Purpose of this Document

This Functional Requirements Document (FRD) translates the business needs expressed in the approved Business Requirements Document (BRD v1.0) into precise, testable functional specifications for the Restaurant Management System (RMS). Where the BRD answers *what* the business needs and *why*, this FRD answers *how the system must behave* to satisfy those needs — the inputs it accepts, the rules it applies, the outputs it produces, the validations it enforces, and the conditions under which each function succeeds or fails.

Every functional requirement identifier introduced in the BRD (FR-01 through FR-30) is carried forward unchanged and expanded here into a full specification; **FR-31 (Configure Reference / System Data)** was subsequently added by amendment (v1.1) to give the Administrator-only reference/system-data configuration — previously only implied by the §2.4 permission matrix and FR-14 — an explicit, traceable requirement. This preserves end-to-end traceability from business objective → functional requirement → use case → acceptance criterion → test case. This document deliberately avoids prescribing internal implementation detail (class design, SQL, screen pixel layout); those belong to the Development Plan and Database Design (REF-2, REF-3).

### 1.2 Scope of the System

The RMS automates the core day-to-day operations of a single-location restaurant across six functional modules: **Authentication & Administration**, **Menu & Table Management**, **Orders & Billing (POS)**, **Inventory & Suppliers**, **Staff & Reservations**, and **Reporting**. It is delivered in two phases against one shared MySQL database: Phase 1 is an on-premise desktop application (JavaFX), and Phase 2 re-uses the same business logic and data layer behind a web interface.

**Out of scope** for the current phases (and therefore not specified here): online customer-facing ordering and delivery, third-party payment gateway / card processing, kitchen display screens, loyalty / rewards programs, accounting or payroll integration, multi-branch consolidation, and a native mobile app. These may be considered as future phases.

### 1.3 Intended Audience

| Audience | How they use this document |
|---|---|
| Development Team | Primary specification for building each function correctly. |
| QA / Test Lead | Source of testable acceptance criteria and validation rules. |
| Restaurant Manager / Owner | Confirmation that the system will support real operational workflows. |
| Project Sponsor | Verification that scope aligns with agreed business objectives. |

### 1.4 Definitions, Acronyms & Abbreviations

| Term | Meaning |
|---|---|
| RMS | Restaurant Management System — the software specified here. |
| POS | Point of Sale — the ordering and billing module. |
| BRD / FRD | Business / Functional Requirements Document. |
| BO-n | Business Objective identifier from the BRD (BO-1…BO-6). |
| FR-nn / NFR-nn | Functional / Non-Functional Requirement identifier. |
| BR-nn | Business Rule identifier defined in §6 of this FRD. |
| Order line / Order Item | A single menu item plus quantity within an order. |
| Reorder level | The stock threshold at or below which an item is flagged for re-purchase. |
| MVC | Model-View-Controller architectural pattern. |
| 3NF | Third Normal Form (relational normalisation target). |
| RBAC | Role-Based Access Control. |

### 1.5 Document Conventions

Each functional requirement is presented as a self-contained specification containing the fields below. The keyword **shall** denotes a mandatory requirement.

- **Description** — the behaviour the system must exhibit.
- **Priority** — *High* = must-have for launch; *Medium* = important; *Low* = desirable.
- **Actors** — roles permitted to invoke the function.
- **Trigger / Pre-conditions** — what starts the function and what must already be true.
- **Inputs** — data the function consumes, with field-level validation.
- **Processing & Business Rules** — the logic the system applies.
- **Outputs / Post-conditions** — results produced and the resulting system state.
- **Exceptions** — error and alternate-flow handling.
- **Acceptance Criteria** — objective, testable pass conditions.
- **Traceability** — business objective(s) and business rule(s) satisfied.

---

## 2. System Overview

### 2.1 System Context

The RMS is the single source of truth for menu, pricing, orders, payments, tables, stock, suppliers, staff, and reservations. Human actors (Administrator, Manager, Cashier) interact through authenticated sessions. Two external parties are represented as data only: *Suppliers*, whose goods are recorded through purchase orders, and *Customers*, whose orders and reservations are recorded but who do not log in. Payment is **recorded** in the system but **processed outside** it (no card-gateway integration in scope).

### 2.2 Architecture & Phasing

The system is built on the Model-View-Controller pattern with an explicit service/data layer that is independent of the desktop UI framework, so the same business logic and MySQL database serve both phases. This is a functional constraint because it shapes where behaviour lives: all calculation, validation, and state rules specified in §3 must reside in the reusable business layer, not in screen code.

| Phase | Interface | Data / Logic | Access |
|---|---|---|---|
| Phase 1 | JavaFX desktop application, on-premise | Shared business layer + MySQL | Local machine / LAN, no internet required |
| Phase 2 | Browser-based web application | Same business layer + same MySQL schema | More devices; groundwork for multi-location |

### 2.3 Actors

| Actor | Type | Description |
|---|---|---|
| Administrator | Primary | Full control: user accounts, roles, reference/system data, and every module and report. Typically the owner or IT-responsible person. |
| Manager | Primary | Operational control: menu & prices, tables, inventory & suppliers, reservations, staff records, and all reports. Cannot manage system-level user accounts unless also granted Administrator rights. |
| Cashier | Primary | Front-line: creates/manages orders, produces bills & receipts, records payments, views menu & table status, records reservations. Cannot change prices, manage stock, or view management reports. |
| Supplier | External (data) | Provides stock recorded through purchase orders; does not use the system. |
| Customer | External (data) | Experiences service; may have a reservation recorded. Does not use the system. |

### 2.4 Role–Permission Matrix

Access to every function is governed by role (RBAC). A dash means the function is denied to that role. Permissions are cumulative: an Administrator implicitly holds all Manager and Cashier permissions.

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

> The matrix above is the authoritative interpretation of BRD §6. Every FR in §3 lists its permitted actors; those lists must remain consistent with this matrix and are enforced by **FR-02**.

---

## 3. Functional Requirements Specification

This section expands each BRD functional requirement into a full specification. Requirements are grouped by module. Field-level input validation referenced here is consolidated in Appendix A; status values are enumerated in Appendix B.

### 3.1 Authentication & Administration (FR-01 … FR-04, FR-31)

#### FR-01 — User Login & Authentication
- **Module:** Admin | **Priority:** High | **Actors:** All roles (Administrator, Manager, Cashier)
- **Trigger:** User opens the application / an unauthenticated action is attempted. | **Traces:** BO-5; NFR-04; BR-01, BR-02

**Description.** The system shall require every user to authenticate with a username and password before any other function is accessible. No module, screen, or report is reachable without a valid, active session.

**Pre-conditions.** The user has an account that exists and is *active* (not deactivated). The application is running and can reach the database.

**Inputs.**

| Field | Validation |
|---|---|
| Username | Required. 3–50 chars. Case-insensitive match against stored accounts. |
| Password | Required. Entered masked. Compared against the stored one-way hash (never stored or logged in plain text). |

**Processing & Business Rules.**
- Credentials are validated against the account store; the password is verified by comparing salted hashes (BR-02).
- On success the system creates an authenticated session bound to the user's role and records the login event (used by the staff activity report, FR-29).
- Deactivated accounts shall be refused even with correct credentials.
- After a configurable number of consecutive failures (default 5) the login is temporarily throttled to deter brute force.

**Outputs / Post-conditions.** A valid session exists; the user lands on the role-appropriate home screen; the available menu reflects the user's permissions (FR-02).

**Exceptions.**
- Unknown username *or* wrong password → generic message "Invalid username or password" (does not disclose which was wrong).
- Deactivated account → "This account is inactive. Contact an administrator."
- Database unreachable → "Unable to sign in — service unavailable"; no session is created.

**Acceptance Criteria.**
- Given valid credentials for an active account, the user is authenticated and routed to their home screen.
- Given any invalid credential, access is denied with the generic message and no session is created.
- Passwords never appear in the database, logs, or screens in readable form.

---

#### FR-02 — Role-Based Access Control
- **Module:** Admin | **Priority:** High | **Actors:** System (enforced for every authenticated user)
- **Trigger:** Any attempt to open a screen or invoke a function. | **Traces:** BO-5; NFR-04; BR-03; §2.4 matrix

**Description.** The system shall restrict access to every function based on the logged-in user's role, per the Role–Permission Matrix (§2.4). Denied functions shall be hidden or disabled in the interface *and* re-checked on execution (defence in depth), so access cannot be gained by bypassing the UI.

**Processing & Business Rules.**
- Every function is tagged with the roles permitted to invoke it; the business layer verifies the caller's role before executing (BR-03).
- Permissions are cumulative — Administrator ⊇ Manager permissions where stated; Manager and Cashier hold only their listed permissions.
- Cashiers shall not access price changes, stock management, or management reports.

**Outputs / Post-conditions.** Permitted actions proceed; unauthorised actions are blocked and logged.

**Exceptions.** Unauthorised invocation → action refused with "You do not have permission to perform this action"; the attempt is recorded.

**Acceptance Criteria.**
- A Cashier cannot reach price-edit, stock, or report functions by any path in the UI.
- A Manager can reach all operational functions but not user-account management (unless also an Administrator).
- Server/business-layer checks reject a role-forbidden call even if the UI control were bypassed.

---

#### FR-03 — Manage User Accounts & Roles
- **Module:** Admin | **Priority:** High | **Actors:** Administrator
- **Trigger:** Administrator opens User Management. | **Traces:** BO-5; NFR-04; BR-04, BR-05

**Description.** An Administrator shall be able to create, edit, deactivate, and delete user accounts and assign each account exactly one role (Administrator, Manager, or Cashier).

**Inputs.**

| Field | Validation |
|---|---|
| Username | Required, unique, 3–50 chars. |
| Full name | Required, 2–100 chars. |
| Role | Required; one of Administrator / Manager / Cashier. |
| Initial password | Required on create; meets password policy (Appendix A); stored hashed. |
| Status | Active / Inactive (default Active). |

**Processing & Business Rules.**
- Usernames are unique across the system (BR-04); duplicates are rejected.
- **Deactivate** preserves the account and its historical links (orders, logins) but blocks login. **Delete** is permitted only when the account has no dependent historical records; otherwise the system requires deactivation instead (BR-05, referential integrity).
- The system shall prevent removing or deactivating the last remaining active Administrator (BR-06).
- Role changes take effect on the user's next login.

**Outputs / Post-conditions.** The account list reflects the change; affected user's permissions update per FR-02.

**Exceptions.**
- Duplicate username → "Username already exists."
- Delete blocked by history → offer to deactivate instead.
- Attempt to remove last Administrator → refused with explanation.

**Acceptance Criteria.**
- A new account with a valid unique username and role can log in immediately.
- A deactivated account cannot log in but its past records remain intact.
- The system never allows zero active Administrators.

---

#### FR-04 — Logout & Session Termination
- **Module:** Admin | **Priority:** High | **Actors:** All roles
- **Trigger:** User selects Log Out (or session times out). | **Traces:** NFR-04; BR-07

**Description.** The system shall allow a user to log out and shall end the session on logout, returning to the login screen and clearing in-memory user context.

**Processing & Business Rules.**
- On logout the session is invalidated and the logout event is recorded (feeds FR-29).
- If an order is open and unsaved at logout, the system warns and requires the user to finalise, park, or discard it first (BR-07) so no partial financial data is orphaned (NFR-03).
- An optional idle timeout (default 15 min) triggers automatic logout to protect an unattended terminal.

**Outputs / Post-conditions.** No authenticated session remains; the login screen is displayed; protected functions are again inaccessible.

**Acceptance Criteria.**
- After logout, pressing Back or re-invoking a protected function requires re-authentication.
- Logout with an open order prompts for resolution before ending the session.

---

#### FR-31 — Configure Reference / System Data
- **Module:** Admin | **Priority:** High | **Actors:** Administrator (only)
- **Trigger:** Initial system commissioning, or a change to a system-wide value (e.g. a new tax rate). | **Traces:** BO-2, BO-5; NFR-04; BR-31, BR-03, BR-09, BR-18; §2.4 matrix

**Description.** An Administrator shall be able to configure the reference/system data on which the rest of the system depends — the **tax rate**, the **payment-method reference list**, and system constants (**idle timeout**, **login-attempt limit**, **reservation slot duration**, **discount-approval threshold**) — so billing and validation are correct from day one. This function is exclusive to the Administrator role.

**Pre-conditions.** The caller is authenticated with an active Administrator account.

**Inputs.**

| Field | Validation |
|---|---|
| Tax rate | Required; decimal ≥ 0 and ≤ 1.0000 (0–100%), stored to four decimals. |
| Payment methods | Reference list; each name required, unique, 2–20 chars; activated/deactivated rather than hard-deleted once referenced by a payment. |
| Idle timeout (minutes) | Integer ≥ 0; default 15 (0 disables auto-logout). |
| Login max attempts | Integer ≥ 1; default 5. |
| Reservation slot (minutes) | Integer ≥ 1; default 90. |
| Discount approval threshold | A fixed monetary amount in the configured currency (decimal ≥ 0) — the discount amount above which a discount requires Manager/Administrator authorisation (FR-13). |

**Processing & Business Rules.**
- Configuration is Administrator-only; the business layer verifies the role before any read or write (BR-03), independent of the interface.
- Each change is persisted with the changing administrator and a timestamp for audit (BR-31).
- Billing (FR-14) and validation consume the **current** values. The tax rate in force at finalisation is snapshotted onto each order (BR-18); changing the rate later never alters a finalised bill (BR-09).
- Reference data (a tax rate and at least one active payment method) must exist before an order can be finalised (dependency for FR-14/FR-15).

**Outputs / Post-conditions.** Reference data is configured and available to billing and validation; every change is recorded (who/when).

**Exceptions.**
- Non-Administrator attempt → refused in the business layer with "You do not have permission to perform this action" (FR-02); the attempt is recorded.
- Invalid value (e.g. tax rate outside 0–1, non-positive slot) → rejected with a field-specific message.
- Finalisation attempted before a tax rate / payment method exists → blocked (dependency).

**Acceptance Criteria.**
- Only an Administrator can view or change reference/system data; a role-forbidden attempt is rejected even if the UI control were bypassed.
- Changing the reference tax rate does not alter any previously finalised bill.
- Every change records the changing administrator and a timestamp.

---

### 3.2 Menu & Table Management (FR-05 … FR-09)

#### FR-05 — Manage Menu Categories
- **Module:** Menu | **Priority:** High | **Actors:** Manager, Administrator
- **Trigger:** Manager opens Menu → Categories. | **Traces:** BO-1, BO-5; BR-08

**Description.** A Manager shall be able to create, edit, and delete menu categories (e.g., Starters, Mains, Beverages) used to organise menu items.

**Inputs.**

| Field | Validation |
|---|---|
| Category name | Required, unique, 2–50 chars. |
| Display order | Optional integer controlling on-screen ordering. |

**Processing & Business Rules.**
- Category names are unique (BR-08).
- A category that still contains menu items cannot be hard-deleted; the system requires the items to be reassigned or removed first (referential integrity).
- Changes appear immediately in the ordering screens (menu-maintenance flow).

**Outputs / Post-conditions.** Updated category list; ordering screens reflect the change.

**Exceptions.** Duplicate name → rejected. Delete of non-empty category → blocked with guidance.

**Acceptance Criteria.**
- A newly created category is immediately selectable when adding a menu item and when ordering.
- Deleting a non-empty category is prevented.

---

#### FR-06 — Manage Menu Items
- **Module:** Menu | **Priority:** High | **Actors:** Manager, Administrator
- **Trigger:** Manager opens Menu → Items. | **Traces:** BO-1, BO-2, BO-5; BR-09, BR-10

**Description.** A Manager shall be able to create, edit, and delete menu items, each with a name, category, price, and availability status.

**Inputs.**

| Field | Validation |
|---|---|
| Item name | Required, 2–80 chars, unique within its category. |
| Category | Required; existing category (FR-05). |
| Price | Required; decimal ≥ 0.00, two decimal places, within currency limits. |
| Availability | Available / Unavailable (default Available). |
| Description (opt.) | Up to 255 chars. |

**Processing & Business Rules.**
- Price must be non-negative and is stored to two decimals; price changes apply only to *future* orders — bills already finalised are never retro-priced (BR-09).
- An item referenced by any historical order shall not be hard-deleted; it is instead marked Unavailable/discontinued to preserve order history (BR-10; see FR-07).
- Menu changes are reflected immediately in the ordering screens.

**Outputs / Post-conditions.** Item catalogue updated; ordering screens show current items and prices.

**Exceptions.** Negative/invalid price → rejected. Delete of item with order history → converted to "mark unavailable".

**Acceptance Criteria.**
- A new available item with a valid price is orderable immediately at that price.
- Editing a price does not alter any previously finalised bill.

---

#### FR-07 — Toggle Menu Item Availability
- **Module:** Menu | **Priority:** Medium | **Actors:** Manager, Administrator
- **Trigger:** Item runs out / returns (e.g., "86" an item). | **Traces:** BO-1; BR-10

**Description.** The system shall allow a menu item to be marked available or unavailable without deleting it, so seasonal or sold-out items can be hidden from ordering yet retained for history and later reactivation.

**Processing & Business Rules.**
- Unavailable items are not selectable when building a new order but remain visible (greyed/flagged) in menu administration.
- Marking unavailable does not affect items already on open orders.

**Outputs / Post-conditions.** Ordering screens include only available items; the item can be re-enabled at any time.

**Acceptance Criteria.**
- An item toggled Unavailable disappears from the order-entry list but its record and history persist.
- Re-enabling makes it immediately orderable again.

---

#### FR-08 — Define Dining Tables
- **Module:** Tables | **Priority:** High | **Actors:** Manager, Administrator
- **Trigger:** Manager configures the dining room. | **Traces:** BO-1, BO-5; BR-11

**Description.** A Manager shall be able to define dining tables, each with a unique number/label and a seating capacity.

**Inputs.**

| Field | Validation |
|---|---|
| Table number/label | Required, unique, 1–10 chars. |
| Seating capacity | Required, integer ≥ 1. |

**Processing & Business Rules.**
- Table labels are unique (BR-11); capacity supports reservation party-size checks (FR-24).
- A table currently occupied or holding future reservations cannot be deleted until it is free and un-booked.

**Outputs / Post-conditions.** The floor/table list reflects the definition; new tables default to status `Free`.

**Acceptance Criteria.**
- A defined table appears for order and reservation selection with its capacity.
- Duplicate labels are rejected.

---

#### FR-09 — Display & Update Table Status
- **Module:** Tables | **Priority:** High | **Actors:** All roles (view/update per matrix)
- **Trigger:** Order/reservation events, or manual status change. | **Traces:** BO-1; BR-12; Appendix B

**Description.** The system shall display and update each table's status. Valid statuses are `Free`, `Occupied`, `Reserved`, and `Needs Cleaning`.

**Processing & Business Rules (state model — see Appendix B).**
- Opening a dine-in order on a Free/Reserved table sets it `Occupied`.
- Closing a dine-in order sets the table `Needs Cleaning` (FR-17); staff mark it `Free` when cleaned.
- A confirmed reservation for the current service sets/keeps the table `Reserved` until seated or cancelled (FR-25).
- Only defined transitions are allowed; illegal transitions are rejected (BR-12).

**Outputs / Post-conditions.** A live floor view shows current status per table, colour-coded, refreshed as events occur.

**Acceptance Criteria.**
- Table status visibly changes in response to order open/close and reservation events.
- An invalid manual transition is prevented.

---

### 3.3 Orders & Billing / POS (FR-10 … FR-17)

> **Financial-integrity note:** All monetary calculation for FR-12 to FR-16 shall be centralised in the business layer (one billing engine), computed in a single database transaction, and rounded consistently (BR-13, BR-16). This directly mitigates the BRD risk "Incorrect financial calculations" and satisfies BO-2.

#### FR-10 — Open a New Order
- **Module:** POS | **Priority:** High | **Actors:** Cashier, Manager, Administrator
- **Trigger:** A customer is seated or places a takeaway order. | **Traces:** BO-1; BR-14

**Description.** A Cashier shall be able to open a new order for a specific dine-in table *or* as takeaway.

**Inputs.**

| Field | Validation |
|---|---|
| Order type | Required; `Dine-in` or `Takeaway`. |
| Table | Required if Dine-in; must be a Free or Reserved (own) table. Omitted for Takeaway. |

**Processing & Business Rules.**
- A new order is created with a unique order number, status `Open`, timestamp, and the creating user (BR-14).
- For Dine-in, the selected table cannot already have an open order; on open, the table becomes `Occupied` (FR-09).

**Outputs / Post-conditions.** An open, empty order exists and is ready to receive items; the table (if any) is Occupied.

**Exceptions.** Table already has an open order → prevented, with option to open that existing order.

**Acceptance Criteria.**
- A dine-in order can only be opened on a table without an active order, and that table becomes Occupied.
- A takeaway order opens with no table.

---

#### FR-11 — Add / Remove Order Items
- **Module:** POS | **Priority:** High | **Actors:** Cashier, Manager, Administrator
- **Trigger:** Order is open; customer selects/changes items. | **Traces:** BO-1, BO-2; BR-15

**Description.** A Cashier shall be able to add available menu items with quantities to an open order and remove them before finalising.

**Inputs.**

| Field | Validation |
|---|---|
| Menu item | Required; must be currently *Available* (FR-07). |
| Quantity | Required; integer ≥ 1. |

**Processing & Business Rules.**
- Each order line captures item, quantity, and the unit price *at the time of ordering* (price snapshot — BR-15), protecting the bill from later menu price edits.
- Adding an item already on the order increments its quantity (or adds a new line, per configuration).
- Items may be added or removed only while the order status is `Open`; a finalised order is immutable.
- The order subtotal recomputes automatically on every change (FR-12).

**Outputs / Post-conditions.** The order reflects current lines and quantities with a live subtotal.

**Exceptions.** Quantity < 1 or non-integer → rejected. Adding an unavailable item → blocked. Editing a finalised order → blocked.

**Acceptance Criteria.**
- Adding/removing lines updates the subtotal immediately and correctly.
- Only available items can be added; finalised orders cannot be edited.

---

#### FR-12 — Automatic Subtotal Calculation
- **Module:** POS | **Priority:** High | **Actors:** System
- **Trigger:** Any change to order lines/quantities. | **Traces:** BO-2; BR-13

**Description.** The system shall calculate the order subtotal automatically from item prices and quantities — no manual arithmetic.

**Processing & Business Rules.**
- `line total = unit price (snapshot) × quantity`; `subtotal = Σ line totals` (BR-13).
- Rounding to two decimals is applied consistently (BR-16); computation happens in the business layer, not the UI.

**Outputs / Post-conditions.** A current subtotal displayed on the order and carried into the bill.

**Acceptance Criteria.**
- For any set of lines, the displayed subtotal equals Σ(price × qty) to two decimals.
- The subtotal updates within the performance target (NFR-02) on each edit.

---

#### FR-13 — Apply Discount
- **Module:** POS | **Priority:** Medium | **Actors:** Cashier, Manager, Administrator
- **Trigger:** A discount is agreed before finalising. | **Traces:** BO-2; BR-17

**Description.** The system shall support applying a discount to an order, as either a percentage of the subtotal or a fixed amount.

**Inputs.**

| Field | Validation |
|---|---|
| Discount type | Percentage or Fixed amount. |
| Discount value | Percentage 0–100; or fixed amount 0 ≤ value ≤ subtotal. |

**Processing & Business Rules.**
- Discount is applied to the subtotal *before* tax: `discountable = subtotal − discount` (BR-17). Tax (FR-14) is computed on the discounted amount.
- A discount can never make the discounted amount negative; percentage capped at 100, fixed capped at subtotal.
- Optional policy: discounts above a threshold may require Manager authorisation (configurable) — recorded for audit.

**Outputs / Post-conditions.** The bill shows subtotal, discount, and reduced pre-tax base.

**Exceptions.** Percentage > 100 or fixed > subtotal → rejected with correction prompt.

**Acceptance Criteria.**
- A 10% discount on a 100.00 subtotal yields a 90.00 pre-tax base.
- No discount can produce a negative total.

---

#### FR-14 — Automatic Tax & Final Total
- **Module:** POS | **Priority:** High | **Actors:** System
- **Trigger:** Bill is requested / order is being finalised. | **Traces:** BO-2; BR-18, BR-16

**Description.** The system shall calculate tax and the final total automatically using the configured tax rate (reference data set by Administrator).

**Processing & Business Rules.**
- `tax = round((subtotal − discount) × tax_rate)`; `total = (subtotal − discount) + tax` (BR-18).
- The tax rate is a single configurable reference value; the rate in force at finalisation is stored on the order for audit and accurate reprinting (BR-18).
- Rounding to two decimals is applied once, consistently (BR-16).

**Outputs / Post-conditions.** The bill displays subtotal, discount, tax, and grand total.

**Acceptance Criteria.**
- With a 10% tax rate, a 90.00 pre-tax base yields tax 9.00 and total 99.00.
- Changing the reference tax rate does not alter any previously finalised bill.

---

#### FR-15 — Record Payment & Finalise Order
- **Module:** POS | **Priority:** High | **Actors:** Cashier, Manager, Administrator
- **Trigger:** Customer pays. | **Traces:** BO-1, BO-2; BR-19

**Description.** A Cashier shall be able to record the payment method and finalise (close) the order. Payment is *recorded*, not processed (no gateway in scope).

**Inputs.**

| Field | Validation |
|---|---|
| Payment method | Required; e.g., Cash, Card, Other (reference list). |
| Amount tendered (opt.) | For Cash; system computes change = tendered − total (≥ 0). |

**Processing & Business Rules.**
- Finalisation records the payment, sets order status `Paid/Closed`, and stores the final figures (subtotal, discount, tax rate, tax, total) in one atomic transaction (BR-19, NFR-03).
- An order with zero items cannot be finalised.
- Once closed, the order becomes immutable (no further item/discount edits).

**Outputs / Post-conditions.** Order is closed and payment recorded; a receipt is available (FR-16); a dine-in table is released (FR-17).

**Exceptions.** No payment method → cannot finalise. Cash tendered < total → rejected. Transaction failure → order remains Open, no partial write (NFR-03).

**Acceptance Criteria.**
- Finalising with a valid method closes the order and locks its figures.
- A failed finalisation leaves the order fully Open with no orphaned payment.

---

#### FR-16 — Generate Receipt
- **Module:** POS | **Priority:** High | **Actors:** Cashier, Manager, Administrator
- **Trigger:** Order finalised (FR-15). | **Traces:** BO-1, BO-2; BR-20

**Description.** The system shall generate a printable/exportable receipt for a finalised order.

**Processing & Business Rules (receipt content — BR-20).**
- The receipt shall include: restaurant name/header, receipt/order number, date-time, table or "Takeaway", itemised lines (name, qty, unit price, line total), subtotal, discount, tax rate & tax, grand total, payment method (and change if cash), and the serving cashier.
- The receipt reproduces the stored finalised figures exactly (no recomputation from current prices/tax).
- A finalised receipt may be re-printed/re-exported without altering data.

**Outputs / Post-conditions.** A receipt is displayed and can be printed or exported (e.g., PDF).

**Acceptance Criteria.**
- The receipt totals match the finalised bill exactly.
- Re-printing later yields an identical receipt regardless of later menu/tax changes.

---

#### FR-17 — Release Table on Order Close
- **Module:** POS/Tables | **Priority:** High | **Actors:** System
- **Trigger:** A dine-in order is closed (FR-15). | **Traces:** BO-1; BR-12

**Description.** The system shall release the table when a dine-in order is closed, setting it to `Needs Cleaning` (and then `Free` once staff mark it cleaned).

**Processing & Business Rules.**
- On close of a dine-in order the associated table transitions `Occupied → Needs Cleaning` automatically (BR-12).
- Takeaway orders have no table and trigger no table change.

**Outputs / Post-conditions.** The table is freed for turnover; the floor view updates immediately.

**Acceptance Criteria.**
- Closing a dine-in order changes its table to Needs Cleaning without manual action.
- The table becomes available for a new order once marked Free.

---

### 3.4 Inventory & Suppliers (FR-18 … FR-22)

#### FR-18 — Manage Stock Items
- **Module:** Inventory | **Priority:** High | **Actors:** Manager, Administrator
- **Trigger:** Manager sets up or maintains inventory. | **Traces:** BO-4, BO-5; BR-21

**Description.** A Manager shall be able to create, edit, and delete stock items, each with a unit of measure and a reorder level.

**Inputs.**

| Field | Validation |
|---|---|
| Stock item name | Required, unique, 2–80 chars. |
| Unit of measure | Required; e.g., kg, L, unit, bottle. |
| Reorder level | Required; number ≥ 0 in the item's unit. |
| Quantity on hand | System-maintained; opening value on create ≥ 0. |

**Processing & Business Rules.**
- Stock item names are unique (BR-21); quantity on hand is never edited directly to fabricate stock — it changes only via recorded deliveries (FR-21) or explicit adjustments that are logged.
- A stock item referenced by any purchase-order history cannot be hard-deleted; it is deactivated to preserve history.

**Outputs / Post-conditions.** The inventory catalogue reflects the change; reorder flags (FR-22) recompute.

**Exceptions.** Duplicate name / negative reorder level → rejected. Delete with PO history → converted to deactivate.

**Acceptance Criteria.**
- A new stock item appears in inventory with its unit and reorder level and participates in low-stock flagging.

---

#### FR-19 — Register & Maintain Suppliers
- **Module:** Suppliers | **Priority:** Medium | **Actors:** Manager, Administrator
- **Trigger:** A new supplier relationship is established. | **Traces:** BO-4, BO-5; BR-22

**Description.** A Manager shall be able to register and maintain suppliers used on purchase orders.

**Inputs.**

| Field | Validation |
|---|---|
| Supplier name | Required, unique, 2–100 chars. |
| Contact person / phone / email | Optional; email & phone format-validated when present. |
| Address (opt.) | Up to 255 chars. |
| Status | Active / Inactive. |

**Processing & Business Rules.**
- Supplier names are unique (BR-22). A supplier referenced by purchase orders cannot be deleted; it is set Inactive instead.

**Outputs / Post-conditions.** Supplier is selectable when creating purchase orders (FR-20).

**Acceptance Criteria.**
- A registered active supplier can be chosen on a new purchase order.
- Deleting a supplier with PO history is prevented.

---

#### FR-20 — Create Purchase Order
- **Module:** Purchasing | **Priority:** High | **Actors:** Manager, Administrator
- **Trigger:** Stock is low / restock is needed. | **Traces:** BO-4; BR-23

**Description.** A Manager shall be able to create a purchase order (PO) for a supplier, listing stock items and quantities to be purchased.

**Inputs.**

| Field | Validation |
|---|---|
| Supplier | Required; an active supplier (FR-19). |
| PO lines | ≥ 1 line; each = stock item (FR-18) + quantity > 0 (+ optional unit cost). |
| Expected date (opt.) | Valid date, today or later. |

**Processing & Business Rules.**
- A PO is created with a unique number, status `Ordered`, creating user, and timestamp (BR-23).
- Creating a PO does *not* change stock on hand; stock increases only on receipt (FR-21).
- A PO must contain at least one valid line.

**Outputs / Post-conditions.** An open PO awaiting delivery; visible in the purchasing list.

**Exceptions.** No lines / quantity ≤ 0 → rejected.

**Acceptance Criteria.**
- A valid PO is saved with status Ordered and does not alter stock on hand.

---

#### FR-21 — Receive Delivery & Increase Stock
- **Module:** Purchasing | **Priority:** High | **Actors:** Manager, Administrator
- **Trigger:** Goods arrive against a PO. | **Traces:** BO-4; BR-24; NFR-03

**Description.** The system shall increase stock on hand when a purchase order's delivery is recorded as received.

**Inputs.**

| Field | Validation |
|---|---|
| PO reference | Required; an Ordered/partially-received PO. |
| Received quantities | Per line; 0 ≤ received ≤ ordered (partial receipts allowed). |

**Processing & Business Rules.**
- On receipt, each stock item's quantity on hand increases by the received quantity in a single transaction (BR-24, NFR-03).
- PO status updates to `Received` (all lines fulfilled) or `Partially Received`.
- Recording receipt recomputes reorder flags (FR-22).

**Outputs / Post-conditions.** Stock on hand reflects the delivery; PO status updated; low-stock flags refreshed.

**Exceptions.** Received > ordered → rejected. Transaction failure → no stock change (no partial write).

**Acceptance Criteria.**
- Receiving 10 units of an item raises its on-hand by exactly 10.
- A failed receipt leaves stock and PO status unchanged.

---

#### FR-22 — Flag Low Stock
- **Module:** Inventory | **Priority:** High | **Actors:** System (surfaced to Manager/Admin)
- **Trigger:** Stock quantity changes or inventory is viewed. | **Traces:** BO-4; BR-25

**Description.** The system shall flag stock items whose quantity on hand is at or below their reorder level, so shortages are caught early.

**Processing & Business Rules.**
- An item is flagged when `quantity on hand ≤ reorder level` (BR-25).
- Flagged items are visually highlighted in inventory and listed in the inventory report (FR-28).

**Outputs / Post-conditions.** A visible low-stock indicator and a filterable list of items needing reorder.

**Acceptance Criteria.**
- An item at or below its reorder level is flagged; receiving stock above the level clears the flag.

---

### 3.5 Staff & Reservations (FR-23 … FR-26)

#### FR-23 — Manage Staff Records
- **Module:** Staff | **Priority:** Medium | **Actors:** Manager, Administrator
- **Trigger:** Hiring, role change, or offboarding. | **Traces:** BO-3, BO-5; BR-26

**Description.** A Manager shall be able to create, edit, and deactivate staff records (name, role, contact, status).

**Inputs.**

| Field | Validation |
|---|---|
| Name | Required, 2–100 chars. |
| Role / position | Required (e.g., Cashier, Waiter, Chef). |
| Contact | Phone/email, format-validated. |
| Status | Active / Inactive. |

**Processing & Business Rules.**
- Staff records are distinct from system *user accounts* (FR-03); a staff record may or may not have a login. Deactivation retains history (BR-26).
- A staff record linked to historical activity is deactivated, not deleted, to preserve reporting integrity.

**Outputs / Post-conditions.** The staff roster reflects the change; inactive staff are excluded from active listings.

**Acceptance Criteria.**
- A new staff record is stored and appears in the roster; deactivation hides them from active lists but keeps history.

---

#### FR-24 — Create Reservation
- **Module:** Reservations | **Priority:** High | **Actors:** Cashier, Manager, Administrator
- **Trigger:** A customer books a table. | **Traces:** BO-1, BO-5; BR-27, BR-28

**Description.** A Cashier or Manager shall be able to create a reservation with customer name, contact, date, time, party size, and table.

**Inputs.**

| Field | Validation |
|---|---|
| Customer name | Required, 2–100 chars. |
| Contact | Required; phone/email, format-validated. |
| Date & time | Required; must be in the future (today or later, valid service time). |
| Party size | Required; integer ≥ 1. |
| Table | Required; an existing table (FR-08). |

**Processing & Business Rules.**
- Party size should not exceed table capacity; if it does, the system warns and may require override or a larger table (BR-28).
- The chosen table/time must not overlap an existing reservation (enforced by FR-26/BR-27).
- A confirmed reservation sets the table status to `Reserved` for that service window (FR-09).
- New reservations are created with status `Booked`.

**Outputs / Post-conditions.** A stored reservation; the table reflects the booking; the reservation appears in the day's list.

**Exceptions.** Past date/time → rejected. Overlap → rejected (FR-26). Party > capacity → warning/override.

**Acceptance Criteria.**
- A valid future reservation on a free time-slot is saved and marks the table Reserved.

---

#### FR-25 — Reservation Lifecycle (Seat / Complete / Cancel)
- **Module:** Reservations | **Priority:** Medium | **Actors:** Cashier, Manager, Administrator
- **Trigger:** Guest arrives, dines, or cancels. | **Traces:** BO-1; BR-29; Appendix B

**Description.** The system shall allow a reservation to be seated, completed, or cancelled.

**Processing & Business Rules (state model).**
- `Booked → Seated` (guest arrives; table becomes Occupied, an order may be opened), `Seated → Completed` (visit ends), or `Booked/Seated → Cancelled`. An optional `No-Show` state may close out un-arrived bookings.
- Cancelling or completing a reservation frees the table's reserved hold, per the table state model (BR-29).
- Only defined transitions are permitted.

**Outputs / Post-conditions.** Reservation and table statuses reflect the lifecycle stage.

**Acceptance Criteria.**
- Seating a reservation frees the reserved hold and marks the table Occupied; cancelling frees the slot for rebooking.

---

#### FR-26 — Prevent Double-Booking
- **Module:** Reservations | **Priority:** High | **Actors:** System
- **Trigger:** A reservation is created or rescheduled. | **Traces:** BO-1; BR-27

**Description.** The system shall prevent double-booking the same table for overlapping reservation times.

**Processing & Business Rules.**
- Before saving, the system checks the target table for any active (Booked/Seated) reservation whose time window overlaps the requested window (default slot duration configurable) — BR-27.
- Overlaps are rejected; the system may suggest alternative free tables/times.
- Cancelled/completed reservations do not block new bookings.

**Outputs / Post-conditions.** Only non-conflicting reservations are stored.

**Exceptions.** Overlap detected → save blocked with a clear conflict message.

**Acceptance Criteria.**
- Two overlapping reservations on the same table cannot both exist.
- Back-to-back non-overlapping bookings are allowed.

---

### 3.6 Reporting (FR-27 … FR-30)

#### FR-27 — Sales Report
- **Module:** Reporting | **Priority:** High | **Actors:** Manager, Administrator
- **Trigger:** Manager selects Reports → Sales. | **Traces:** BO-3; BR-30

**Description.** A Manager shall be able to generate a sales report for a chosen date range showing totals and item breakdowns.

**Inputs.**

| Field | Validation |
|---|---|
| Date range | Required; start ≤ end; both valid dates. |
| Filters (opt.) | Order type, payment method, cashier, category. |

**Processing & Business Rules.**
- Aggregates only *finalised* orders within the range: total sales, order count, average order value, tax collected, discounts given, and a per-item / per-category quantity-and-revenue breakdown (BR-30).
- Figures derive from stored finalised order data (never from current menu prices).

**Outputs / Post-conditions.** An on-screen sales report, exportable via FR-30.

**Exceptions.** start > end → rejected. No data in range → empty report with a clear note.

**Acceptance Criteria.**
- Totals reconcile to the sum of finalised orders in the range.
- Item breakdown quantities and revenue match the underlying orders.

---

#### FR-28 — Inventory / Stock Report
- **Module:** Reporting | **Priority:** High | **Actors:** Manager, Administrator
- **Trigger:** Manager selects Reports → Inventory. | **Traces:** BO-4; BR-25

**Description.** A Manager shall be able to generate an inventory report showing current stock levels and items below reorder level.

**Processing & Business Rules.**
- Lists every active stock item with unit, quantity on hand, reorder level, and a low-stock indicator (quantity ≤ reorder level — BR-25).
- Supports filtering to "below reorder level only" to drive purchasing.

**Outputs / Post-conditions.** An on-screen inventory report, exportable via FR-30.

**Acceptance Criteria.**
- Every item at/below its reorder level is listed as low.
- Quantities match live stock on hand at generation time.

---

#### FR-29 — Staff Activity Report
- **Module:** Reporting | **Priority:** Medium | **Actors:** Manager, Administrator
- **Trigger:** Manager selects Reports → Staff Activity. | **Traces:** BO-3; FR-01, FR-04

**Description.** A Manager shall be able to generate a staff activity report for a chosen date range.

**Processing & Business Rules.**
- Summarises, per user/cashier: login/logout activity, number of orders processed, total sales handled, and discounts applied — derived from session events (FR-01/FR-04) and finalised orders.
- Respects RBAC: only Manager/Administrator may view it (FR-02).

**Outputs / Post-conditions.** An on-screen staff activity report, exportable via FR-30.

**Acceptance Criteria.**
- Order counts and sales totals per cashier reconcile to finalised orders in the range.

---

#### FR-30 — Export Reports
- **Module:** Reporting | **Priority:** Medium | **Actors:** Manager, Administrator
- **Trigger:** A report is displayed. | **Traces:** BO-3

**Description.** The system shall allow any report (FR-27…FR-29) to be exported to a shareable, printable format (e.g., PDF) or printed directly.

**Processing & Business Rules.**
- The export reproduces the on-screen report exactly, with title, generated-by user, generation timestamp, and the applied date range/filters in the header.

**Outputs / Post-conditions.** A file/printout suitable for records or sharing with the owner.

**Acceptance Criteria.**
- The exported document matches the displayed report and records its parameters.

---

## 4. Key Use Cases & Process Flows

These end-to-end flows show how the functional requirements combine to serve the operational processes described in BRD §7. Each numbered step references the FR(s) it exercises.

### UC-1 — Order-to-Payment (Dine-in)

| Field | Detail |
|---|---|
| Actor | Cashier |
| Goal | Serve a seated party from order entry to paid receipt with an accurate bill. |
| Pre-conditions | Cashier is authenticated (FR-01); a Free table exists. |

**Main flow:**
1. Cashier opens a new dine-in order and selects the table → table becomes Occupied. `[FR-10][FR-09]`
2. Cashier adds available menu items with quantities; subtotal updates live. `[FR-11][FR-12]`
3. Customer requests the bill; cashier optionally applies a discount. `[FR-13]`
4. System computes tax and grand total. `[FR-14]`
5. Cashier records the payment method and finalises the order (atomic). `[FR-15]`
6. System generates the receipt (print/export). `[FR-16]`
7. System sets the table to Needs Cleaning → Free after cleaning. `[FR-17][FR-09]`

**Alternate / exception flows:** item unavailable (blocked at step 2, FR-11); discount invalid (FR-13); finalisation transaction fails → order stays Open, nothing written (FR-15, NFR-03); takeaway variant skips table steps 1 & 7.

### UC-2 — Menu Maintenance
1. Manager authenticates and opens Menu administration. `[FR-01]`
2. Manager creates/edits categories. `[FR-05]`
3. Manager creates/edits items with price & availability; changes appear immediately in ordering. `[FR-06]`
4. Manager marks sold-out items Unavailable (retaining history) and re-enables later. `[FR-07]`

**Rule:** price edits affect only future orders, never finalised bills (BR-09).

### UC-3 — Inventory & Purchasing
1. Manager registers suppliers and stock items with reorder levels. `[FR-19][FR-18]`
2. System flags items at/below reorder level. `[FR-22]`
3. Manager raises a purchase order to a supplier. `[FR-20]`
4. On delivery, manager records received quantities → stock on hand increases (atomic); flags refresh. `[FR-21][FR-22]`

### UC-4 — Reservation
1. Cashier/Manager creates a reservation (customer, date/time, party size, table). `[FR-24]`
2. System rejects overlapping bookings on the same table. `[FR-26]`
3. Table shows Reserved for the service window. `[FR-09]`
4. Reservation is seated, completed, or cancelled. `[FR-25]`

### UC-5 — Reporting
1. Manager/Administrator selects a report and date range. `[FR-27][FR-28][FR-29]`
2. System aggregates the underlying data and presents results.
3. Manager exports/prints the report. `[FR-30]`

---

## 5. Data Requirements & State Models

The functions above operate over the core entities below. This is a functional-level view; full attributes, keys, and the 3NF schema are finalised in the Database Design (REF-3). All operational data resides in one shared MySQL database (NFR-07) used by both phases.

### 5.1 Core Entities

| Entity | Purpose / key attributes (indicative) | Key relationships |
|---|---|---|
| User Account | Login identity: username, password hash, role, status. | Has one Role; creates Orders, POs. |
| Role | Administrator / Manager / Cashier; governs permissions. | Held by User Accounts. |
| Staff | Employee record: name, position, contact, status. | Optionally linked to a User Account. |
| Menu Category | Grouping: name, display order. | Has many Menu Items. |
| Menu Item | Sellable item: name, price, availability. | Belongs to a Category; referenced by Order Items. |
| Table | Dining table: label, capacity, status. | Referenced by Orders and Reservations. |
| Order | Header: number, type, status, timestamps, subtotal, discount, tax rate, tax, total, payment. | Belongs to a Table (dine-in); has many Order Items; has one Payment. |
| Order Item | Line: item, quantity, unit-price snapshot, line total. | Belongs to an Order; references a Menu Item. |
| Payment | Method, amount, timestamp (recorded, not processed). | Belongs to one finalised Order. |
| Stock Item | Ingredient/supply: name, unit, reorder level, quantity on hand. | Referenced by PO Items. |
| Supplier | Vendor: name, contact, status. | Has many Purchase Orders. |
| Purchase Order | Header: number, supplier, status, dates. | Belongs to a Supplier; has many PO Items. |
| Purchase Order Item | Line: stock item, ordered qty, received qty, unit cost. | Belongs to a PO; references a Stock Item. |
| Reservation | Customer, contact, date/time, party size, status. | Belongs to a Table. |
| Reference Data | Tax rate, payment methods, and other system constants. | Administered via FR-31 (Administrator-only, audited); consumed by billing and validation. |

### 5.2 Key Data Integrity Rules
- Financial and stock updates occur within transactions; a failed operation leaves no partial data (NFR-03).
- Records with historical references are deactivated, not deleted, to preserve auditability (menu items, suppliers, stock items, staff, accounts).
- Order Items store a unit-price snapshot; finalised orders store the tax rate in force — so history is immutable to later configuration changes.
- The schema normalises to at least 3NF (NFR-07).

### 5.3 State Models

| Object | States | Permitted transitions |
|---|---|---|
| Order | Open, Paid/Closed, (Cancelled) | Open → Paid/Closed (FR-15); Open → Cancelled (voided before payment). |
| Table | Free, Occupied, Reserved, Needs Cleaning | Free/Reserved → Occupied (FR-10); Occupied → Needs Cleaning (FR-17); Needs Cleaning → Free; Free ↔ Reserved (FR-24/FR-25). |
| Reservation | Booked, Seated, Completed, Cancelled, (No-Show) | Booked → Seated → Completed; Booked/Seated → Cancelled; Booked → No-Show. |
| Purchase Order | Ordered, Partially Received, Received, (Cancelled) | Ordered → Partially Received → Received (FR-21); Ordered → Cancelled. |

---

## 6. Business Rules Catalogue

Business rules are cross-cutting constraints referenced throughout §3. They are specified once here and enforced in the shared business layer.

| ID | Rule | Enforced by |
|---|---|---|
| BR-01 | No function is accessible without a valid authenticated session. | FR-01, FR-02 |
| BR-02 | Passwords are stored only as salted one-way hashes; never in plain text in DB, logs, or UI. | FR-01, FR-03; NFR-04 |
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

---

## 7. Non-Functional Requirements

| ID | Category | Requirement & measurable target |
|---|---|---|
| NFR-01 | Usability | Front-line ordering/billing screens shall be learnable by a cashier in a single short training session, with minimal clicks for common actions (open order, add item, bill). Target: a trained cashier completes an order-to-receipt cycle without reference to a manual. |
| NFR-02 | Performance | Common actions (open an order, add an item, generate a bill) shall respond within ≈2 seconds under normal single-restaurant load. |
| NFR-03 | Reliability & Data Integrity | Database constraints and transactions keep financial and stock data consistent; a failed operation shall leave no partial data. |
| NFR-04 | Security | Passwords stored hashed (not plain text); access enforced by role; financial and personal data not exposed to unauthorised roles. |
| NFR-05 | Maintainability | MVC separation of data, business logic, and presentation so the interface can be replaced (desktop→web) without rewriting business logic. |
| NFR-06 | Portability / Migration | Business logic and data access kept independent of the desktop UI framework, reusable by the future web interface on the same MySQL database. |
| NFR-07 | Data Persistence | All operational data stored in a MySQL relational database normalised to at least 3NF, shared across both phases. |
| NFR-08 | Availability | For the desktop phase, the system functions on the restaurant's local machine/network without requiring internet access. |

---

## 8. Assumptions, Dependencies & Constraints

### 8.1 Assumptions
- The restaurant operates as a single location for the initial phases.
- A reliable local computer (and network, if multi-terminal) is available for the desktop phase.
- Payment is recorded in the system but processed outside it (no card gateway in scope).
- Menu, tax rate, and reference data are provided by the restaurant during setup.
- Cashier-role staff have basic computer familiarity.

### 8.2 Dependencies
- Reference data (tax rate, payment methods) must be configured via **FR-31** before billing (FR-14/FR-15).
- Menu categories/items and tables must exist before orders and reservations can be taken.
- Suppliers and stock items must exist before purchase orders and receipts.

### 8.3 Constraints (from BRD §12)
- **Technology:** Desktop client in Java/JavaFX; MySQL data store; MVC architecture.
- **Migration:** Business logic must not be locked into the desktop UI, so a web front end can reuse it.
- **Budget/effort:** Built by a small team, favouring proven, well-documented technologies over experimental ones.

### 8.4 Risk Alignment

| BRD Risk | How this FRD mitigates it |
|---|---|
| Scope creep from out-of-scope features | §1.2 fixes the in/out-of-scope boundary; no out-of-scope FRs are specified. |
| Desktop logic too coupled to UI | NFR-05/06 and §2.2 mandate calculation/validation in a reusable business layer. |
| Data model changes late | §5 fixes entities/state models before heavy coding; schema to 3NF. |
| Incorrect financial calculations | Centralised billing engine, atomic transactions, and explicit BR-13/16/17/18/19 with worked acceptance criteria. |

---

## 9. Requirements Traceability Matrix

Each functional requirement traces up to at least one business objective and across to the use case that exercises it, giving full coverage from business need to verifiable behaviour.

| FR | Title | Business Objective(s) | Use Case | Priority |
|---|---|---|---|---|
| FR-01 | Login & Authentication | BO-5 | All | High |
| FR-02 | Role-Based Access Control | BO-5 | All | High |
| FR-03 | Manage User Accounts | BO-5 | Admin | High |
| FR-04 | Logout / Session End | BO-5 | All | High |
| FR-05 | Manage Menu Categories | BO-1, BO-5 | UC-2 | High |
| FR-06 | Manage Menu Items | BO-1, BO-2, BO-5 | UC-2 | High |
| FR-07 | Toggle Item Availability | BO-1 | UC-2 | Medium |
| FR-08 | Define Tables | BO-1, BO-5 | UC-1, UC-4 | High |
| FR-09 | Table Status | BO-1 | UC-1, UC-4 | High |
| FR-10 | Open Order | BO-1 | UC-1 | High |
| FR-11 | Add/Remove Items | BO-1, BO-2 | UC-1 | High |
| FR-12 | Subtotal Calculation | BO-2 | UC-1 | High |
| FR-13 | Apply Discount | BO-2 | UC-1 | Medium |
| FR-14 | Tax & Total | BO-2 | UC-1 | High |
| FR-15 | Record Payment / Finalise | BO-1, BO-2 | UC-1 | High |
| FR-16 | Generate Receipt | BO-1, BO-2 | UC-1 | High |
| FR-17 | Release Table | BO-1 | UC-1 | High |
| FR-18 | Manage Stock Items | BO-4, BO-5 | UC-3 | High |
| FR-19 | Manage Suppliers | BO-4, BO-5 | UC-3 | Medium |
| FR-20 | Create Purchase Order | BO-4 | UC-3 | High |
| FR-21 | Receive Delivery | BO-4 | UC-3 | High |
| FR-22 | Flag Low Stock | BO-4 | UC-3 | High |
| FR-23 | Manage Staff | BO-3, BO-5 | — | Medium |
| FR-24 | Create Reservation | BO-1, BO-5 | UC-4 | High |
| FR-25 | Reservation Lifecycle | BO-1 | UC-4 | Medium |
| FR-26 | Prevent Double-Booking | BO-1 | UC-4 | High |
| FR-27 | Sales Report | BO-3 | UC-5 | High |
| FR-28 | Inventory Report | BO-3, BO-4 | UC-5 | High |
| FR-29 | Staff Activity Report | BO-3 | UC-5 | Medium |
| FR-30 | Export Reports | BO-3 | UC-5 | Medium |
| FR-31 | Configure Reference / System Data | BO-2, BO-5 | UC-2, UC-1 (setup dependency) | High |

*BO-1 Speed up service · BO-2 Reduce billing errors · BO-3 Management visibility · BO-4 Control stock & cost · BO-5 Centralise data · BO-6 Prepare for growth (served by NFR-05/06 architecture, not a single FR).*

---

## 10. Acceptance & Success Criteria

The FRD is satisfied when all of the following hold, consistent with BRD §14:

1. A cashier can complete an entire order-to-receipt cycle within the system (FR-10…FR-17).
2. A manager can maintain the menu, stock, suppliers, and reservations and generate accurate, exportable reports (FR-05…FR-08, FR-18…FR-30).
3. All monetary calculations (subtotal, discount, tax, total) are computed automatically and verified correct against the worked examples in FR-12/13/14.
4. Every data operation persists correctly in a 3NF MySQL database, with no partial writes on failure (NFR-03, NFR-07).
5. Access is correctly governed by role for all functions per the §2.4 matrix (FR-01, FR-02).
6. The architecture allows the Phase 2 web front end to be added without rewriting the business or data layers (NFR-05, NFR-06).

> Each acceptance criterion in §3 is written to be objectively testable and maps to test cases in the Test Plan (REF-4).

---

## Appendix A — Field Validation Reference

| Field | Rule | On violation |
|---|---|---|
| Username | Required, unique, 3–50 chars, no leading/trailing spaces. | Reject; "Username already exists" / "Enter 3–50 characters". |
| Password | Meets policy: min 8 chars, at least one letter and one digit; stored hashed. | Reject with policy hint. |
| Names (menu/item/table/supplier/stock/customer/staff) | Required, length-bounded, unique where specified. | Reject with field-specific message. |
| Price / unit cost | Decimal ≥ 0.00, two decimals, within currency max. | Reject; "Enter a valid non-negative price". |
| Quantity (order / PO / party size) | Integer ≥ 1 (received qty ≥ 0). | Reject; "Enter a whole number ≥ 1". |
| Discount % | 0–100. | Reject; cap to valid range. |
| Discount fixed | 0 ≤ value ≤ subtotal. | Reject; "Discount cannot exceed subtotal". |
| Reorder level | Number ≥ 0 in item's unit. | Reject; "Enter a non-negative value". |
| Reservation date/time | Valid date/time, today or future, within service hours. | Reject; "Choose a future date/time". |
| Email / phone | Format-validated when provided. | Reject; "Enter a valid email/phone". |
| Date range (reports) | start ≤ end; both valid dates. | Reject; "Start date must be on or before end date". |
| Tax rate (system config) | Decimal ≥ 0 and ≤ 1.0000 (0–100%), four decimals. | Reject; "Enter a tax rate between 0 and 100%". |
| System constants (timeout / attempts / slot) | Integers within bounds (timeout ≥ 0; attempts ≥ 1; slot ≥ 1). | Reject; field-specific message. |
| Discount approval threshold | Decimal ≥ 0. | Reject; "Enter a non-negative value". |

---

## Appendix B — Status & Enumeration Reference

| Enumeration | Values |
|---|---|
| User Role | Administrator, Manager, Cashier |
| Account / Staff / Supplier Status | Active, Inactive |
| Menu Item Availability | Available, Unavailable |
| Table Status | Free, Occupied, Reserved, Needs Cleaning |
| Order Type | Dine-in, Takeaway |
| Order Status | Open, Paid/Closed, Cancelled |
| Payment Method | Cash, Card, Other (reference list) |
| Purchase Order Status | Ordered, Partially Received, Received, Cancelled |
| Reservation Status | Booked, Seated, Completed, Cancelled, No-Show |

---

*— End of Functional Requirements Document (RMS-FRD v1.1) —*
