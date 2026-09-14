-- FACE 连锁店基础结构
-- 适用：现有 face_salon 单店数据库升级为多租户、多区域、多门店结构。
-- 说明：保留 shop_id/role_code 等兼容字段，旧系统可在迁移期间继续运行。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE `tenant` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(32) NOT NULL,
  `name` varchar(120) NOT NULL,
  `legal_name` varchar(180) DEFAULT NULL,
  `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai',
  `currency_code` char(3) NOT NULL DEFAULT 'CNY',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  CONSTRAINT `ck_tenant_status` CHECK (`status` IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连锁品牌/租户';

INSERT INTO `tenant` (`tenant_code`, `name`, `legal_name`)
VALUES ('FACE', 'FACE 美容连锁', 'FACE 美容连锁')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

CREATE TABLE `region` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `region_code` varchar(32) NOT NULL,
  `name` varchar(100) NOT NULL,
  `manager_account_id` bigint unsigned DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_region_code` (`tenant_id`, `region_code`),
  KEY `idx_region_tenant_status` (`tenant_id`, `status`),
  CONSTRAINT `fk_region_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `ck_region_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连锁区域';

INSERT INTO `region` (`tenant_id`, `region_code`, `name`)
SELECT `id`, 'DEFAULT', '默认区域'
FROM `tenant`
WHERE `tenant_code` = 'FACE'
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

ALTER TABLE `shop`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `region_id` bigint unsigned NULL AFTER `tenant_id`,
  ADD COLUMN `shop_code` varchar(32) NULL AFTER `region_id`,
  ADD COLUMN `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER `business_hours`,
  ADD COLUMN `currency_code` char(3) NOT NULL DEFAULT 'CNY' AFTER `timezone`;

UPDATE `shop`
SET
  `tenant_id` = (SELECT `id` FROM `tenant` WHERE `tenant_code` = 'FACE' LIMIT 1),
  `region_id` = (
    SELECT `id` FROM `region`
    WHERE `tenant_id` = (SELECT `id` FROM `tenant` WHERE `tenant_code` = 'FACE' LIMIT 1)
      AND `region_code` = 'DEFAULT'
    LIMIT 1
  ),
  `shop_code` = COALESCE(NULLIF(`shop_code`, ''), CONCAT('SHOP-', LPAD(`id`, 6, '0')));

ALTER TABLE `shop`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  MODIFY COLUMN `shop_code` varchar(32) NOT NULL,
  ADD UNIQUE KEY `uk_shop_tenant_code` (`tenant_id`, `shop_code`),
  ADD KEY `idx_shop_tenant_region_status` (`tenant_id`, `region_id`, `status`),
  ADD CONSTRAINT `fk_shop_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  ADD CONSTRAINT `fk_shop_region` FOREIGN KEY (`region_id`) REFERENCES `region` (`id`);

ALTER TABLE `account`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `home_shop_id` bigint unsigned NULL AFTER `tenant_id`,
  ADD COLUMN `display_name` varchar(80) DEFAULT NULL AFTER `username`,
  ADD COLUMN `must_change_password` tinyint(1) NOT NULL DEFAULT 0 AFTER `status`,
  ADD COLUMN `failed_login_count` smallint unsigned NOT NULL DEFAULT 0 AFTER `must_change_password`,
  ADD COLUMN `locked_until` datetime(3) DEFAULT NULL AFTER `failed_login_count`;

UPDATE `account` a
JOIN `shop` s ON s.`id` = a.`shop_id`
SET a.`tenant_id` = s.`tenant_id`, a.`home_shop_id` = a.`shop_id`;

ALTER TABLE `account`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_account_tenant_status` (`tenant_id`, `status`),
  ADD KEY `idx_account_home_shop` (`home_shop_id`),
  ADD CONSTRAINT `fk_account_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  ADD CONSTRAINT `fk_account_home_shop` FOREIGN KEY (`home_shop_id`) REFERENCES `shop` (`id`);

ALTER TABLE `member`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `home_shop_id` bigint unsigned NULL AFTER `tenant_id`,
  ADD COLUMN `global_member_no` varchar(40) DEFAULT NULL AFTER `member_no`,
  ADD COLUMN `consent_marketing_at` datetime(3) DEFAULT NULL AFTER `notes`,
  ADD COLUMN `consent_privacy_at` datetime(3) DEFAULT NULL AFTER `consent_marketing_at`;

UPDATE `member` m
JOIN `shop` s ON s.`id` = m.`shop_id`
SET
  m.`tenant_id` = s.`tenant_id`,
  m.`home_shop_id` = m.`shop_id`,
  m.`global_member_no` = COALESCE(NULLIF(m.`global_member_no`, ''), CONCAT('GM', LPAD(m.`id`, 12, '0')));

ALTER TABLE `member`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  MODIFY COLUMN `global_member_no` varchar(40) NOT NULL,
  ADD UNIQUE KEY `uk_member_tenant_global_no` (`tenant_id`, `global_member_no`),
  ADD KEY `idx_member_tenant_phone` (`tenant_id`, `phone`),
  ADD KEY `idx_member_home_shop` (`home_shop_id`),
  ADD CONSTRAINT `fk_member_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  ADD CONSTRAINT `fk_member_home_shop` FOREIGN KEY (`home_shop_id`) REFERENCES `shop` (`id`);

ALTER TABLE `staff`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `home_shop_id` bigint unsigned NULL AFTER `tenant_id`;

UPDATE `staff` s
JOIN `shop` sh ON sh.`id` = s.`shop_id`
SET s.`tenant_id` = sh.`tenant_id`, s.`home_shop_id` = s.`shop_id`;

ALTER TABLE `staff`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_staff_tenant_status` (`tenant_id`, `status`),
  ADD KEY `idx_staff_home_shop` (`home_shop_id`),
  ADD CONSTRAINT `fk_staff_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  ADD CONSTRAINT `fk_staff_home_shop` FOREIGN KEY (`home_shop_id`) REFERENCES `shop` (`id`);

ALTER TABLE `service_category`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `is_chain_standard` tinyint(1) NOT NULL DEFAULT 0 AFTER `name`;

UPDATE `service_category` c
JOIN `shop` s ON s.`id` = c.`shop_id`
SET c.`tenant_id` = s.`tenant_id`;

ALTER TABLE `service_category`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_service_category_tenant` (`tenant_id`, `status`),
  ADD CONSTRAINT `fk_service_category_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `service_item`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `is_chain_standard` tinyint(1) NOT NULL DEFAULT 0 AFTER `is_featured`;

UPDATE `service_item` i
JOIN `shop` s ON s.`id` = i.`shop_id`
SET i.`tenant_id` = s.`tenant_id`;

ALTER TABLE `service_item`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_service_item_tenant_status` (`tenant_id`, `status`),
  ADD CONSTRAINT `fk_service_item_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `product_category`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`;

UPDATE `product_category` c
JOIN `shop` s ON s.`id` = c.`shop_id`
SET c.`tenant_id` = s.`tenant_id`;

ALTER TABLE `product_category`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_product_category_tenant` (`tenant_id`, `status`),
  ADD CONSTRAINT `fk_product_category_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `product`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`;

UPDATE `product` p
JOIN `shop` s ON s.`id` = p.`shop_id`
SET p.`tenant_id` = s.`tenant_id`;

ALTER TABLE `product`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_product_tenant_status` (`tenant_id`, `status`),
  ADD CONSTRAINT `fk_product_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `appointment`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`;

UPDATE `appointment` a
JOIN `shop` s ON s.`id` = a.`shop_id`
SET a.`tenant_id` = s.`tenant_id`;

ALTER TABLE `appointment`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_appointment_tenant_time` (`tenant_id`, `start_at`),
  ADD CONSTRAINT `fk_appointment_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `sales_order`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `business_date` date DEFAULT NULL AFTER `order_no`,
  ADD COLUMN `currency_code` char(3) NOT NULL DEFAULT 'CNY' AFTER `paid_amount`;

UPDATE `sales_order` o
JOIN `shop` s ON s.`id` = o.`shop_id`
SET
  o.`tenant_id` = s.`tenant_id`,
  o.`business_date` = COALESCE(o.`business_date`, DATE(o.`created_at`));

ALTER TABLE `sales_order`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_sales_order_tenant_business_date` (`tenant_id`, `business_date`),
  ADD CONSTRAINT `fk_sales_order_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `inventory_movement`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `idempotency_key` varchar(80) DEFAULT NULL AFTER `reference_no`;

UPDATE `inventory_movement` m
JOIN `shop` s ON s.`id` = m.`shop_id`
SET m.`tenant_id` = s.`tenant_id`;

ALTER TABLE `inventory_movement`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD UNIQUE KEY `uk_inventory_tenant_idempotency` (`tenant_id`, `idempotency_key`),
  ADD KEY `idx_inventory_tenant_time` (`tenant_id`, `created_at`),
  ADD CONSTRAINT `fk_inventory_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

ALTER TABLE `audit_log`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `request_id` varchar(80) DEFAULT NULL AFTER `ip_address`,
  ADD COLUMN `metadata` json DEFAULT NULL AFTER `request_id`;

UPDATE `audit_log` l
JOIN `shop` s ON s.`id` = l.`shop_id`
SET l.`tenant_id` = s.`tenant_id`;

ALTER TABLE `audit_log`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  MODIFY COLUMN `shop_id` bigint unsigned NULL,
  ADD KEY `idx_audit_tenant_time` (`tenant_id`, `created_at`),
  ADD KEY `idx_audit_request` (`request_id`),
  ADD CONSTRAINT `fk_audit_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`);

CREATE TABLE `role_definition` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `role_code` varchar(40) NOT NULL,
  `role_name` varchar(80) NOT NULL,
  `scope_type` varchar(20) NOT NULL DEFAULT 'SHOP',
  `is_system` tinyint(1) NOT NULL DEFAULT 0,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_tenant_code` (`tenant_id`, `role_code`),
  CONSTRAINT `fk_role_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `ck_role_scope` CHECK (`scope_type` IN ('TENANT', 'REGION', 'SHOP', 'SELF')),
  CONSTRAINT `ck_role_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户角色定义';

CREATE TABLE `permission_definition` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `permission_code` varchar(80) NOT NULL,
  `permission_name` varchar(100) NOT NULL,
  `module_code` varchar(40) NOT NULL,
  `risk_level` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  CONSTRAINT `ck_permission_risk` CHECK (`risk_level` IN ('NORMAL', 'SENSITIVE', 'CRITICAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限定义';

CREATE TABLE `role_permission` (
  `role_id` bigint unsigned NOT NULL,
  `permission_id` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`role_id`, `permission_id`),
  CONSTRAINT `fk_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `role_definition` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `permission_definition` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限';

CREATE TABLE `account_shop_role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `region_id` bigint unsigned DEFAULT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `role_id` bigint unsigned NOT NULL,
  `effective_from` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `effective_to` datetime(3) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_scope_role` (`account_id`, `region_id`, `shop_id`, `role_id`),
  KEY `idx_account_shop_role_scope` (`tenant_id`, `region_id`, `shop_id`, `status`),
  CONSTRAINT `fk_account_shop_role_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_account_shop_role_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_account_shop_role_region` FOREIGN KEY (`region_id`) REFERENCES `region` (`id`),
  CONSTRAINT `fk_account_shop_role_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_account_shop_role_role` FOREIGN KEY (`role_id`) REFERENCES `role_definition` (`id`),
  CONSTRAINT `ck_account_shop_role_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号在区域/门店的角色';

CREATE TABLE `staff_shop_assignment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `staff_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `assignment_type` varchar(20) NOT NULL DEFAULT 'PRIMARY',
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_shop_assignment` (`staff_id`, `shop_id`, `effective_from`),
  KEY `idx_staff_assignment_shop` (`tenant_id`, `shop_id`, `status`),
  CONSTRAINT `fk_staff_assignment_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_staff_assignment_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_staff_assignment_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_staff_assignment_type` CHECK (`assignment_type` IN ('PRIMARY', 'SUPPORT', 'TEMPORARY')),
  CONSTRAINT `ck_staff_assignment_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工跨门店任职';

CREATE TABLE `member_shop_profile` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `first_visit_at` datetime(3) DEFAULT NULL,
  `last_visit_at` datetime(3) DEFAULT NULL,
  `visit_count` int unsigned NOT NULL DEFAULT 0,
  `source` varchar(40) DEFAULT NULL,
  `consultant_staff_id` bigint unsigned DEFAULT NULL,
  `shop_notes` varchar(1000) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_shop_profile` (`member_id`, `shop_id`),
  KEY `idx_member_shop_profile_shop` (`tenant_id`, `shop_id`, `status`),
  CONSTRAINT `fk_member_shop_profile_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_member_shop_profile_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_member_shop_profile_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_member_shop_profile_consultant` FOREIGN KEY (`consultant_staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `ck_member_shop_profile_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员门店关系';

CREATE TABLE `member_account` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `account_type` varchar(20) NOT NULL,
  `currency_code` char(3) NOT NULL DEFAULT 'CNY',
  `balance` decimal(14,2) NOT NULL DEFAULT 0.00,
  `version` int unsigned NOT NULL DEFAULT 0,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_account_type` (`tenant_id`, `member_id`, `account_type`),
  CONSTRAINT `fk_member_account_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_member_account_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `ck_member_account_type` CHECK (`account_type` IN ('BALANCE', 'GIFT_BALANCE', 'POINTS')),
  CONSTRAINT `ck_member_account_status` CHECK (`status` IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员资金/积分账户';

CREATE TABLE `member_account_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `entry_type` varchar(30) NOT NULL,
  `amount_delta` decimal(14,2) NOT NULL,
  `balance_after` decimal(14,2) NOT NULL,
  `reference_type` varchar(40) DEFAULT NULL,
  `reference_id` bigint unsigned DEFAULT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_ledger_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_member_ledger_account_time` (`account_id`, `created_at`),
  CONSTRAINT `fk_member_ledger_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_member_ledger_account` FOREIGN KEY (`account_id`) REFERENCES `member_account` (`id`),
  CONSTRAINT `fk_member_ledger_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_member_ledger_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_member_ledger_delta` CHECK (`amount_delta` <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员账户不可变流水';

CREATE TABLE `shop_service_price` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `service_id` bigint unsigned NOT NULL,
  `list_price` decimal(12,2) NOT NULL,
  `member_price` decimal(12,2) DEFAULT NULL,
  `bookable` tinyint(1) NOT NULL DEFAULT 1,
  `effective_from` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `effective_to` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_service_price_version` (`shop_id`, `service_id`, `effective_from`),
  KEY `idx_shop_service_price_active` (`tenant_id`, `shop_id`, `bookable`, `effective_to`),
  CONSTRAINT `fk_shop_service_price_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_shop_service_price_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_shop_service_price_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `ck_shop_service_price_amount` CHECK (`list_price` >= 0 AND (`member_price` IS NULL OR `member_price` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店项目价格';

CREATE TABLE `stock_location` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `location_code` varchar(40) NOT NULL,
  `name` varchar(100) NOT NULL,
  `location_type` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_location_code` (`tenant_id`, `location_code`),
  KEY `idx_stock_location_shop` (`shop_id`, `status`),
  CONSTRAINT `fk_stock_location_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_stock_location_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_stock_location_type` CHECK (`location_type` IN ('HEADQUARTERS', 'SHOP', 'ROOM')),
  CONSTRAINT `ck_stock_location_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存地点';

CREATE TABLE `stock_balance` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `location_id` bigint unsigned NOT NULL,
  `product_id` bigint unsigned NOT NULL,
  `quantity_on_hand` decimal(14,3) NOT NULL DEFAULT 0.000,
  `quantity_reserved` decimal(14,3) NOT NULL DEFAULT 0.000,
  `version` int unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_balance_location_product` (`location_id`, `product_id`),
  KEY `idx_stock_balance_tenant_product` (`tenant_id`, `product_id`),
  CONSTRAINT `fk_stock_balance_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_stock_balance_location` FOREIGN KEY (`location_id`) REFERENCES `stock_location` (`id`),
  CONSTRAINT `fk_stock_balance_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `ck_stock_balance_values` CHECK (`quantity_on_hand` >= 0 AND `quantity_reserved` >= 0 AND `quantity_reserved` <= `quantity_on_hand`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存实时余额';

CREATE TABLE `payment_transaction` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `order_id` bigint unsigned NOT NULL,
  `payment_no` varchar(48) NOT NULL,
  `payment_method` varchar(30) NOT NULL,
  `amount` decimal(14,2) NOT NULL,
  `currency_code` char(3) NOT NULL DEFAULT 'CNY',
  `external_transaction_no` varchar(100) DEFAULT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `paid_at` datetime(3) DEFAULT NULL,
  `created_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`tenant_id`, `payment_no`),
  UNIQUE KEY `uk_payment_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_payment_order_status` (`order_id`, `status`),
  CONSTRAINT `fk_payment_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_payment_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_payment_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_payment_amount` CHECK (`amount` > 0),
  CONSTRAINT `ck_payment_status` CHECK (`status` IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付流水';

CREATE TABLE `refund_transaction` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `order_id` bigint unsigned NOT NULL,
  `payment_id` bigint unsigned DEFAULT NULL,
  `refund_no` varchar(48) NOT NULL,
  `amount` decimal(14,2) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `approved_by` bigint unsigned DEFAULT NULL,
  `refunded_at` datetime(3) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`tenant_id`, `refund_no`),
  UNIQUE KEY `uk_refund_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_refund_order_status` (`order_id`, `status`),
  CONSTRAINT `fk_refund_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_refund_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_refund_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_refund_payment` FOREIGN KEY (`payment_id`) REFERENCES `payment_transaction` (`id`),
  CONSTRAINT `fk_refund_approver` FOREIGN KEY (`approved_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_refund_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_refund_amount` CHECK (`amount` > 0),
  CONSTRAINT `ck_refund_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'SUCCESS', 'REJECTED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款流水';

CREATE TABLE `idempotency_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `operation_code` varchar(80) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `response_code` int DEFAULT NULL,
  `response_body` json DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PROCESSING',
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency_tenant_key` (`tenant_id`, `idempotency_key`),
  KEY `idx_idempotency_expiry` (`expires_at`, `status`),
  CONSTRAINT `fk_idempotency_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `ck_idempotency_status` CHECK (`status` IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口幂等记录';

INSERT INTO `role_definition` (`tenant_id`, `role_code`, `role_name`, `scope_type`, `is_system`)
SELECT t.`id`, r.`role_code`, r.`role_name`, r.`scope_type`, 1
FROM `tenant` t
JOIN (
  SELECT 'OWNER' AS role_code, '品牌负责人' AS role_name, 'TENANT' AS scope_type
  UNION ALL SELECT 'REGIONAL_MANAGER', '区域经理', 'REGION'
  UNION ALL SELECT 'MANAGER', '店长', 'SHOP'
  UNION ALL SELECT 'FRONT_DESK', '前台', 'SHOP'
  UNION ALL SELECT 'BEAUTICIAN', '美容师', 'SELF'
  UNION ALL SELECT 'WAREHOUSE', '仓管', 'SHOP'
  UNION ALL SELECT 'FINANCE', '财务', 'TENANT'
  UNION ALL SELECT 'MEMBER', '会员', 'SELF'
) r
WHERE t.`tenant_code` = 'FACE';

INSERT INTO `permission_definition` (`permission_code`, `permission_name`, `module_code`, `risk_level`) VALUES
  ('dashboard:view', '查看经营工作台', 'dashboard', 'NORMAL'),
  ('tenant:manage', '管理品牌设置', 'tenant', 'CRITICAL'),
  ('shop:view', '查看门店', 'shop', 'NORMAL'),
  ('shop:manage', '管理门店', 'shop', 'SENSITIVE'),
  ('staff:view', '查看员工', 'staff', 'NORMAL'),
  ('staff:manage', '管理员工', 'staff', 'SENSITIVE'),
  ('member:view', '查看会员', 'member', 'SENSITIVE'),
  ('member:manage', '管理会员', 'member', 'SENSITIVE'),
  ('appointment:view', '查看预约', 'appointment', 'NORMAL'),
  ('appointment:manage', '管理预约', 'appointment', 'NORMAL'),
  ('service:manage', '管理项目与价格', 'service', 'SENSITIVE'),
  ('order:view', '查看订单', 'order', 'SENSITIVE'),
  ('order:manage', '创建与结算订单', 'order', 'SENSITIVE'),
  ('refund:approve', '审批退款', 'order', 'CRITICAL'),
  ('inventory:view', '查看库存', 'inventory', 'NORMAL'),
  ('inventory:manage', '调整库存', 'inventory', 'CRITICAL'),
  ('finance:view', '查看财务数据', 'finance', 'CRITICAL'),
  ('audit:view', '查看审计日志', 'audit', 'CRITICAL');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
WHERE r.`role_code` = 'OWNER';

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
WHERE r.`role_code` = 'MANAGER'
  AND p.`permission_code` IN (
    'dashboard:view', 'shop:view', 'staff:view', 'staff:manage',
    'member:view', 'member:manage', 'appointment:view', 'appointment:manage',
    'service:manage', 'order:view', 'order:manage',
    'inventory:view', 'inventory:manage'
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
WHERE r.`role_code` = 'FRONT_DESK'
  AND p.`permission_code` IN (
    'dashboard:view', 'staff:view', 'member:view', 'member:manage',
    'appointment:view', 'appointment:manage', 'order:view', 'order:manage',
    'inventory:view'
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
WHERE r.`role_code` = 'BEAUTICIAN'
  AND p.`permission_code` IN (
    'dashboard:view', 'member:view', 'appointment:view',
    'appointment:manage', 'inventory:view'
  );

INSERT INTO `account_shop_role` (`tenant_id`, `account_id`, `shop_id`, `role_id`)
SELECT
  a.`tenant_id`,
  a.`id`,
  CASE WHEN r.`scope_type` = 'TENANT' THEN NULL ELSE a.`home_shop_id` END,
  r.`id`
FROM `account` a
JOIN `role_definition` r
  ON r.`tenant_id` = a.`tenant_id`
 AND r.`role_code` = a.`role_code`;

INSERT INTO `staff_shop_assignment` (
  `tenant_id`, `staff_id`, `shop_id`, `assignment_type`, `effective_from`
)
SELECT `tenant_id`, `id`, `home_shop_id`, 'PRIMARY', COALESCE(`hire_date`, DATE(`created_at`))
FROM `staff`;

INSERT INTO `member_shop_profile` (
  `tenant_id`, `member_id`, `shop_id`, `source`, `status`
)
SELECT `tenant_id`, `id`, `home_shop_id`, `source`, `status`
FROM `member`;

INSERT INTO `member_account` (`tenant_id`, `member_id`, `account_type`, `balance`)
SELECT `tenant_id`, `id`, 'POINTS', CAST(`points` AS DECIMAL(14,2))
FROM `member`;

INSERT INTO `shop_service_price` (
  `tenant_id`, `shop_id`, `service_id`, `list_price`, `member_price`
)
SELECT `tenant_id`, `shop_id`, `id`, `list_price`, `member_price`
FROM `service_item`;

INSERT INTO `stock_location` (`tenant_id`, `shop_id`, `location_code`, `name`, `location_type`)
SELECT `tenant_id`, `id`, CONCAT('SHOP-', LPAD(`id`, 6, '0'), '-MAIN'), CONCAT(`name`, '主仓'), 'SHOP'
FROM `shop`;

INSERT INTO `stock_balance` (`tenant_id`, `location_id`, `product_id`, `quantity_on_hand`)
SELECT p.`tenant_id`, l.`id`, p.`id`, p.`stock_quantity`
FROM `product` p
JOIN `stock_location` l
  ON l.`shop_id` = p.`shop_id`
 AND l.`location_type` = 'SHOP';
