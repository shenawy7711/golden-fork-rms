-- Migration 002 — reporting indexes on orders (T078, NFR-02).
--
-- The sales and staff-activity reports range-scan `orders` by time and status: finalised orders by
-- closed_at, and orders by created_at. Every foreign-key column is already indexed (InnoDB), but
-- these time columns are not, so at volume the range scan is the one query that could drift past the
-- NFR-02 ~2-second target. These two indexes keep it a range seek.
--
-- Idempotent: each index is added only if absent.

SET @has_closed := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_status_closed'
);
SET @ddl1 := IF(@has_closed = 0,
  'ALTER TABLE `orders` ADD INDEX `idx_orders_status_closed` (`status`, `closed_at`)',
  'SELECT 1');
PREPARE s1 FROM @ddl1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @has_created := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_created'
);
SET @ddl2 := IF(@has_created = 0,
  'ALTER TABLE `orders` ADD INDEX `idx_orders_created` (`created_at`)',
  'SELECT 1');
PREPARE s2 FROM @ddl2; EXECUTE s2; DEALLOCATE PREPARE s2;
