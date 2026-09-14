CREATE TABLE `purchase_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `purchase_order_no` varchar(48) NOT NULL,
  `supplier_name` varchar(160) NOT NULL,
  `expected_date` date DEFAULT NULL,
  `currency_code` char(3) NOT NULL DEFAULT 'CNY',
  `total_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `status` varchar(24) NOT NULL DEFAULT 'DRAFT',
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `decision_note` varchar(500) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `approved_by` bigint unsigned DEFAULT NULL,
  `submitted_at` datetime(3) DEFAULT NULL,
  `approved_at` datetime(3) DEFAULT NULL,
  `closed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_order_no` (`tenant_id`, `purchase_order_no`),
  UNIQUE KEY `uk_purchase_order_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_purchase_order_shop_status`
    (`tenant_id`, `shop_id`, `status`, `created_at`),
  CONSTRAINT `fk_purchase_order_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_purchase_order_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_purchase_order_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_purchase_order_approver`
    FOREIGN KEY (`approved_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_purchase_order_total`
    CHECK (`total_amount` >= 0),
  CONSTRAINT `ck_purchase_order_status`
    CHECK (`status` IN (
      'DRAFT', 'SUBMITTED', 'APPROVED',
      'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='采购单';

CREATE TABLE `purchase_order_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `purchase_order_id` bigint unsigned NOT NULL,
  `product_id` bigint unsigned NOT NULL,
  `sku_snapshot` varchar(64) NOT NULL,
  `product_name_snapshot` varchar(160) NOT NULL,
  `unit_name_snapshot` varchar(40) NOT NULL,
  `ordered_quantity` decimal(14,3) NOT NULL,
  `received_quantity` decimal(14,3) NOT NULL DEFAULT 0.000,
  `unit_cost` decimal(14,4) NOT NULL,
  `line_amount` decimal(14,2) NOT NULL,
  `sort_order` int unsigned NOT NULL DEFAULT 0,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_order_product` (`purchase_order_id`, `product_id`),
  KEY `idx_purchase_item_product` (`product_id`, `created_at`),
  CONSTRAINT `fk_purchase_item_order`
    FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
  CONSTRAINT `fk_purchase_item_product`
    FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `ck_purchase_item_quantity`
    CHECK (
      `ordered_quantity` > 0
      AND `received_quantity` >= 0
      AND `received_quantity` <= `ordered_quantity`
    ),
  CONSTRAINT `ck_purchase_item_cost`
    CHECK (`unit_cost` >= 0 AND `line_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='采购单明细';

CREATE TABLE `stock_batch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `location_id` bigint unsigned NOT NULL,
  `product_id` bigint unsigned NOT NULL,
  `batch_no` varchar(64) NOT NULL,
  `vendor_batch_no` varchar(100) DEFAULT NULL,
  `produced_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `unit_cost` decimal(14,4) DEFAULT NULL,
  `quantity_received` decimal(14,3) NOT NULL,
  `quantity_on_hand` decimal(14,3) NOT NULL,
  `quantity_reserved` decimal(14,3) NOT NULL DEFAULT 0.000,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint unsigned DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_batch_no` (`tenant_id`, `batch_no`),
  KEY `idx_stock_batch_fefo`
    (`tenant_id`, `location_id`, `product_id`, `status`, `expiry_date`, `created_at`),
  KEY `idx_stock_batch_source`
    (`tenant_id`, `source_type`, `source_id`),
  CONSTRAINT `fk_stock_batch_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_stock_batch_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_stock_batch_location`
    FOREIGN KEY (`location_id`) REFERENCES `stock_location` (`id`),
  CONSTRAINT `fk_stock_batch_product`
    FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_stock_batch_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_stock_batch_quantities`
    CHECK (
      `quantity_received` > 0
      AND `quantity_on_hand` >= 0
      AND `quantity_reserved` >= 0
      AND `quantity_reserved` <= `quantity_on_hand`
      AND `quantity_on_hand` <= `quantity_received`
    ),
  CONSTRAINT `ck_stock_batch_dates`
    CHECK (
      `produced_date` IS NULL
      OR `expiry_date` IS NULL
      OR `expiry_date` >= `produced_date`
    ),
  CONSTRAINT `ck_stock_batch_status`
    CHECK (`status` IN ('ACTIVE', 'FROZEN', 'DEPLETED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='库存批次';

CREATE TABLE `stock_batch_movement` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `stock_batch_id` bigint unsigned NOT NULL,
  `movement_type` varchar(32) NOT NULL,
  `quantity_delta` decimal(14,3) NOT NULL,
  `balance_after` decimal(14,3) NOT NULL,
  `reference_type` varchar(40) NOT NULL,
  `reference_id` bigint unsigned DEFAULT NULL,
  `business_key` varchar(120) NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_batch_movement_business`
    (`tenant_id`, `business_key`),
  KEY `idx_stock_batch_movement_batch`
    (`stock_batch_id`, `created_at`, `id`),
  KEY `idx_stock_batch_movement_reference`
    (`tenant_id`, `reference_type`, `reference_id`),
  CONSTRAINT `fk_stock_batch_movement_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_stock_batch_movement_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_stock_batch_movement_batch`
    FOREIGN KEY (`stock_batch_id`) REFERENCES `stock_batch` (`id`),
  CONSTRAINT `fk_stock_batch_movement_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_stock_batch_movement_delta`
    CHECK (`quantity_delta` <> 0 AND `balance_after` >= 0),
  CONSTRAINT `ck_stock_batch_movement_type`
    CHECK (`movement_type` IN (
      'MIGRATION_OPENING', 'PURCHASE_IN', 'RETURN_IN',
      'SALE_OUT', 'SERVICE_USE', 'TRANSFER_IN', 'TRANSFER_OUT',
      'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'REVERSAL'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='库存批次不可变流水';

CREATE TABLE `purchase_receipt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `purchase_order_id` bigint unsigned NOT NULL,
  `receipt_no` varchar(48) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `received_at` datetime(3) NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_receipt_no` (`tenant_id`, `receipt_no`),
  UNIQUE KEY `uk_purchase_receipt_idempotency`
    (`tenant_id`, `idempotency_key`),
  KEY `idx_purchase_receipt_order`
    (`purchase_order_id`, `received_at`, `id`),
  CONSTRAINT `fk_purchase_receipt_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_purchase_receipt_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_purchase_receipt_order`
    FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`),
  CONSTRAINT `fk_purchase_receipt_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='采购收货单';

CREATE TABLE `purchase_receipt_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `purchase_receipt_id` bigint unsigned NOT NULL,
  `purchase_order_item_id` bigint unsigned NOT NULL,
  `product_id` bigint unsigned NOT NULL,
  `location_id` bigint unsigned NOT NULL,
  `stock_batch_id` bigint unsigned NOT NULL,
  `inventory_movement_id` bigint unsigned NOT NULL,
  `received_quantity` decimal(14,3) NOT NULL,
  `unit_cost` decimal(14,4) NOT NULL,
  `vendor_batch_no` varchar(100) DEFAULT NULL,
  `produced_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_receipt_item`
    (`purchase_receipt_id`, `purchase_order_item_id`),
  UNIQUE KEY `uk_purchase_receipt_inventory_movement`
    (`inventory_movement_id`),
  KEY `idx_purchase_receipt_item_order`
    (`purchase_order_item_id`, `created_at`),
  CONSTRAINT `fk_purchase_receipt_item_receipt`
    FOREIGN KEY (`purchase_receipt_id`) REFERENCES `purchase_receipt` (`id`),
  CONSTRAINT `fk_purchase_receipt_item_order_item`
    FOREIGN KEY (`purchase_order_item_id`) REFERENCES `purchase_order_item` (`id`),
  CONSTRAINT `fk_purchase_receipt_item_product`
    FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_purchase_receipt_item_location`
    FOREIGN KEY (`location_id`) REFERENCES `stock_location` (`id`),
  CONSTRAINT `fk_purchase_receipt_item_batch`
    FOREIGN KEY (`stock_batch_id`) REFERENCES `stock_batch` (`id`),
  CONSTRAINT `fk_purchase_receipt_item_inventory_movement`
    FOREIGN KEY (`inventory_movement_id`) REFERENCES `inventory_movement` (`id`),
  CONSTRAINT `ck_purchase_receipt_item_quantity`
    CHECK (`received_quantity` > 0 AND `unit_cost` >= 0),
  CONSTRAINT `ck_purchase_receipt_item_dates`
    CHECK (
      `produced_date` IS NULL
      OR `expiry_date` IS NULL
      OR `expiry_date` >= `produced_date`
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='采购收货明细';

INSERT INTO `stock_batch` (
  `tenant_id`, `shop_id`, `location_id`, `product_id`,
  `batch_no`, `vendor_batch_no`, `produced_date`, `expiry_date`,
  `unit_cost`, `quantity_received`, `quantity_on_hand`, `quantity_reserved`,
  `status`, `source_type`, `source_id`, `version`, `created_by`, `created_at`
)
SELECT
  sb.`tenant_id`,
  sl.`shop_id`,
  sb.`location_id`,
  sb.`product_id`,
  CONCAT('OPEN-', sb.`tenant_id`, '-', sb.`location_id`, '-', sb.`product_id`),
  NULL,
  NULL,
  NULL,
  NULL,
  sb.`quantity_on_hand`,
  sb.`quantity_on_hand`,
  sb.`quantity_reserved`,
  CASE
    WHEN sb.`quantity_on_hand` = 0 THEN 'DEPLETED'
    ELSE 'ACTIVE'
  END,
  'MIGRATION_OPENING',
  sb.`id`,
  0,
  (
    SELECT MIN(a.`id`)
    FROM `account` a
    WHERE a.`tenant_id` = sb.`tenant_id`
  ),
  TIMESTAMP('2026-07-30 00:00:00')
FROM `stock_balance` sb
JOIN `stock_location` sl
  ON sl.`id` = sb.`location_id`
 AND sl.`tenant_id` = sb.`tenant_id`
WHERE sb.`quantity_on_hand` > 0
  AND NOT EXISTS (
    SELECT 1
    FROM `stock_batch` existing
    WHERE existing.`tenant_id` = sb.`tenant_id`
      AND existing.`source_type` = 'MIGRATION_OPENING'
      AND existing.`source_id` = sb.`id`
  );

INSERT INTO `stock_batch_movement` (
  `tenant_id`, `shop_id`, `stock_batch_id`, `movement_type`,
  `quantity_delta`, `balance_after`, `reference_type`, `reference_id`,
  `business_key`, `remark`, `created_by`, `created_at`
)
SELECT
  b.`tenant_id`,
  b.`shop_id`,
  b.`id`,
  'MIGRATION_OPENING',
  b.`quantity_received`,
  b.`quantity_on_hand`,
  'STOCK_BALANCE',
  b.`source_id`,
  CONCAT('M4-BATCH-OPENING:', b.`source_id`),
  'M4 批次库存迁移期初余额',
  b.`created_by`,
  b.`created_at`
FROM `stock_batch` b
WHERE b.`source_type` = 'MIGRATION_OPENING'
  AND NOT EXISTS (
    SELECT 1
    FROM `stock_batch_movement` movement
    WHERE movement.`tenant_id` = b.`tenant_id`
      AND movement.`business_key` = CONCAT('M4-BATCH-OPENING:', b.`source_id`)
  );

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('purchase:view', '查看采购与批次', 'purchase', 'NORMAL'),
  ('purchase:manage', '维护采购单', 'purchase', 'SENSITIVE'),
  ('purchase:approve', '审批采购单', 'purchase', 'CRITICAL'),
  ('purchase:receive', '采购收货入库', 'purchase', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'purchase:view'
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'WAREHOUSE', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'purchase:manage'
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'WAREHOUSE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'purchase:approve'
WHERE r.`role_code` IN ('OWNER', 'MANAGER')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'purchase:receive'
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'WAREHOUSE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
