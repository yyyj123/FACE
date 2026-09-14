CREATE TABLE `commission_settlement_batch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `settlement_no` varchar(40) NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `currency_code` char(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY',
  `create_idempotency_key` varchar(100) NOT NULL,
  `create_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `item_count` int unsigned NOT NULL DEFAULT 0,
  `total_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `calculated_by` bigint unsigned DEFAULT NULL,
  `calculated_at` datetime(3) DEFAULT NULL,
  `confirmed_by` bigint unsigned DEFAULT NULL,
  `confirmed_at` datetime(3) DEFAULT NULL,
  `paid_by` bigint unsigned DEFAULT NULL,
  `paid_at` datetime(3) DEFAULT NULL,
  `payment_reference` varchar(100) DEFAULT NULL,
  `closed_by` bigint unsigned DEFAULT NULL,
  `closed_at` datetime(3) DEFAULT NULL,
  `voided_by` bigint unsigned DEFAULT NULL,
  `voided_at` datetime(3) DEFAULT NULL,
  `void_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_settlement_no` (`tenant_id`, `shop_id`, `settlement_no`),
  UNIQUE KEY `uk_commission_settlement_create_key`
    (`tenant_id`, `create_idempotency_key`),
  KEY `idx_commission_settlement_period`
    (`tenant_id`, `shop_id`, `period_start`, `period_end`, `status`),
  KEY `idx_commission_settlement_staff`
    (`tenant_id`, `shop_id`, `status`, `created_at`),
  CONSTRAINT `fk_commission_settlement_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_settlement_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_commission_settlement_created_by`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_settlement_calculated_by`
    FOREIGN KEY (`calculated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_settlement_confirmed_by`
    FOREIGN KEY (`confirmed_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_settlement_paid_by`
    FOREIGN KEY (`paid_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_settlement_closed_by`
    FOREIGN KEY (`closed_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_settlement_voided_by`
    FOREIGN KEY (`voided_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_commission_settlement_period`
    CHECK (`period_start` <= `period_end`),
  CONSTRAINT `ck_commission_settlement_status`
    CHECK (`status` IN ('DRAFT', 'CALCULATED', 'CONFIRMED', 'PAID', 'CLOSED', 'VOIDED')),
  CONSTRAINT `ck_commission_settlement_totals`
    CHECK (`item_count` >= 0 AND `version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 提成结算批次';

CREATE TABLE `commission_settlement_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `batch_id` bigint unsigned NOT NULL,
  `commission_entry_id` bigint unsigned NOT NULL,
  `staff_id` bigint unsigned NOT NULL,
  `settlement_amount` decimal(14,2) NOT NULL,
  `entry_occurred_at` datetime(3) NOT NULL,
  `active_flag` tinyint unsigned NOT NULL DEFAULT 1,
  `active_entry_id` bigint unsigned
    GENERATED ALWAYS AS (
      CASE WHEN `active_flag` = 1 THEN `commission_entry_id` ELSE NULL END
    ) STORED,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_settlement_batch_entry`
    (`tenant_id`, `batch_id`, `commission_entry_id`),
  UNIQUE KEY `uk_commission_settlement_active_entry`
    (`tenant_id`, `active_entry_id`),
  KEY `idx_commission_settlement_item_staff`
    (`tenant_id`, `shop_id`, `staff_id`, `batch_id`),
  CONSTRAINT `fk_commission_settlement_item_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_settlement_item_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_commission_settlement_item_batch`
    FOREIGN KEY (`batch_id`) REFERENCES `commission_settlement_batch` (`id`),
  CONSTRAINT `fk_commission_settlement_item_entry`
    FOREIGN KEY (`commission_entry_id`) REFERENCES `commission_entry` (`id`),
  CONSTRAINT `fk_commission_settlement_item_staff`
    FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `ck_commission_settlement_item_active`
    CHECK (`active_flag` IN (0, 1)),
  CONSTRAINT `ck_commission_settlement_item_amount`
    CHECK (`settlement_amount` <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 不可变提成结算明细';

ALTER TABLE `commission_entry_history`
  DROP CHECK `ck_commission_history_action`,
  ADD CONSTRAINT `ck_commission_history_action`
    CHECK (`action` IN (
      'ACCRUED', 'FROZEN', 'UNFROZEN', 'REVERSED',
      'SETTLED', 'SETTLEMENT_VOIDED'
    ));

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('commission:settlement:view', '查看提成结算', 'commission', 'NORMAL'),
  ('commission:settlement:manage', '创建和计算提成结算', 'commission', 'SENSITIVE'),
  ('commission:settlement:approve', '确认支付或关闭提成结算', 'commission', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN (
    'commission:settlement:view',
    'commission:settlement:manage',
    'commission:settlement:approve'
  )
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
