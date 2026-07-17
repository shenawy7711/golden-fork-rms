-- ============================================================================
--  Golden Fork RMS — canonical database schema (18 tables, 3NF, InnoDB/utf8mb4)
--  Source: hand-built in DBeaver, reconciled against specs/.../data-model.md.
--
--  Two corrections applied vs. the initial DBeaver build (see chat 2026-07-15):
--    1. staff.user_id is NULLABLE  — a staff record may exist without a login
--       account (FR-23, BR-26). It was NOT NULL, which forced every staff member
--       to also be a system user.
--    2. user_account.password_hash is NOT UNIQUE — a uniqueness constraint on a
--       password hash is semantically wrong; it was removed.
--
--  Tables are ordered so every foreign-key target is created first.
--  Run against a fresh server, then load db/seed.sql.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `rms` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `rms`;

-- --- reference / independent tables (no FKs) ---------------------------------

CREATE TABLE `dining_table` (
  `table_id` int NOT NULL AUTO_INCREMENT,
  `label` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `capacity` int NOT NULL,
  `status` enum('Free','Occupied','Reserved','Needs Cleaning') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Free',
  PRIMARY KEY (`table_id`),
  UNIQUE KEY `label` (`label`),
  CONSTRAINT `chk_table_capacity` CHECK ((`capacity` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `menu_category` (
  `category_id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_order` int DEFAULT NULL,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_method` (
  `method_id` int NOT NULL AUTO_INCREMENT,
  `method_name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('Active','Inactive') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Active',
  PRIMARY KEY (`method_id`),
  UNIQUE KEY `method_name` (`method_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `role` (
  `role_id` int NOT NULL AUTO_INCREMENT,
  `role_name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `Role_unique` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_item` (
  `stock_item_id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `unit_of_measure` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reorder_level` decimal(10,3) NOT NULL,
  `quantity_on_hand` decimal(10,3) NOT NULL DEFAULT '0.000',
  `status` enum('Active','Inactive') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Active',
  PRIMARY KEY (`stock_item_id`),
  UNIQUE KEY `name` (`name`),
  CONSTRAINT `chk_onhand_nonneg` CHECK ((`quantity_on_hand` >= 0)),
  CONSTRAINT `chk_reorder_nonneg` CHECK ((`reorder_level` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `supplier` (
  `supplier_id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact_person` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('Active','Inactive') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Active',
  PRIMARY KEY (`supplier_id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --- tables with foreign keys ------------------------------------------------

CREATE TABLE `menu_item` (
  `item_id` int NOT NULL AUTO_INCREMENT,
  `category_id` int NOT NULL,
  `name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `availability` enum('Available','Unavailable') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Available',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uq_item_per_category` (`category_id`,`name`),
  CONSTRAINT `menu_item_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `menu_category` (`category_id`),
  CONSTRAINT `chk_item_price_nonneg` CHECK ((`price` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_account` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_id` int NOT NULL,
  `status` enum('Active','Inactive') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Active',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `user_account_unique` (`username`),
  KEY `user_account_role_FK` (`role_id`),
  CONSTRAINT `user_account_role_FK` FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `login_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `event_type` enum('LOGIN','LOGOUT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_time` datetime NOT NULL,
  PRIMARY KEY (`event_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `login_event_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `orders` (
  `order_id` int NOT NULL AUTO_INCREMENT,
  `order_number` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_type` enum('Dine-in','Takeaway') COLLATE utf8mb4_unicode_ci NOT NULL,
  `table_id` int DEFAULT NULL,
  `status` enum('Open','Paid/Closed','Cancelled') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Open',
  `created_by` int NOT NULL,
  `created_at` datetime NOT NULL,
  `closed_at` datetime DEFAULT NULL,
  `subtotal` decimal(10,2) NOT NULL DEFAULT '0.00',
  `discount_type` enum('None','Percentage','Fixed') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'None',
  `discount_value` decimal(10,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `tax_rate` decimal(5,4) NOT NULL DEFAULT '0.0000',
  `tax_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `total` decimal(10,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `order_number` (`order_number`),
  KEY `table_id` (`table_id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `orders_ibfk_1` FOREIGN KEY (`table_id`) REFERENCES `dining_table` (`table_id`),
  CONSTRAINT `orders_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `user_account` (`user_id`),
  CONSTRAINT `chk_discount_nonneg` CHECK ((`discount_amount` >= 0)),
  CONSTRAINT `chk_total_nonneg` CHECK ((`total` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment` (
  `payment_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `method_id` int NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `amount_tendered` decimal(10,2) DEFAULT NULL,
  `change_given` decimal(10,2) DEFAULT NULL,
  `paid_at` datetime NOT NULL,
  PRIMARY KEY (`payment_id`),
  UNIQUE KEY `order_id` (`order_id`),
  KEY `method_id` (`method_id`),
  CONSTRAINT `payment_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`),
  CONSTRAINT `payment_ibfk_2` FOREIGN KEY (`method_id`) REFERENCES `payment_method` (`method_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `purchase_order` (
  `po_id` int NOT NULL AUTO_INCREMENT,
  `po_number` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `supplier_id` int NOT NULL,
  `status` enum('Ordered','Partially Received','Received','Cancelled') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Ordered',
  `created_by` int NOT NULL,
  `ordered_at` datetime NOT NULL,
  `expected_date` date DEFAULT NULL,
  PRIMARY KEY (`po_id`),
  UNIQUE KEY `po_number` (`po_number`),
  KEY `supplier_id` (`supplier_id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `purchase_order_ibfk_1` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`supplier_id`),
  CONSTRAINT `purchase_order_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `user_account` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `purchase_order_item` (
  `po_item_id` int NOT NULL AUTO_INCREMENT,
  `po_id` int NOT NULL,
  `stock_item_id` int NOT NULL,
  `ordered_qty` decimal(10,3) NOT NULL,
  `received_qty` decimal(10,3) NOT NULL DEFAULT '0.000',
  `unit_cost` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`po_item_id`),
  KEY `po_id` (`po_id`),
  KEY `stock_item_id` (`stock_item_id`),
  CONSTRAINT `purchase_order_item_ibfk_1` FOREIGN KEY (`po_id`) REFERENCES `purchase_order` (`po_id`) ON DELETE CASCADE,
  CONSTRAINT `purchase_order_item_ibfk_2` FOREIGN KEY (`stock_item_id`) REFERENCES `stock_item` (`stock_item_id`),
  CONSTRAINT `chk_ordered_pos` CHECK ((`ordered_qty` > 0)),
  CONSTRAINT `chk_received_range` CHECK (((`received_qty` >= 0) and (`received_qty` <= `ordered_qty`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reservation` (
  `reservation_id` int NOT NULL AUTO_INCREMENT,
  `table_id` int NOT NULL,
  `customer_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact_phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reservation_datetime` datetime NOT NULL,
  `duration_minutes` int NOT NULL DEFAULT '90',
  `party_size` int NOT NULL,
  `status` enum('Booked','Seated','Completed','Cancelled','No-Show') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Booked',
  `created_by` int NOT NULL,
  PRIMARY KEY (`reservation_id`),
  KEY `table_id` (`table_id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `reservation_ibfk_1` FOREIGN KEY (`table_id`) REFERENCES `dining_table` (`table_id`),
  CONSTRAINT `reservation_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `user_account` (`user_id`),
  CONSTRAINT `chk_party_min1` CHECK ((`party_size` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- staff.user_id is NULLABLE: a staff record may exist without a login (FR-23, BR-26).
CREATE TABLE `staff` (
  `staff_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `position` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('Active','Inactive') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Active',
  `user_id` int DEFAULT NULL,
  PRIMARY KEY (`staff_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `staff_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_movement` (
  `movement_id` bigint NOT NULL AUTO_INCREMENT,
  `stock_item_id` int NOT NULL,
  `po_item_id` int DEFAULT NULL,
  `movement_type` enum('Receipt','Adjustment') COLLATE utf8mb4_unicode_ci NOT NULL,
  `quantity_change` decimal(10,3) NOT NULL,
  `moved_at` datetime NOT NULL,
  `moved_by` int NOT NULL,
  PRIMARY KEY (`movement_id`),
  KEY `stock_item_id` (`stock_item_id`),
  KEY `po_item_id` (`po_item_id`),
  KEY `moved_by` (`moved_by`),
  CONSTRAINT `stock_movement_ibfk_1` FOREIGN KEY (`stock_item_id`) REFERENCES `stock_item` (`stock_item_id`),
  CONSTRAINT `stock_movement_ibfk_2` FOREIGN KEY (`po_item_id`) REFERENCES `purchase_order_item` (`po_item_id`),
  CONSTRAINT `stock_movement_ibfk_3` FOREIGN KEY (`moved_by`) REFERENCES `user_account` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `system_config` (
  `config_id` int NOT NULL AUTO_INCREMENT,
  `config_key` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` int DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `config_key` (`config_key`),
  KEY `updated_by` (`updated_by`),
  CONSTRAINT `system_config_ibfk_1` FOREIGN KEY (`updated_by`) REFERENCES `user_account` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `order_item` (
  `order_item_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `item_id` int NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(10,2) NOT NULL,
  `line_total` decimal(10,2) NOT NULL,
  PRIMARY KEY (`order_item_id`),
  KEY `order_id` (`order_id`),
  KEY `item_id` (`item_id`),
  CONSTRAINT `order_item_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE CASCADE,
  CONSTRAINT `order_item_ibfk_2` FOREIGN KEY (`item_id`) REFERENCES `menu_item` (`item_id`),
  CONSTRAINT `chk_qty_min1` CHECK ((`quantity` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
