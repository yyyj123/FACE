ALTER TABLE `service_record`
  ADD COLUMN `tenant_id` bigint unsigned NOT NULL AFTER `id`,
  ADD COLUMN `record_no` varchar(48) NOT NULL AFTER `tenant_id`,
  ADD COLUMN `legacy_source_id` bigint DEFAULT NULL AFTER `record_no`,
  ADD COLUMN `completion_idempotency_key` varchar(80) DEFAULT NULL AFTER `legacy_source_id`,
  ADD COLUMN `version` int unsigned NOT NULL DEFAULT 0 AFTER `status`,
  ADD COLUMN `void_reason` varchar(500) DEFAULT NULL AFTER `version`,
  ADD COLUMN `created_by` bigint unsigned NOT NULL AFTER `void_reason`,
  ADD COLUMN `updated_by` bigint unsigned NOT NULL AFTER `created_by`,
  ADD UNIQUE KEY `uk_service_record_no` (`tenant_id`, `record_no`),
  ADD UNIQUE KEY `uk_service_record_legacy` (`tenant_id`, `legacy_source_id`),
  ADD UNIQUE KEY `uk_service_record_completion_key`
    (`tenant_id`, `completion_idempotency_key`),
  ADD KEY `idx_service_record_tenant_shop_status`
    (`tenant_id`, `shop_id`, `status`, `actual_start_at`),
  ADD CONSTRAINT `fk_service_record_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  ADD CONSTRAINT `fk_service_record_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  ADD CONSTRAINT `fk_service_record_updater`
    FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`);

ALTER TABLE `care_record`
  ADD COLUMN `tenant_id` bigint unsigned NOT NULL AFTER `id`,
  ADD COLUMN `version` int unsigned NOT NULL DEFAULT 0 AFTER `next_recommended_at`,
  ADD COLUMN `updated_by` bigint unsigned NOT NULL AFTER `created_by`,
  ADD KEY `idx_care_record_tenant_member`
    (`tenant_id`, `member_id`, `created_at`),
  ADD CONSTRAINT `fk_care_record_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  ADD CONSTRAINT `fk_care_record_updater`
    FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`);

CREATE TABLE `service_record_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `service_record_id` bigint unsigned NOT NULL,
  `service_id` bigint unsigned NOT NULL,
  `service_name_snapshot` varchar(120) NOT NULL,
  `duration_minutes_snapshot` smallint unsigned NOT NULL,
  `price_snapshot` decimal(10,2) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_record_item_service`
    (`service_record_id`, `service_id`),
  KEY `idx_service_record_item_service` (`service_id`),
  CONSTRAINT `fk_service_record_item_record`
    FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_service_record_item_service`
    FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `ck_service_record_item_values`
    CHECK (`duration_minutes_snapshot` > 0 AND `price_snapshot` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='实际服务项目快照';

CREATE TABLE `service_record_consumption` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `service_record_id` bigint unsigned NOT NULL,
  `location_id` bigint unsigned NOT NULL,
  `product_id` bigint unsigned NOT NULL,
  `quantity` decimal(12,3) NOT NULL,
  `inventory_movement_id` bigint unsigned NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_consumption_movement` (`inventory_movement_id`),
  UNIQUE KEY `uk_service_consumption_line`
    (`service_record_id`, `location_id`, `product_id`),
  KEY `idx_service_consumption_tenant_record`
    (`tenant_id`, `service_record_id`),
  KEY `idx_service_consumption_product` (`product_id`, `created_at`),
  CONSTRAINT `fk_service_consumption_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_service_consumption_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_service_consumption_record`
    FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_service_consumption_location`
    FOREIGN KEY (`location_id`) REFERENCES `stock_location` (`id`),
  CONSTRAINT `fk_service_consumption_product`
    FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_service_consumption_movement`
    FOREIGN KEY (`inventory_movement_id`) REFERENCES `inventory_movement` (`id`),
  CONSTRAINT `fk_service_consumption_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_service_consumption_quantity`
    CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='服务耗材领用明细';

-- 旧系统中已实际出现在服务记录里的商品，本质上属于服务耗材。
UPDATE `product` p
JOIN `weixiujilu` w
  ON w.`peijianmingcheng` COLLATE utf8mb4_unicode_ci =
     p.`name` COLLATE utf8mb4_unicode_ci
SET p.`is_consumable` = 1
WHERE COALESCE(w.`peijianmingcheng`, '') <> '';

ALTER TABLE `sales_order`
  ADD UNIQUE KEY `uk_sales_order_service_record` (`service_record_id`);

INSERT INTO `permission_definition` (
  `permission_code`, `permission_name`, `module_code`, `risk_level`
) VALUES
  ('service_record:view', '查看到店服务记录', 'service_record', 'SENSITIVE'),
  ('service_record:manage', '执行与完成到店服务', 'service_record', 'SENSITIVE'),
  ('care_record:view', '查看会员护理档案', 'care_record', 'SENSITIVE'),
  ('care_record:manage', '填写与修改护理档案', 'care_record', 'SENSITIVE');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN (
    'service_record:view', 'service_record:manage',
    'care_record:view', 'care_record:manage'
  )
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'BEAUTICIAN')
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id`
      AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN ('service_record:view', 'service_record:manage')
WHERE r.`role_code` = 'FRONT_DESK'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id`
      AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'service_record:view'
WHERE r.`role_code` = 'REGIONAL_MANAGER'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id`
      AND rp.`permission_id` = p.`id`
  );

INSERT INTO `service_record` (
  `tenant_id`, `record_no`, `legacy_source_id`, `shop_id`,
  `appointment_id`, `member_id`, `staff_id`,
  `actual_start_at`, `actual_end_at`, `service_summary`,
  `next_visit_recommendation`, `status`, `version`,
  `created_by`, `updated_by`, `created_at`, `updated_at`
)
SELECT
  1,
  w.`weixiubianhao`,
  w.`id`,
  1,
  NULL,
  m.`id`,
  st.`id`,
  w.`weixiushijian`,
  DATE_ADD(w.`weixiushijian`, INTERVAL COALESCE(si.`duration_minutes`, 60) MINUTE),
  w.`weixiushuoming`,
  CASE
    WHEN w.`weixiushuoming` LIKE '%建议%' THEN w.`weixiushuoming`
    ELSE NULL
  END,
  'COMPLETED',
  0,
  a.`id`,
  a.`id`,
  w.`addtime`,
  w.`addtime`
FROM `weixiujilu` w
JOIN `member` m
  ON m.`tenant_id` = 1
 AND m.`member_no` COLLATE utf8mb4_unicode_ci =
     w.`zhanghao` COLLATE utf8mb4_unicode_ci
JOIN `staff` st
  ON st.`tenant_id` = 1
 AND st.`staff_no` COLLATE utf8mb4_unicode_ci =
     w.`weixiuzhanghao` COLLATE utf8mb4_unicode_ci
LEFT JOIN `service_item` si
  ON si.`tenant_id` = 1
 AND si.`shop_id` = 1
 AND si.`name` COLLATE utf8mb4_unicode_ci =
     w.`fuwumingcheng` COLLATE utf8mb4_unicode_ci
JOIN `account` a
  ON a.`tenant_id` = 1
 AND a.`username` = 'admin'
WHERE NOT EXISTS (
  SELECT 1
  FROM `service_record` sr
  WHERE sr.`tenant_id` = 1
    AND sr.`legacy_source_id` = w.`id`
);

INSERT INTO `service_record_item` (
  `service_record_id`, `service_id`, `service_name_snapshot`,
  `duration_minutes_snapshot`, `price_snapshot`, `sort_order`
)
SELECT
  sr.`id`,
  si.`id`,
  w.`fuwumingcheng`,
  si.`duration_minutes`,
  CAST(w.`jiage` AS DECIMAL(10,2)),
  0
FROM `weixiujilu` w
JOIN `service_record` sr
  ON sr.`tenant_id` = 1
 AND sr.`legacy_source_id` = w.`id`
JOIN `service_item` si
  ON si.`tenant_id` = 1
 AND si.`shop_id` = 1
 AND si.`name` COLLATE utf8mb4_unicode_ci =
     w.`fuwumingcheng` COLLATE utf8mb4_unicode_ci
WHERE NOT EXISTS (
  SELECT 1
  FROM `service_record_item` sri
  WHERE sri.`service_record_id` = sr.`id`
    AND sri.`service_id` = si.`id`
);

INSERT INTO `care_record` (
  `tenant_id`, `shop_id`, `service_record_id`, `member_id`,
  `skin_type`, `concerns`, `observations`, `products_used`,
  `home_care_advice`, `next_recommended_at`, `version`,
  `created_by`, `updated_by`, `created_at`, `updated_at`
)
SELECT
  sr.`tenant_id`,
  sr.`shop_id`,
  sr.`id`,
  sr.`member_id`,
  NULL,
  JSON_ARRAY(),
  w.`weixiushuoming`,
  CASE
    WHEN p.`id` IS NULL THEN JSON_ARRAY()
    ELSE JSON_ARRAY(JSON_OBJECT(
      'productId', p.`id`,
      'sku', p.`sku`,
      'name', p.`name`,
      'quantity', 1,
      'source', 'LEGACY'
    ))
  END,
  NULL,
  DATE_ADD(DATE(w.`weixiushijian`), INTERVAL 28 DAY),
  0,
  a.`id`,
  a.`id`,
  w.`addtime`,
  w.`addtime`
FROM `weixiujilu` w
JOIN `service_record` sr
  ON sr.`tenant_id` = 1
 AND sr.`legacy_source_id` = w.`id`
LEFT JOIN `product` p
  ON p.`tenant_id` = 1
 AND p.`shop_id` = 1
 AND p.`name` COLLATE utf8mb4_unicode_ci =
     w.`peijianmingcheng` COLLATE utf8mb4_unicode_ci
JOIN `account` a
  ON a.`tenant_id` = sr.`tenant_id`
 AND a.`username` = 'admin'
WHERE NOT EXISTS (
  SELECT 1
  FROM `care_record` cr
  WHERE cr.`service_record_id` = sr.`id`
);

INSERT INTO `service_record` (
  `tenant_id`, `record_no`, `shop_id`, `appointment_id`,
  `member_id`, `staff_id`, `actual_start_at`, `actual_end_at`,
  `service_summary`, `status`, `version`,
  `created_by`, `updated_by`, `created_at`, `updated_at`
)
SELECT
  ap.`tenant_id`,
  CONCAT('SRM-', LPAD(ap.`id`, 10, '0')),
  ap.`shop_id`,
  ap.`id`,
  ap.`member_id`,
  ap.`staff_id`,
  ap.`start_at`,
  ap.`end_at`,
  '由已完成预约自动建档，护理详情待补充',
  'COMPLETED',
  0,
  a.`id`,
  a.`id`,
  ap.`created_at`,
  ap.`updated_at`
FROM `appointment` ap
JOIN `account` a
  ON a.`tenant_id` = ap.`tenant_id`
 AND a.`username` = 'admin'
WHERE ap.`status` = 'COMPLETED'
  AND NOT EXISTS (
    SELECT 1
    FROM `service_record` sr
    WHERE sr.`appointment_id` = ap.`id`
  );

INSERT INTO `service_record_item` (
  `service_record_id`, `service_id`, `service_name_snapshot`,
  `duration_minutes_snapshot`, `price_snapshot`, `sort_order`
)
SELECT
  sr.`id`,
  ai.`service_id`,
  ai.`service_name_snapshot`,
  ai.`duration_minutes_snapshot`,
  ai.`price_snapshot`,
  ai.`sort_order`
FROM `service_record` sr
JOIN `appointment_item` ai
  ON ai.`appointment_id` = sr.`appointment_id`
WHERE NOT EXISTS (
  SELECT 1
  FROM `service_record_item` sri
  WHERE sri.`service_record_id` = sr.`id`
    AND sri.`service_id` = ai.`service_id`
);

UPDATE `sales_order` so
JOIN `service_record` sr
  ON sr.`tenant_id` = so.`tenant_id`
 AND (
   sr.`record_no` COLLATE utf8mb4_unicode_ci =
     so.`order_no` COLLATE utf8mb4_unicode_ci
   OR (
     sr.`appointment_id` IS NOT NULL
     AND sr.`appointment_id` = so.`appointment_id`
   )
 )
SET so.`service_record_id` = sr.`id`
WHERE so.`service_record_id` IS NULL;
