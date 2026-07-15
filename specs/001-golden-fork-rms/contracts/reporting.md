# Contract — Reporting (FR-27…FR-30)

> All reports aggregate **only finalised data** over the selected range/filters (BR-30) and
> derive figures from **stored** order data, never current menu prices. RBAC-guarded — Cashiers
> are denied (FR-02).

## ReportService

### `salesReport(range, filters) → SalesReport`
- **Permission**: `VIEW_REPORTS` (Manager/Admin). **FR-27; BR-30.**
- `range` requires start ≤ end (else `ValidationException`); optional filters: order type,
  payment method, cashier, category. Aggregates Paid/Closed orders in range: total sales, order
  count, average order value, tax collected, discounts given, and per-item / per-category
  quantity + revenue. Empty range → empty report with a note. Totals reconcile to underlying
  finalised orders (SC-007).

### `inventoryReport(belowReorderOnly?) → InventoryReport`
- **Permission**: `VIEW_REPORTS`. **FR-28; BR-25.** Lists active stock items with unit,
  on-hand, reorder level, and low-stock indicator (`on_hand ≤ reorder_level`). Quantities match
  live on-hand at generation time.

### `staffActivityReport(range) → StaffActivityReport`
- **Permission**: `VIEW_REPORTS`. **FR-29.** Per user/cashier: login/logout activity (from
  `login_event`), orders processed, total sales handled, discounts applied — from finalised
  orders. Counts/totals reconcile to finalised orders in range.

## ReportExporter (`util/ReportExporter`)

### `export(report) → byte[] (PDF)`
- **Permission**: `EXPORT_REPORTS` (Manager/Admin). **FR-30.** Reproduces the on-screen report
  exactly, with a header carrying title, generating user, generation timestamp, and the applied
  date range/filters. Output matches the displayed report and records its parameters.

**Tables**: `orders`, `order_item`, `stock_item`, `login_event`, `user_account`.
