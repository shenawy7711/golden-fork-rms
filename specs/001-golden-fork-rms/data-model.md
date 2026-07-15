# Phase 1 — Data Model: Golden Fork RMS

**Feature**: 001-golden-fork-rms | **Date**: 2026-07-14 | **Source**: TDD §3 data dictionary
(authoritative), FRD business rules, Constitution Principle V.

One shared MySQL database, **18 tables**, normalised to ≥ 3NF, reused unchanged by both phases.
Money = `DECIMAL(10,2)`; tax rate = `DECIMAL(5,4)`; fractional stock = `DECIMAL(10,3)`. Types,
keys, and constraints below match the TDD data dictionary exactly. The full DDL lives in
`db/schema.sql`; this file is the entity/constraint/state reference.

---

## 1. Entity overview (18 tables)

| # | Table | Module | Purpose | Soft-delete? |
|---|---|---|---|---|
| 1 | `role` | Auth | RBAC roles (reference) | no (fixed set) |
| 2 | `user_account` | Auth | Login identities, hashed passwords | yes (`status`) |
| 3 | `staff` | Staff | Employee records (distinct from logins) | yes (`status`) |
| 4 | `login_event` | Auth/Report | Login/logout audit (feeds FR-29) | no (append-only) |
| 5 | `menu_category` | Menu | Item groupings | delete-if-empty |
| 6 | `menu_item` | Menu | Sellable items + price | yes (`availability`) |
| 7 | `dining_table` | Tables | Physical tables + live status | delete-if-free |
| 8 | `orders` | POS | Order header + stored final figures | status lifecycle |
| 9 | `order_item` | POS | Order lines + unit-price snapshot | cascade w/ open order |
| 10 | `payment_method` | POS | Payment methods (reference) | no (fixed set) |
| 11 | `payment` | POS | One payment per finalised order | no (immutable) |
| 12 | `supplier` | Inventory | Vendors | yes (`status`) |
| 13 | `stock_item` | Inventory | Inventory items + on-hand | yes (`status`) |
| 14 | `stock_movement` | Inventory | Immutable on-hand ledger | no (append-only) |
| 15 | `purchase_order` | Inventory | PO header | status lifecycle |
| 16 | `purchase_order_item` | Inventory | PO lines (ordered/received qty) | cascade w/ PO |
| 17 | `reservation` | Reservations | Table bookings | status lifecycle |
| 18 | `system_config` | Config | Tax rate & tunable reference data | no |

---

## 2. Tables, fields, and constraints

Legend: **PK** primary key · **FK** foreign key · **UQ** unique · **NN** NOT NULL ·
**CK** check. All `*_id` surrogate keys are `AUTO_INCREMENT`.

### 2.1 `role`  *(FR-02)*
| Column | Type | Constraints |
|---|---|---|
| role_id | INT | PK |
| role_name | VARCHAR(20) | UQ, NN — 'Administrator' \| 'Manager' \| 'Cashier' |

### 2.2 `user_account`  *(FR-01, FR-03; BR-02, BR-04, BR-05, BR-06)*
| Column | Type | Constraints |
|---|---|---|
| user_id | INT | PK |
| username | VARCHAR(50) | UQ, NN — 3–50 chars, case-insensitive login |
| password_hash | VARCHAR(100) | NN — salted BCrypt/PBKDF2; never plain text |
| full_name | VARCHAR(100) | NN — 2–100 chars |
| role_id | INT | FK → role, NN |
| status | ENUM('Active','Inactive') | NN, default 'Active' |
| created_at | DATETIME | NN |

**Rules**: unique username (BR-04); deactivate to keep history (BR-05); ≥1 active Administrator
must always exist (BR-06) — enforced in `UserService`.

### 2.3 `staff`  *(FR-23; BR-26)*
| Column | Type | Constraints |
|---|---|---|
| staff_id | INT | PK |
| full_name | VARCHAR(100) | NN |
| position | VARCHAR(50) | NN |
| phone | VARCHAR(30) | NULL — format-validated when present |
| email | VARCHAR(100) | NULL — format-validated when present |
| status | ENUM('Active','Inactive') | NN, default 'Active' |
| user_id | INT | FK → user_account, NULL — optional login link |

### 2.4 `login_event`  *(FR-01, FR-04, FR-29)*
| Column | Type | Constraints |
|---|---|---|
| event_id | BIGINT | PK |
| user_id | INT | FK → user_account, NN |
| event_type | ENUM('LOGIN','LOGOUT') | NN |
| event_time | DATETIME | NN |

Append-only audit; source for the staff-activity report.

### 2.5 `menu_category`  *(FR-05; BR-08)*
| Column | Type | Constraints |
|---|---|---|
| category_id | INT | PK |
| name | VARCHAR(50) | UQ, NN — 2–50 chars |
| display_order | INT | NULL |

Non-empty categories cannot be hard-deleted.

### 2.6 `menu_item`  *(FR-06, FR-07; BR-09, BR-10)*
| Column | Type | Constraints |
|---|---|---|
| item_id | INT | PK |
| category_id | INT | FK → menu_category, NN |
| name | VARCHAR(80) | NN, **UQ(category_id, name)** |
| price | DECIMAL(10,2) | NN, **CK price ≥ 0** |
| availability | ENUM('Available','Unavailable') | NN, default 'Available' |
| description | VARCHAR(255) | NULL |

Price snapshots onto order lines; items with order history become Unavailable, not deleted.

### 2.7 `dining_table`  *(FR-08, FR-09; BR-11, BR-12)*
| Column | Type | Constraints |
|---|---|---|
| table_id | INT | PK |
| label | VARCHAR(10) | UQ, NN |
| capacity | INT | NN, **CK capacity ≥ 1** |
| status | ENUM('Free','Occupied','Reserved','Needs Cleaning') | NN, default 'Free' |

### 2.8 `orders`  *(FR-10…FR-17; BR-13, BR-14, BR-16, BR-18)*
| Column | Type | Constraints |
|---|---|---|
| order_id | INT | PK |
| order_number | VARCHAR(20) | UQ, NN |
| order_type | ENUM('Dine-in','Takeaway') | NN |
| table_id | INT | FK → dining_table, NULL (NN-equiv for dine-in, NULL for takeaway) |
| status | ENUM('Open','Paid/Closed','Cancelled') | NN, default 'Open' |
| created_by | INT | FK → user_account, NN |
| created_at | DATETIME | NN |
| closed_at | DATETIME | NULL |
| subtotal | DECIMAL(10,2) | NN, default 0 |
| discount_type | ENUM('None','Percentage','Fixed') | NN, default 'None' |
| discount_value | DECIMAL(10,2) | NN, default 0 |
| discount_amount | DECIMAL(10,2) | NN, default 0 |
| tax_rate | DECIMAL(5,4) | NN — **snapshot at finalisation** |
| tax_amount | DECIMAL(10,2) | NN, default 0 |
| total | DECIMAL(10,2) | NN, default 0, **CK total ≥ 0** |

### 2.9 `order_item`  *(FR-11, FR-12; BR-13, BR-15)*
| Column | Type | Constraints |
|---|---|---|
| order_item_id | INT | PK |
| order_id | INT | FK → orders **ON DELETE CASCADE**, NN |
| item_id | INT | FK → menu_item, NN |
| quantity | INT | NN, **CK quantity ≥ 1** |
| unit_price | DECIMAL(10,2) | NN — **price snapshot at order time** |
| line_total | DECIMAL(10,2) | NN — quantity × unit_price |

### 2.10 `payment_method`  *(FR-15)*
| Column | Type | Constraints |
|---|---|---|
| method_id | INT | PK |
| method_name | VARCHAR(20) | UQ, NN — 'Cash' \| 'Card' \| 'Other' |

### 2.11 `payment`  *(FR-15; BR-19)*
| Column | Type | Constraints |
|---|---|---|
| payment_id | INT | PK |
| order_id | INT | FK → orders, **UQ**, NN — **one payment per order** |
| method_id | INT | FK → payment_method, NN |
| amount | DECIMAL(10,2) | NN — = order.total |
| amount_tendered | DECIMAL(10,2) | NULL — cash tendered |
| change_given | DECIMAL(10,2) | NULL — tendered − total, ≥ 0 |
| paid_at | DATETIME | NN |

### 2.12 `supplier`  *(FR-19; BR-05, BR-22)*
| Column | Type | Constraints |
|---|---|---|
| supplier_id | INT | PK |
| name | VARCHAR(100) | UQ, NN |
| contact_person | VARCHAR(100) | NULL |
| phone | VARCHAR(30) | NULL |
| email | VARCHAR(100) | NULL |
| address | VARCHAR(255) | NULL |
| status | ENUM('Active','Inactive') | NN, default 'Active' |

### 2.13 `stock_item`  *(FR-18, FR-22; BR-05, BR-21, BR-25)*
| Column | Type | Constraints |
|---|---|---|
| stock_item_id | INT | PK |
| name | VARCHAR(80) | UQ, NN |
| unit_of_measure | VARCHAR(20) | NN |
| reorder_level | DECIMAL(10,3) | NN, **CK ≥ 0** |
| quantity_on_hand | DECIMAL(10,3) | NN, **CK ≥ 0** — system-maintained |
| status | ENUM('Active','Inactive') | NN, default 'Active' |

Low-stock when `quantity_on_hand ≤ reorder_level` (BR-25).

### 2.14 `stock_movement`  *(FR-21; BR-21, BR-24)*
| Column | Type | Constraints |
|---|---|---|
| movement_id | BIGINT | PK |
| stock_item_id | INT | FK → stock_item, NN |
| po_item_id | INT | FK → purchase_order_item, NULL — set for PO receipts |
| movement_type | ENUM('Receipt','Adjustment') | NN |
| quantity_change | DECIMAL(10,3) | NN — signed delta |
| moved_at | DATETIME | NN |
| moved_by | INT | FK → user_account, NN |

Append-only ledger; `stock_item.quantity_on_hand` = running sum of these.

### 2.15 `purchase_order`  *(FR-20, FR-21; BR-23)*
| Column | Type | Constraints |
|---|---|---|
| po_id | INT | PK |
| po_number | VARCHAR(20) | UQ, NN |
| supplier_id | INT | FK → supplier, NN |
| status | ENUM('Ordered','Partially Received','Received','Cancelled') | NN, default 'Ordered' |
| created_by | INT | FK → user_account, NN |
| ordered_at | DATETIME | NN |
| expected_date | DATE | NULL — today or later |

### 2.16 `purchase_order_item`  *(FR-20, FR-21)*
| Column | Type | Constraints |
|---|---|---|
| po_item_id | INT | PK |
| po_id | INT | FK → purchase_order **ON DELETE CASCADE**, NN |
| stock_item_id | INT | FK → stock_item, NN |
| ordered_qty | DECIMAL(10,3) | NN, **CK > 0** |
| received_qty | DECIMAL(10,3) | NN, default 0, **CK 0 ≤ received_qty ≤ ordered_qty** |
| unit_cost | DECIMAL(10,2) | NULL |

### 2.17 `reservation`  *(FR-24, FR-25, FR-26; BR-27, BR-28, BR-29)*
| Column | Type | Constraints |
|---|---|---|
| reservation_id | INT | PK |
| table_id | INT | FK → dining_table, NN |
| customer_name | VARCHAR(100) | NN — 2–100 chars |
| contact_phone | VARCHAR(30) | NULL — one contact required (service rule) |
| contact_email | VARCHAR(100) | NULL — one contact required (service rule) |
| reservation_datetime | DATETIME | NN — future |
| duration_minutes | INT | NN, default 90 |
| party_size | INT | NN, **CK ≥ 1** |
| status | ENUM('Booked','Seated','Completed','Cancelled','No-Show') | NN, default 'Booked' |
| created_by | INT | FK → user_account, NN |

Overlap on the same table for active (Booked/Seated) windows is rejected (BR-27, FR-26).

### 2.18 `system_config`  *(FR-31 — Administrator-only, audited; consumed by FR-13/FR-14; BR-31)*
| Column | Type | Constraints |
|---|---|---|
| config_id | INT | PK |
| config_key | VARCHAR(50) | UQ, NN |
| config_value | VARCHAR(255) | NN |
| updated_by | INT | FK → user_account, NULL |
| updated_at | DATETIME | NN |

Seeded keys: `tax_rate`, `idle_timeout_min`, `login_max_attempts`,
`reservation_slot_minutes`, `discount_approval_threshold`. Written only by `SystemConfigService`
(Administrator-only, `CONFIGURE_SYSTEM`); every write stamps `updated_by`/`updated_at` (BR-31).

---

## 3. Relationships (referential integrity)

| Parent (1) | Child (N) | Rule |
|---|---|---|
| role | user_account | each user has exactly one role |
| user_account | staff | staff optionally linked to a login (0..1) |
| user_account | login_event / orders / purchase_order / reservation / stock_movement / system_config | audit "created_by / moved_by / updated_by" |
| menu_category | menu_item | items grouped by category |
| menu_item | order_item | item may appear on many lines |
| dining_table | orders / reservation | dine-in orders & bookings reference a table |
| orders | order_item | 1..N lines |
| orders | payment | **1:1** (payment.order_id UNIQUE) |
| payment_method | payment | reference list |
| supplier | purchase_order | vendor's POs |
| purchase_order | purchase_order_item | 1..N lines |
| stock_item | purchase_order_item / stock_movement | ordered on / moved |
| purchase_order_item | stock_movement | a receipt links to its PO line (0..N) |

---

## 4. State models (business-layer enforced; illegal transitions rejected — BR-12, BR-29)

**Order**: `Open → Paid/Closed` (finalise, FR-15); `Open → Cancelled` (void before payment).
Paid/Closed is terminal and immutable.

**Table**: `Free → Occupied` (open dine-in, FR-10); `Reserved → Occupied` (seat, FR-25);
`Occupied → Needs Cleaning` (close order, FR-17); `Needs Cleaning → Free` (staff mark clean);
`Free ↔ Reserved` (create/cancel/complete reservation, FR-24/25).

**Reservation**: `Booked → Seated → Completed`; `Booked|Seated → Cancelled`; `Booked → No-Show`.
Complete/Cancel frees the reserved hold.

**Purchase Order**: `Ordered → Partially Received → Received`; `Ordered → Cancelled`. Status is
recomputed from line `received_qty` vs `ordered_qty` on each receipt.

---

## 5. Key derived/computed values (owned by `BillingService`)

```
line_total      = unit_price_snapshot × quantity
subtotal        = Σ line_total
discount_amount = PERCENTAGE → round(subtotal × value/100)
                  FIXED      → min(value, subtotal)
                  None       → 0
discountable    = subtotal − discount_amount        (never < 0 — BR-17)
tax_amount      = round(discountable × tax_rate)     (tax_rate = order snapshot)
total           = discountable + tax_amount
change_given    = amount_tendered − total            (≥ 0; cash only)
```
One HALF-UP rounding step per money figure (BR-13, BR-16, BR-18). Worked example: subtotal
100.00, 10% discount → 10.00, base 90.00, 10% tax → 9.00, **total 99.00** (SC-002, TDD §8.3).

---

## 6. Seed data (`db/seed.sql`) — required for first boot

- `role`: Administrator, Manager, Cashier.
- `payment_method`: Cash, Card, Other.
- `system_config`: `tax_rate` (e.g. 0.1000), `idle_timeout_min`=15, `login_max_attempts`=5,
  `reservation_slot_minutes`=90, `discount_approval_threshold` (restaurant-set).
- One **active Administrator** `user_account` with a BCrypt-hashed password (satisfies BR-06
  from first boot; used to create all other accounts per FR-03).
