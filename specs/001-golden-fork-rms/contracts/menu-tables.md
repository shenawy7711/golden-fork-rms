# Contract — Menu & Table Management (FR-05…FR-09)

## MenuService

### `saveCategory(category) → MenuCategory`
- **Permission**: `MANAGE_MENU` (Manager/Admin). **FR-05; BR-08.**
- Unique name (2–50), optional display order. Duplicate → `ConflictException`.

### `deleteCategory(categoryId) → void`
- **Permission**: `MANAGE_MENU`. **FR-05.** Blocked if the category still contains items
  (`ConflictException`) — reassign/remove items first.

### `saveItem(item) → MenuItem`
- **Permission**: `MANAGE_MENU`. **FR-06; BR-09.**
- Name 2–80 **unique within category**; `category_id` must exist; `price ≥ 0` (two decimals);
  availability default Available; description ≤ 255. Price edits apply to **future** orders only
  — never retro-price finalised bills.

### `setAvailability(itemId, availability) → void`
- **Permission**: `MANAGE_MENU`. **FR-07; BR-10.** Toggling Unavailable removes the item from
  order-entry but preserves its record/history; open orders already holding it are unaffected;
  re-enabling makes it orderable again.

### `deleteItem(itemId) → void`
- **Permission**: `MANAGE_MENU`. **FR-06; BR-10.** An item referenced by any order is **not**
  hard-deleted — it is set Unavailable/discontinued (soft delete).

### `listOrderableItems() → List<MenuItem>` / `listCategories() → List<MenuCategory>`
- **Permission**: — . Read models for POS and admin screens (orderable = Available only).

## TableService

### `defineTable(table) → DiningTable`
- **Permission**: `DEFINE_TABLES` (Manager/Admin). **FR-08; BR-11.** Unique label (1–10),
  capacity ≥ 1, default status Free. Duplicate label → `ConflictException`.

### `deleteTable(tableId) → void`
- **Permission**: `DEFINE_TABLES`. **FR-08.** Blocked while Occupied or holding future
  reservations — must be free and un-booked first.

### `changeStatus(tableId, newStatus) → void`
- **Permission**: `UPDATE_TABLE_STATUS` (all roles). **FR-09; BR-12.** Validates the transition
  against the table state machine (Free/Occupied/Reserved/Needs Cleaning); illegal transition →
  `ValidationException`. Called by OrderService/ReservationService for automatic transitions.

**Tables**: `menu_category`, `menu_item`, `dining_table`.
