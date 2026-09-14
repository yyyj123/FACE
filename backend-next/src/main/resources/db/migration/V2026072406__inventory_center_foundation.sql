ALTER TABLE `inventory_movement`
  ADD COLUMN `location_id` bigint unsigned NULL AFTER `shop_id`,
  ADD KEY `idx_inventory_location_time` (`location_id`, `created_at`);

UPDATE `inventory_movement` im
JOIN `stock_location` sl
  ON sl.`tenant_id` = im.`tenant_id`
 AND sl.`shop_id` = im.`shop_id`
 AND sl.`location_type` = 'SHOP'
SET im.`location_id` = sl.`id`
WHERE im.`location_id` IS NULL;

ALTER TABLE `inventory_movement`
  MODIFY COLUMN `location_id` bigint unsigned NOT NULL,
  ADD CONSTRAINT `fk_inventory_location`
    FOREIGN KEY (`location_id`) REFERENCES `stock_location` (`id`),
  DROP CHECK `ck_inventory_type`,
  ADD CONSTRAINT `ck_inventory_type`
    CHECK (`movement_type` IN (
      'INITIAL_BALANCE', 'LEGACY_OUT', 'PURCHASE_IN', 'SALE_OUT',
      'SERVICE_USE', 'RETURN_IN', 'TRANSFER_OUT', 'TRANSFER_IN',
      'MANUAL_IN', 'MANUAL_OUT', 'ADJUSTMENT'
    ));

ALTER TABLE `sales_order`
  ADD COLUMN `inventory_deducted_at` datetime(3) DEFAULT NULL AFTER `paid_at`,
  ADD KEY `idx_sales_order_inventory_pending`
    (`tenant_id`, `shop_id`, `status`, `inventory_deducted_at`);

UPDATE `sales_order`
SET `inventory_deducted_at` = COALESCE(`paid_at`, `created_at`)
WHERE `status` IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')
  AND `inventory_deducted_at` IS NULL;

INSERT INTO `stock_location` (
  `tenant_id`, `shop_id`, `location_code`, `name`, `location_type`, `status`
)
SELECT
  s.`tenant_id`,
  s.`id`,
  CONCAT(s.`shop_code`, '-CARE'),
  CONCAT(s.`name`, '护理耗材间'),
  'ROOM',
  'ACTIVE'
FROM `shop` s
WHERE NOT EXISTS (
  SELECT 1
  FROM `stock_location` sl
  WHERE sl.`tenant_id` = s.`tenant_id`
    AND sl.`location_code` = CONCAT(s.`shop_code`, '-CARE')
);

INSERT INTO `stock_balance` (
  `tenant_id`, `location_id`, `product_id`,
  `quantity_on_hand`, `quantity_reserved`, `version`
)
SELECT
  p.`tenant_id`,
  sl.`id`,
  p.`id`,
  CASE WHEN sl.`location_type` = 'SHOP' THEN p.`stock_quantity` ELSE 0 END,
  0,
  0
FROM `product` p
JOIN `stock_location` sl
  ON sl.`tenant_id` = p.`tenant_id`
 AND sl.`shop_id` = p.`shop_id`
 AND sl.`status` = 'ACTIVE'
WHERE NOT EXISTS (
  SELECT 1
  FROM `stock_balance` sb
  WHERE sb.`location_id` = sl.`id`
    AND sb.`product_id` = p.`id`
);

INSERT INTO `inventory_movement` (
  `tenant_id`, `shop_id`, `location_id`, `product_id`,
  `movement_type`, `quantity_delta`, `balance_after`,
  `reference_no`, `idempotency_key`, `remark`,
  `created_by`, `created_at`
)
SELECT
  p.`tenant_id`,
  p.`shop_id`,
  sl.`id`,
  p.`id`,
  'INITIAL_BALANCE',
  p.`stock_quantity` + COALESCE(legacy.`out_quantity`, 0),
  p.`stock_quantity` + COALESCE(legacy.`out_quantity`, 0),
  'INVENTORY-MIGRATION-20260724',
  CONCAT('INVENTORY-OPENING-', p.`id`),
  '库存中心迁移期初余额',
  a.`id`,
  TIMESTAMP('2026-07-23 00:00:00')
FROM `product` p
JOIN `stock_location` sl
  ON sl.`tenant_id` = p.`tenant_id`
 AND sl.`shop_id` = p.`shop_id`
 AND sl.`location_type` = 'SHOP'
JOIN `account` a
  ON a.`tenant_id` = p.`tenant_id`
 AND a.`username` = 'admin'
LEFT JOIN (
  SELECT
    p2.`id` AS product_id,
    SUM(pc.`shuliang`) AS out_quantity
  FROM `product` p2
  JOIN `peijianchuku` pc
    ON pc.`peijianmingcheng` COLLATE utf8mb4_unicode_ci =
       p2.`name` COLLATE utf8mb4_unicode_ci
  GROUP BY p2.`id`
) legacy ON legacy.`product_id` = p.`id`
WHERE p.`stock_quantity` + COALESCE(legacy.`out_quantity`, 0) > 0
  AND NOT EXISTS (
    SELECT 1
    FROM `inventory_movement` im
    WHERE im.`tenant_id` = p.`tenant_id`
      AND im.`idempotency_key` = CONCAT('INVENTORY-OPENING-', p.`id`)
  );

INSERT INTO `inventory_movement` (
  `tenant_id`, `shop_id`, `location_id`, `product_id`,
  `movement_type`, `quantity_delta`, `balance_after`,
  `reference_no`, `idempotency_key`, `remark`,
  `created_by`, `created_at`
)
SELECT
  p.`tenant_id`,
  p.`shop_id`,
  sl.`id`,
  p.`id`,
  'LEGACY_OUT',
  -CAST(pc.`shuliang` AS DECIMAL(12,3)),
  p.`stock_quantity`
    + SUM(pc.`shuliang`) OVER (PARTITION BY p.`id`)
    - SUM(pc.`shuliang`) OVER (
        PARTITION BY p.`id`
        ORDER BY pc.`chukushijian`, pc.`id`
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
      ),
  pc.`chukubianhao`,
  CONCAT('INVENTORY-LEGACY-OUT-', pc.`id`),
  CONCAT('旧系统出库迁移：', COALESCE(pc.`beizhu`, '')),
  a.`id`,
  TIMESTAMP(pc.`chukushijian`, TIME(pc.`addtime`))
FROM `peijianchuku` pc
JOIN `product` p
  ON p.`name` COLLATE utf8mb4_unicode_ci =
     pc.`peijianmingcheng` COLLATE utf8mb4_unicode_ci
JOIN `stock_location` sl
  ON sl.`tenant_id` = p.`tenant_id`
 AND sl.`shop_id` = p.`shop_id`
 AND sl.`location_type` = 'SHOP'
JOIN `account` a
  ON a.`tenant_id` = p.`tenant_id`
 AND a.`username` = 'admin'
WHERE NOT EXISTS (
  SELECT 1
  FROM `inventory_movement` im
  WHERE im.`tenant_id` = p.`tenant_id`
    AND im.`idempotency_key` = CONCAT('INVENTORY-LEGACY-OUT-', pc.`id`)
);

CREATE TABLE `inventory_transfer` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `transfer_no` varchar(48) NOT NULL,
  `source_shop_id` bigint unsigned NOT NULL,
  `source_location_id` bigint unsigned NOT NULL,
  `destination_shop_id` bigint unsigned NOT NULL,
  `destination_location_id` bigint unsigned NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `idempotency_key` varchar(80) NOT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `decision_note` varchar(500) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `approved_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `reviewed_at` datetime(3) DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_transfer_no` (`tenant_id`, `transfer_no`),
  UNIQUE KEY `uk_inventory_transfer_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_inventory_transfer_source` (`source_shop_id`, `status`, `created_at`),
  KEY `idx_inventory_transfer_destination` (`destination_shop_id`, `status`, `created_at`),
  CONSTRAINT `fk_inventory_transfer_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_inventory_transfer_source_shop`
    FOREIGN KEY (`source_shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_inventory_transfer_destination_shop`
    FOREIGN KEY (`destination_shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_inventory_transfer_source_location`
    FOREIGN KEY (`source_location_id`) REFERENCES `stock_location` (`id`),
  CONSTRAINT `fk_inventory_transfer_destination_location`
    FOREIGN KEY (`destination_location_id`) REFERENCES `stock_location` (`id`),
  CONSTRAINT `fk_inventory_transfer_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_inventory_transfer_approver`
    FOREIGN KEY (`approved_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_inventory_transfer_status`
    CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT `ck_inventory_transfer_locations`
    CHECK (`source_location_id` <> `destination_location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='库存调拨单';

CREATE TABLE `inventory_transfer_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `transfer_id` bigint unsigned NOT NULL,
  `source_product_id` bigint unsigned NOT NULL,
  `destination_product_id` bigint unsigned NOT NULL,
  `sku_snapshot` varchar(50) NOT NULL,
  `product_name_snapshot` varchar(120) NOT NULL,
  `quantity` decimal(12,3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_transfer_item_product`
    (`transfer_id`, `source_product_id`),
  KEY `idx_inventory_transfer_item_destination_product`
    (`destination_product_id`),
  CONSTRAINT `fk_inventory_transfer_item_transfer`
    FOREIGN KEY (`transfer_id`) REFERENCES `inventory_transfer` (`id`),
  CONSTRAINT `fk_inventory_transfer_item_source_product`
    FOREIGN KEY (`source_product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_inventory_transfer_item_destination_product`
    FOREIGN KEY (`destination_product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `ck_inventory_transfer_item_quantity`
    CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='库存调拨明细';

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'inventory:view'
WHERE r.`role_code` = 'REGIONAL_MANAGER'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id`
      AND rp.`permission_id` = p.`id`
  );

