ALTER TABLE `sales_order_item`
  ADD COLUMN `category_id_snapshot` bigint unsigned DEFAULT NULL
    COMMENT '成交时品类ID快照；不设外键，避免主数据变更改写历史'
    AFTER `item_name_snapshot`,
  ADD COLUMN `category_name_snapshot` varchar(80) DEFAULT NULL
    COMMENT '成交时品类名称快照'
    AFTER `category_id_snapshot`,
  ADD COLUMN `brand_name_snapshot` varchar(100) DEFAULT NULL
    COMMENT '成交时零售品牌名称快照'
    AFTER `category_name_snapshot`,
  ADD COLUMN `dimension_snapshot_quality` varchar(30) NOT NULL DEFAULT 'MISSING'
    COMMENT 'TRANSACTION_TIME/CURRENT_MASTER_BACKFILL/MISSING'
    AFTER `brand_name_snapshot`;

UPDATE `sales_order_item` soi
JOIN `service_item` si ON si.id = soi.service_id
LEFT JOIN `service_category` sc ON sc.id = si.category_id
SET soi.category_id_snapshot = si.category_id,
    soi.category_name_snapshot = sc.name,
    soi.dimension_snapshot_quality = CASE
      WHEN sc.id IS NULL THEN 'MISSING'
      ELSE 'CURRENT_MASTER_BACKFILL'
    END
WHERE soi.item_type = 'SERVICE';

UPDATE `sales_order_item` soi
JOIN `product` p ON p.id = soi.product_id
LEFT JOIN `product_category` pc ON pc.id = p.category_id
SET soi.category_id_snapshot = p.category_id,
    soi.category_name_snapshot = pc.name,
    soi.brand_name_snapshot = NULLIF(TRIM(p.brand_name), ''),
    soi.dimension_snapshot_quality = CASE
      WHEN pc.id IS NULL THEN 'MISSING'
      ELSE 'CURRENT_MASTER_BACKFILL'
    END
WHERE soi.item_type = 'PRODUCT';

ALTER TABLE `sales_order_item`
  ADD CONSTRAINT `ck_order_item_dimension_snapshot_quality`
    CHECK (
      `dimension_snapshot_quality` IN (
        'TRANSACTION_TIME',
        'CURRENT_MASTER_BACKFILL',
        'MISSING'
      )
    ),
  ADD KEY `idx_order_item_category_analytics`
    (`item_type`, `category_id_snapshot`, `order_id`),
  ADD KEY `idx_order_item_brand_analytics`
    (`item_type`, `brand_name_snapshot`, `order_id`);

INSERT IGNORE INTO `permission_definition` (
  `permission_code`, `permission_name`, `module_code`, `risk_level`
) VALUES
  ('analytics:view', '查看经营分析', 'analytics', 'SENSITIVE'),
  ('analytics:export', '导出经营分析', 'analytics', 'CRITICAL');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.permission_code = 'analytics:view'
WHERE r.role_code IN ('OWNER', 'MANAGER', 'REGIONAL_MANAGER')
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.permission_code = 'analytics:export'
WHERE r.role_code IN ('OWNER', 'REGIONAL_MANAGER')
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );
