-- Migration 001 — payment_method activate/deactivate (T029a).
--
-- FR-15 / FR-31 and contracts/auth-admin.md require a payment method to be activated or
-- deactivated (never hard-deleted once a payment references it), but the original schema modelled
-- payment_method as a fixed set with no status flag. This adds the flag, defaulting every existing
-- row to Active so the change is transparent.
--
-- Idempotent: guarded so re-running does nothing on a DB that already has the column.

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'payment_method'
    AND COLUMN_NAME = 'status'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `payment_method` ADD COLUMN `status` ENUM(''Active'',''Inactive'') NOT NULL DEFAULT ''Active'' AFTER `method_name`',
  'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
