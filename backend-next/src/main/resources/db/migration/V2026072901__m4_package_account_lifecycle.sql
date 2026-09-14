CREATE TABLE `package_product` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `package_code` varchar(48) NOT NULL,
  `name` varchar(120) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `sale_price` decimal(12,2) NOT NULL,
  `validity_days` smallint unsigned NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `updated_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_product_code` (`tenant_id`, `package_code`),
  KEY `idx_package_product_scope_status` (`tenant_id`, `shop_id`, `status`, `name`),
  CONSTRAINT `fk_package_product_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_package_product_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_package_product_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_package_product_updater`
    FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_package_product_values`
    CHECK (`sale_price` >= 0 AND `validity_days` > 0),
  CONSTRAINT `ck_package_product_status`
    CHECK (`status` IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='套餐产品及当前版本';

CREATE TABLE `package_product_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `package_product_id` bigint unsigned NOT NULL,
  `service_id` bigint unsigned NOT NULL,
  `service_name_snapshot` varchar(120) NOT NULL,
  `quantity_total` decimal(12,4) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_product_service` (`package_product_id`, `service_id`),
  KEY `idx_package_item_tenant_service` (`tenant_id`, `service_id`),
  CONSTRAINT `fk_package_item_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_package_item_product`
    FOREIGN KEY (`package_product_id`) REFERENCES `package_product` (`id`),
  CONSTRAINT `fk_package_item_service`
    FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `ck_package_item_quantity`
    CHECK (`quantity_total` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='套餐包含项目及总次数';

CREATE TABLE `package_instance` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `package_product_id` bigint unsigned NOT NULL,
  `instance_no` varchar(48) NOT NULL,
  `source_order_id` bigint unsigned NOT NULL,
  `purchase_price` decimal(12,2) NOT NULL,
  `valid_from` date NOT NULL,
  `valid_until` date NOT NULL,
  `total_quantity` decimal(12,4) NOT NULL,
  `remaining_quantity` decimal(12,4) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `version` int unsigned NOT NULL DEFAULT 0,
  `issue_idempotency_key` varchar(80) NOT NULL,
  `issue_request_hash`
    char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `updated_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_instance_no` (`tenant_id`, `instance_no`),
  UNIQUE KEY `uk_package_instance_issue_key` (`tenant_id`, `issue_idempotency_key`),
  UNIQUE KEY `uk_package_instance_order_product`
    (`tenant_id`, `source_order_id`, `package_product_id`),
  KEY `idx_package_instance_member_status`
    (`tenant_id`, `member_id`, `status`, `valid_until`),
  KEY `idx_package_instance_shop_status`
    (`tenant_id`, `shop_id`, `status`, `valid_until`),
  CONSTRAINT `fk_package_instance_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_package_instance_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_package_instance_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_package_instance_product`
    FOREIGN KEY (`package_product_id`) REFERENCES `package_product` (`id`),
  CONSTRAINT `fk_package_instance_order`
    FOREIGN KEY (`source_order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_package_instance_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_package_instance_updater`
    FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_package_instance_balance`
    CHECK (
      `total_quantity` > 0
      AND `remaining_quantity` >= 0
      AND `remaining_quantity` <= `total_quantity`
    ),
  CONSTRAINT `ck_package_instance_dates`
    CHECK (`valid_until` >= `valid_from`),
  CONSTRAINT `ck_package_instance_status`
    CHECK (`status` IN (
      'ACTIVE', 'FROZEN', 'EXHAUSTED', 'EXPIRED', 'CANCELLED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='会员购买后的套餐权益实例';

CREATE TABLE `package_instance_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `package_instance_id` bigint unsigned NOT NULL,
  `service_id` bigint unsigned NOT NULL,
  `service_name_snapshot` varchar(120) NOT NULL,
  `total_quantity` decimal(12,4) NOT NULL,
  `remaining_quantity` decimal(12,4) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_instance_service` (`package_instance_id`, `service_id`),
  KEY `idx_package_instance_item_service` (`tenant_id`, `service_id`),
  CONSTRAINT `fk_package_instance_item_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_package_instance_item_instance`
    FOREIGN KEY (`package_instance_id`) REFERENCES `package_instance` (`id`),
  CONSTRAINT `fk_package_instance_item_service`
    FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `ck_package_instance_item_balance`
    CHECK (
      `total_quantity` > 0
      AND `remaining_quantity` >= 0
      AND `remaining_quantity` <= `total_quantity`
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='会员套餐项目与余额快照';

CREATE TABLE `package_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `package_instance_id` bigint unsigned NOT NULL,
  `package_instance_item_id` bigint unsigned DEFAULT NULL,
  `service_record_id` bigint unsigned DEFAULT NULL,
  `entry_type` varchar(24) NOT NULL,
  `quantity_delta` decimal(12,4) NOT NULL,
  `balance_after` decimal(12,4) NOT NULL,
  `original_ledger_id` bigint unsigned DEFAULT NULL,
  `business_key` varchar(160) DEFAULT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash`
    char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_ledger_idempotency` (`tenant_id`, `idempotency_key`),
  UNIQUE KEY `uk_package_ledger_reversal` (`tenant_id`, `original_ledger_id`),
  UNIQUE KEY `uk_package_ledger_business` (`tenant_id`, `business_key`),
  KEY `idx_package_ledger_instance_time`
    (`package_instance_id`, `created_at`, `id`),
  KEY `idx_package_ledger_service` (`tenant_id`, `service_record_id`),
  CONSTRAINT `fk_package_ledger_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_package_ledger_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_package_ledger_instance`
    FOREIGN KEY (`package_instance_id`) REFERENCES `package_instance` (`id`),
  CONSTRAINT `fk_package_ledger_item`
    FOREIGN KEY (`package_instance_item_id`) REFERENCES `package_instance_item` (`id`),
  CONSTRAINT `fk_package_ledger_service_record`
    FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_package_ledger_original`
    FOREIGN KEY (`original_ledger_id`) REFERENCES `package_ledger` (`id`),
  CONSTRAINT `fk_package_ledger_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_package_ledger_delta`
    CHECK (`quantity_delta` <> 0 AND `balance_after` >= 0),
  CONSTRAINT `ck_package_ledger_type`
    CHECK (`entry_type` IN ('ISSUE', 'WRITE_OFF', 'REVERSAL', 'ADJUSTMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='套餐权益不可变流水';

ALTER TABLE `sales_order_item`
  ADD COLUMN `package_product_id` bigint unsigned DEFAULT NULL
    AFTER `product_id`,
  ADD KEY `idx_order_item_package` (`package_product_id`),
  ADD CONSTRAINT `fk_order_item_package`
    FOREIGN KEY (`package_product_id`) REFERENCES `package_product` (`id`),
  DROP CHECK `ck_order_item_type`,
  ADD CONSTRAINT `ck_order_item_type`
    CHECK (
      (
        `item_type` = 'SERVICE'
        AND `service_id` IS NOT NULL
        AND `product_id` IS NULL
        AND `package_product_id` IS NULL
      )
      OR (
        `item_type` = 'PRODUCT'
        AND `product_id` IS NOT NULL
        AND `service_id` IS NULL
        AND `package_product_id` IS NULL
      )
      OR (
        `item_type` = 'PACKAGE'
        AND `package_product_id` IS NOT NULL
        AND `service_id` IS NULL
        AND `product_id` IS NULL
      )
    );

ALTER TABLE `member_account_ledger`
  ADD COLUMN `request_hash`
    char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    AFTER `idempotency_key`,
  ADD COLUMN `reversal_of_ledger_id` bigint unsigned DEFAULT NULL
    AFTER `request_hash`,
  ADD UNIQUE KEY `uk_member_ledger_reversal`
    (`tenant_id`, `reversal_of_ledger_id`),
  ADD CONSTRAINT `fk_member_ledger_reversal`
    FOREIGN KEY (`reversal_of_ledger_id`) REFERENCES `member_account_ledger` (`id`);

INSERT INTO `permission_definition` (
  `permission_code`, `permission_name`, `module_code`, `risk_level`
) VALUES
  ('package:view', '查看套餐产品与会员套餐', 'package', 'SENSITIVE'),
  ('package:manage', '创建、修改与发放套餐', 'package', 'CRITICAL'),
  ('package:writeoff', '按服务记录核销套餐', 'package', 'SENSITIVE'),
  ('package:reverse', '冲正套餐核销流水', 'package', 'CRITICAL'),
  ('account:view', '查看会员账户与流水', 'account', 'SENSITIVE'),
  ('account:manage', '会员账户入账、扣减、冻结与冲正', 'account', 'CRITICAL');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN (
    'package:view', 'package:manage', 'package:writeoff', 'package:reverse',
    'account:view', 'account:manage'
  )
WHERE r.`role_code` IN ('OWNER', 'MANAGER')
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
  ON p.`permission_code` IN ('package:view', 'package:writeoff', 'account:view')
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
  ON p.`permission_code` IN ('package:view', 'package:writeoff')
WHERE r.`role_code` = 'BEAUTICIAN'
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
  ON p.`permission_code` IN ('package:view', 'account:view')
WHERE r.`role_code` = 'REGIONAL_MANAGER'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id`
      AND rp.`permission_id` = p.`id`
  );
