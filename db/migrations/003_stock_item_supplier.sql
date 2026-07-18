-- Migration 003 — link each stock item to its supplier.
--
-- Purchasing offered every stock item against every supplier, which is not how ordering works:
-- a supplier has a catalogue. This adds an optional supplier link on stock_item so the purchasing
-- screen can offer only the chosen supplier's items, and the inventory panel can show who supplies
-- an item. Nullable: an item with no supplier yet is still valid (it simply cannot be put on a PO
-- until one is assigned).
--
-- Idempotent: guarded so re-running does nothing on a DB that already has the column.

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'stock_item'
    AND COLUMN_NAME = 'supplier_id'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `stock_item` ADD COLUMN `supplier_id` INT NULL AFTER `status`, '
  || 'ADD CONSTRAINT `fk_stock_item_supplier` FOREIGN KEY (`supplier_id`) '
  || 'REFERENCES `supplier`(`supplier_id`)',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
