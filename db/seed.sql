-- Golden Fork RMS - baseline seed data. Run once against the `rms` database.
-- In DBeaver: make sure the database dropdown shows `rms`, then press Alt+X.
-- Idempotent: INSERT IGNORE skips rows that already exist, so re-running is safe.

-- 1. Roles (FR-02)
INSERT IGNORE INTO role (role_name) VALUES ('Administrator'), ('Manager'), ('Cashier');

-- 2. Payment methods (FR-15)
INSERT IGNORE INTO payment_method (method_name) VALUES ('Cash'), ('Card'), ('Other');

-- 3. System configuration (FR-31). tax_rate is a decimal fraction: 14 percent = 0.1400
INSERT IGNORE INTO system_config (config_key, config_value, updated_by, updated_at) VALUES ('tax_rate','0.1400',NULL,NOW()), ('idle_timeout_min','15',NULL,NOW()), ('login_max_attempts','5',NULL,NOW()), ('reservation_slot_minutes','90',NULL,NOW()), ('discount_approval_threshold','20.00',NULL,NOW());

-- 4. First Administrator (FR-03, BR-06). Login: admin / admin123 (BCrypt hash of admin123). Change it after first login.
INSERT IGNORE INTO user_account (username, password_hash, full_name, role_id, status, created_at) SELECT 'admin','$2a$10$Hg4y87rntmSjf4wbo1ErqOwgH4zN4jp44mkRiVGL7DeS07j8ZG3Fm','System Administrator', r.role_id, 'Active', NOW() FROM role r WHERE r.role_name = 'Administrator';
