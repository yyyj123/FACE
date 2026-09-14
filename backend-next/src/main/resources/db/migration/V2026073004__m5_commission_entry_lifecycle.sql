CREATE TABLE `commission_entry` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `entry_no` varchar(40) NOT NULL,
  `source_entry_key` varchar(200) NOT NULL,
  `staff_id` bigint unsigned NOT NULL,
  `source_snapshot_id` bigint unsigned NOT NULL,
  `rule_version_id` bigint unsigned NOT NULL,
  `original_entry_id` bigint unsigned DEFAULT NULL,
  `refund_id` bigint unsigned DEFAULT NULL,
  `entry_type` varchar(20) NOT NULL,
  `base_amount` decimal(14,2) NOT NULL,
  `amount` decimal(14,2) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `freeze_reason` varchar(500) DEFAULT NULL,
  `frozen_by` bigint unsigned DEFAULT NULL,
  `frozen_at` datetime(3) DEFAULT NULL,
  `unfrozen_by` bigint unsigned DEFAULT NULL,
  `unfrozen_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_entry_no` (`tenant_id`, `entry_no`),
  UNIQUE KEY `uk_commission_entry_source_key` (`tenant_id`, `source_entry_key`),
  KEY `idx_commission_entry_staff_status`
    (`tenant_id`, `shop_id`, `staff_id`, `status`, `created_at`),
  KEY `idx_commission_entry_source`
    (`tenant_id`, `source_snapshot_id`, `rule_version_id`),
  KEY `idx_commission_entry_refund`
    (`tenant_id`, `refund_id`, `original_entry_id`),
  CONSTRAINT `fk_commission_entry_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_entry_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_commission_entry_staff`
    FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_commission_entry_source`
    FOREIGN KEY (`source_snapshot_id`) REFERENCES `commission_source_snapshot` (`id`),
  CONSTRAINT `fk_commission_entry_rule`
    FOREIGN KEY (`rule_version_id`) REFERENCES `commission_rule_version` (`id`),
  CONSTRAINT `fk_commission_entry_original`
    FOREIGN KEY (`original_entry_id`) REFERENCES `commission_entry` (`id`),
  CONSTRAINT `fk_commission_entry_refund`
    FOREIGN KEY (`refund_id`) REFERENCES `refund_transaction` (`id`),
  CONSTRAINT `fk_commission_entry_frozen_by`
    FOREIGN KEY (`frozen_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_entry_unfrozen_by`
    FOREIGN KEY (`unfrozen_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_entry_created_by`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_commission_entry_type`
    CHECK (`entry_type` IN ('ACCRUAL', 'REVERSAL')),
  CONSTRAINT `ck_commission_entry_status`
    CHECK (`status` IN ('PENDING', 'FROZEN', 'SETTLED')),
  CONSTRAINT `ck_commission_entry_direction`
    CHECK (
      (`entry_type` = 'ACCRUAL' AND `amount` > 0)
      OR (`entry_type` = 'REVERSAL' AND `amount` < 0)
    ),
  CONSTRAINT `ck_commission_entry_base`
    CHECK (`base_amount` >= 0),
  CONSTRAINT `ck_commission_entry_link`
    CHECK (
      (`entry_type` = 'ACCRUAL'
        AND `original_entry_id` IS NULL AND `refund_id` IS NULL)
      OR (`entry_type` = 'REVERSAL'
        AND `original_entry_id` IS NOT NULL AND `refund_id` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 不可变提成金额流水';

CREATE TABLE `commission_entry_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `entry_id` bigint unsigned NOT NULL,
  `action` varchar(30) NOT NULL,
  `from_status` varchar(20) DEFAULT NULL,
  `to_status` varchar(20) NOT NULL,
  `amount_delta` decimal(14,2) NOT NULL DEFAULT 0.00,
  `reason` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_entry_history_idempotency`
    (`tenant_id`, `idempotency_key`),
  KEY `idx_commission_entry_history`
    (`tenant_id`, `entry_id`, `created_at`, `id`),
  CONSTRAINT `fk_commission_history_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_history_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_commission_history_entry`
    FOREIGN KEY (`entry_id`) REFERENCES `commission_entry` (`id`),
  CONSTRAINT `fk_commission_history_created_by`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_commission_history_action`
    CHECK (`action` IN ('ACCRUED', 'FROZEN', 'UNFROZEN', 'REVERSED')),
  CONSTRAINT `ck_commission_history_status`
    CHECK (`to_status` IN ('PENDING', 'FROZEN', 'SETTLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 提成流水追加式状态历史';

CREATE TABLE `commission_event_projection` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `outbox_event_id` bigint unsigned NOT NULL,
  `event_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_type` varchar(120) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int unsigned NOT NULL DEFAULT 0,
  `result_code` varchar(80) DEFAULT NULL,
  `last_error_code` varchar(80) DEFAULT NULL,
  `processed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_projection_outbox`
    (`tenant_id`, `outbox_event_id`),
  UNIQUE KEY `uk_commission_projection_event`
    (`tenant_id`, `event_id`),
  KEY `idx_commission_projection_retry`
    (`status`, `attempt_count`, `updated_at`, `id`),
  CONSTRAINT `fk_commission_projection_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_projection_outbox`
    FOREIGN KEY (`outbox_event_id`) REFERENCES `outbox_event` (`id`),
  CONSTRAINT `ck_commission_projection_status`
    CHECK (`status` IN ('PENDING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 提成领域事件投影检查点';

INSERT INTO `commission_event_projection` (
  `tenant_id`, `outbox_event_id`, `event_id`, `event_type`,
  `status`, `attempt_count`, `result_code`, `processed_at`
)
SELECT
  oe.`tenant_id`, oe.`id`, oe.`event_id`, oe.`event_type`,
  'COMPLETED', 0, 'MIGRATION_BASELINE_SKIPPED', CURRENT_TIMESTAMP(3)
FROM `outbox_event` oe
WHERE oe.`event_type` IN (
  'PaymentSucceeded',
  'ServiceRecordCompleted',
  'RefundCompleted'
);

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('commission:freeze', '冻结或解冻待结算提成', 'commission', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'commission:freeze'
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
