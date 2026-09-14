CREATE TABLE `commission_rule_version` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `rule_code` varchar(80) NOT NULL,
  `version_no` int unsigned NOT NULL,
  `rule_name` varchar(120) NOT NULL,
  `source_type` varchar(20) NOT NULL,
  `calculation_type` varchar(30) NOT NULL,
  `rate_value` decimal(9,6) NOT NULL DEFAULT 0.000000,
  `fixed_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `floor_amount` decimal(14,2) DEFAULT NULL,
  `cap_amount` decimal(14,2) DEFAULT NULL,
  `priority` int NOT NULL DEFAULT 0,
  `effective_from` datetime(3) NOT NULL,
  `effective_to` datetime(3) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `published_by` bigint unsigned DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `retired_by` bigint unsigned DEFAULT NULL,
  `retired_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_rule_version`
    (`tenant_id`, `rule_code`, `version_no`),
  KEY `idx_commission_rule_effective`
    (`tenant_id`, `shop_id`, `status`, `source_type`, `effective_from`, `effective_to`),
  KEY `idx_commission_rule_priority`
    (`tenant_id`, `shop_id`, `source_type`, `priority`, `status`),
  CONSTRAINT `fk_commission_rule_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_rule_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_commission_rule_created_by`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_rule_published_by`
    FOREIGN KEY (`published_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_rule_retired_by`
    FOREIGN KEY (`retired_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_commission_rule_source`
    CHECK (`source_type` IN ('SERVICE', 'SALE')),
  CONSTRAINT `ck_commission_rule_calculation`
    CHECK (`calculation_type` IN ('PERCENTAGE', 'FIXED', 'PERCENTAGE_PLUS_FIXED')),
  CONSTRAINT `ck_commission_rule_status`
    CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
  CONSTRAINT `ck_commission_rule_amounts`
    CHECK (
      `rate_value` >= 0
      AND `fixed_amount` >= 0
      AND (`floor_amount` IS NULL OR `floor_amount` >= 0)
      AND (`cap_amount` IS NULL OR `cap_amount` >= 0)
      AND (
        `floor_amount` IS NULL
        OR `cap_amount` IS NULL
        OR `cap_amount` >= `floor_amount`
      )
    ),
  CONSTRAINT `ck_commission_rule_effective_range`
    CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),
  CONSTRAINT `ck_commission_rule_publish_fields`
    CHECK (
      (`status` = 'DRAFT' AND `published_by` IS NULL AND `published_at` IS NULL)
      OR (`status` IN ('PUBLISHED', 'RETIRED') AND `published_by` IS NOT NULL
          AND `published_at` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 提成规则不可变版本';

CREATE TABLE `commission_rule_scope` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `rule_version_id` bigint unsigned NOT NULL,
  `scope_type` varchar(20) NOT NULL,
  `scope_key` varchar(100) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_rule_scope`
    (`tenant_id`, `rule_version_id`, `scope_type`, `scope_key`),
  KEY `idx_commission_scope_lookup`
    (`tenant_id`, `scope_type`, `scope_key`, `rule_version_id`),
  CONSTRAINT `fk_commission_scope_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_scope_rule`
    FOREIGN KEY (`rule_version_id`) REFERENCES `commission_rule_version` (`id`),
  CONSTRAINT `ck_commission_scope_type`
    CHECK (`scope_type` IN ('SHOP', 'ROLE', 'STAFF', 'SERVICE', 'PRODUCT', 'PACKAGE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 提成规则适用范围';

CREATE TABLE `commission_source_snapshot` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `source_type` varchar(20) NOT NULL,
  `source_id` bigint unsigned NOT NULL,
  `staff_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned DEFAULT NULL,
  `business_no` varchar(80) NOT NULL,
  `base_amount` decimal(14,2) NOT NULL,
  `source_occurred_at` datetime(3) NOT NULL,
  `snapshot_data` json NOT NULL,
  `snapshot_hash` char(64) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'CAPTURED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_source_snapshot`
    (`tenant_id`, `source_type`, `source_id`, `staff_id`),
  KEY `idx_commission_source_staff_time`
    (`tenant_id`, `shop_id`, `staff_id`, `source_occurred_at`),
  KEY `idx_commission_source_hash` (`tenant_id`, `snapshot_hash`),
  CONSTRAINT `fk_commission_source_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_source_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_commission_source_staff`
    FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_commission_source_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `ck_commission_source_type`
    CHECK (`source_type` IN ('SERVICE', 'SALE')),
  CONSTRAINT `ck_commission_source_amount`
    CHECK (`base_amount` >= 0),
  CONSTRAINT `ck_commission_source_status`
    CHECK (`status` IN ('CAPTURED', 'VOIDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 订单或服务提成来源快照';

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('commission:rule:view', '查看提成规则版本', 'commission', 'SENSITIVE'),
  ('commission:rule:manage', '维护并发布提成规则版本', 'commission', 'CRITICAL'),
  ('commission:entry:view', '查看提成来源快照与流水', 'commission', 'SENSITIVE')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN (
    'commission:rule:view',
    'commission:rule:manage',
    'commission:entry:view'
  )
WHERE r.`role_code` = 'OWNER'
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN (
    'commission:rule:view',
    'commission:rule:manage',
    'commission:entry:view'
  )
WHERE r.`role_code` = 'MANAGER'
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN ('commission:rule:view', 'commission:entry:view')
WHERE r.`role_code` IN ('REGIONAL_MANAGER', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'commission:entry:view'
WHERE r.`role_code` = 'BEAUTICIAN'
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
