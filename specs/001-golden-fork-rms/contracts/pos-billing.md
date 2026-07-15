# Contract — Orders & Billing / POS (FR-10…FR-17)

> All money math is performed **only** by `BillingService` (Principle III). Controllers/views
> never compute figures. Finalisation is one atomic transaction (BR-19, Principle VII).

## OrderService

### `openOrder(type, tableId?) → Order`
- **Permission**: `CREATE_ORDER` (all roles). **FR-10; BR-14.**
- Dine-in requires a Free/own-Reserved table with **no** existing open order (else
  `ConflictException`, offer existing order); Takeaway omits table. Creates Open order with
  unique `order_number`, `created_at`, `created_by`; sets dine-in table → Occupied.

### `addLine(orderId, itemId, quantity) → Order` / `removeLine(orderItemId) → Order`
- **Permission**: `CREATE_ORDER`. **FR-11; BR-15.**
- Item must be Available; quantity ≥ 1. Captures `unit_price` **snapshot** at add time. Only
  while order is Open (finalised order → `ValidationException`). Recomputes subtotal via
  BillingService on every change.

### `voidOrder(orderId) → void`
- **Permission**: `CREATE_ORDER`. **FR-10 (lifecycle).** Open → Cancelled before payment;
  releases the table hold. Finalised orders can never be voided.

### `finalise(orderId, methodId, amountTendered?) → Order`
- **Permission**: `CREATE_ORDER`. **FR-15, FR-17; BR-19; NFR-03.**
- Rejects zero-item orders (`ValidationException`). Requires a payment method. For Cash,
  `change = tendered − total` must be ≥ 0 (else rejected). **Atomic transaction**: insert
  `payment` (1:1) + set order `Paid/Closed` + persist stored figures (subtotal, discount, tax
  rate, tax, total) + set dine-in table → Needs Cleaning — all commit together or roll back
  entirely (no orphaned payment, order stays Open). Finalised order is immutable.

## BillingService  *(single money engine — FR-12…FR-14; BR-13, BR-16, BR-17, BR-18)*

### `computeSubtotal(order) → Money`
- **FR-12.** `Σ(unit_price_snapshot × quantity)`, two-decimal HALF-UP.

### `applyDiscount(order, type, value, session) → Order`
- **FR-13; BR-17.** Percentage capped at 100; Fixed capped at subtotal; result never negative.
  If the discount exceeds `discount_approval_threshold` (`system_config`), requires
  `RbacGuard.require(session, APPROVE_DISCOUNT)` (Manager/Admin) and records the approving user;
  at/below threshold no separate approval. Over-cap value → `ValidationException`.

### `computeTaxAndTotal(order) → Order`
- **FR-14; BR-16, BR-18.** `tax = round((subtotal − discount) × tax_rate)`;
  `total = (subtotal − discount) + tax`. Uses the tax-rate **snapshot** stored on the order at
  finalisation; later reference-rate edits never alter a finalised bill.

## ReceiptService

### `generate(orderId) → byte[] (PDF)`
- **Permission**: `CREATE_ORDER`. **FR-16; BR-20.** Reproduces the **stored** finalised figures
  exactly (no recomputation): header, receipt/order number, date-time, table or "Takeaway",
  itemised lines, subtotal, discount, tax rate & tax, total, payment method (+ change if cash),
  serving cashier. Idempotent — reprints are identical regardless of later menu/tax changes.

**Tables**: `orders`, `order_item`, `menu_item`, `payment`, `payment_method`, `dining_table`.
