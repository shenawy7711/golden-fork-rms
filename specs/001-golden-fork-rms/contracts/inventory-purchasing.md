# Contract — Inventory & Suppliers (FR-18…FR-22)

## InventoryService

### `saveStockItem(item) → StockItem`
- **Permission**: `MANAGE_STOCK` (Manager/Admin). **FR-18; BR-21.**
- Unique name (2–80); unit of measure required; `reorder_level ≥ 0`; opening `quantity_on_hand
  ≥ 0` on create. On-hand is **never** edited directly afterwards — only via movements.

### `deactivateStockItem(stockItemId) → void`
- **Permission**: `MANAGE_STOCK`. **FR-18; BR-05.** A stock item with PO history is deactivated,
  not hard-deleted.

### `adjustStock(stockItemId, signedQty, reason, session) → void`
- **Permission**: `MANAGE_STOCK`. **FR-18; BR-21; NFR-03.** Atomic: writes an `Adjustment`
  `stock_movement` and updates `quantity_on_hand` together; refreshes low-stock flag.

### `lowStockItems() → List<StockItem>`
- **Permission**: `MANAGE_STOCK`. **FR-22; BR-25.** Active items where
  `quantity_on_hand ≤ reorder_level`. Feeds inventory report + dashboard alerts.

## SupplierService

### `saveSupplier(supplier) → Supplier`
- **Permission**: `MANAGE_SUPPLIERS` (Manager/Admin). **FR-19; BR-22.** Unique name (2–100);
  phone/email format-validated when present. Duplicate → `ConflictException`.

### `deactivateSupplier(supplierId) → void`
- **Permission**: `MANAGE_SUPPLIERS`. **FR-19; BR-05.** Supplier referenced by POs → set
  Inactive, not deleted.

## PurchasingService

### `createPO(supplierId, lines) → PurchaseOrder`
- **Permission**: `MANAGE_PURCHASING` (Manager/Admin). **FR-20; BR-23.**
- Supplier must be Active; ≥ 1 line, each `ordered_qty > 0` (optional unit cost); expected date
  today-or-later. Creates unique `po_number`, status Ordered, creating user + timestamp.
  **Does not change stock on hand.** No lines / qty ≤ 0 → `ValidationException`.

### `receiveDelivery(poId, receivedByLine[]) → PurchaseOrder`
- **Permission**: `MANAGE_PURCHASING`. **FR-21; BR-24; NFR-03.**
- Each `received ≤ (ordered − alreadyReceived)` (else `ValidationException`). **Atomic
  transaction** (TDD §5.4): per line insert a `Receipt` `stock_movement`, add to
  `stock_item.quantity_on_hand`, add to `purchase_order_item.received_qty`; then recompute PO
  status (Received | Partially Received) and refresh low-stock flags. Any error → rollback, no
  partial write (stock & PO unchanged).

**Tables**: `stock_item`, `stock_movement`, `supplier`, `purchase_order`,
`purchase_order_item`.
