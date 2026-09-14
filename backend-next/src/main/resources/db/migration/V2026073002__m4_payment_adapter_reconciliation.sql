ALTER TABLE `payment_transaction`
  ADD COLUMN `channel_code` varchar(30) DEFAULT NULL AFTER `payment_method`,
  ADD COLUMN `channel_status` varchar(32) DEFAULT NULL AFTER `external_transaction_no`,
  ADD COLUMN `channel_request_no` varchar(100) DEFAULT NULL AFTER `channel_status`,
  ADD COLUMN `channel_event_id` varchar(100) DEFAULT NULL AFTER `channel_request_no`,
  ADD COLUMN `confirmed_at` datetime(3) DEFAULT NULL AFTER `paid_at`,
  ADD UNIQUE KEY `uk_payment_channel_request`
    (`tenant_id`, `channel_code`, `channel_request_no`),
  ADD UNIQUE KEY `uk_payment_channel_transaction`
    (`tenant_id`, `channel_code`, `external_transaction_no`),
  ADD KEY `idx_payment_channel_date`
    (`tenant_id`, `shop_id`, `channel_code`, `status`, `paid_at`);

CREATE TABLE `payment_callback_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `payment_id` bigint unsigned NOT NULL,
  `channel_code` varchar(30) NOT NULL,
  `channel_event_id` varchar(100) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `signature_valid` tinyint(1) NOT NULL,
  `processing_status` varchar(20) NOT NULL DEFAULT 'RECEIVED',
  `error_code` varchar(80) DEFAULT NULL,
  `received_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_callback_event`
    (`channel_code`, `channel_event_id`),
  KEY `idx_payment_callback_payment`
    (`tenant_id`, `payment_id`, `received_at`),
  CONSTRAINT `fk_payment_callback_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_payment_callback_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_payment_callback_payment`
    FOREIGN KEY (`payment_id`) REFERENCES `payment_transaction` (`id`),
  CONSTRAINT `ck_payment_callback_signature`
    CHECK (`signature_valid` IN (0, 1)),
  CONSTRAINT `ck_payment_callback_status`
    CHECK (`processing_status` IN (
      'RECEIVED', 'VERIFIED', 'PROCESSED', 'REJECTED', 'IGNORED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='支付通道回调幂等与处理记录';

CREATE TABLE `reconciliation_batch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `channel_code` varchar(30) NOT NULL,
  `accounting_date` date NOT NULL,
  `system_payment_count` int unsigned NOT NULL DEFAULT 0,
  `system_payment_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `system_refund_count` int unsigned NOT NULL DEFAULT 0,
  `system_refund_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `channel_payment_count` int unsigned NOT NULL DEFAULT 0,
  `channel_payment_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `channel_refund_count` int unsigned NOT NULL DEFAULT 0,
  `channel_refund_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `closed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reconciliation_scope_date`
    (`tenant_id`, `shop_id`, `channel_code`, `accounting_date`),
  UNIQUE KEY `uk_reconciliation_idempotency`
    (`tenant_id`, `idempotency_key`),
  KEY `idx_reconciliation_status`
    (`tenant_id`, `shop_id`, `status`, `accounting_date`),
  CONSTRAINT `fk_reconciliation_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_reconciliation_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_reconciliation_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_reconciliation_amounts`
    CHECK (
      `system_payment_amount` >= 0 AND `system_refund_amount` >= 0
      AND `channel_payment_amount` >= 0 AND `channel_refund_amount` >= 0
    ),
  CONSTRAINT `ck_reconciliation_status`
    CHECK (`status` IN (
      'PENDING', 'RUNNING', 'MATCHED', 'DIFFERENT', 'RESOLVED', 'CLOSED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='日终支付退款对账批次';

CREATE TABLE `reconciliation_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `batch_id` bigint unsigned NOT NULL,
  `business_type` varchar(20) NOT NULL,
  `business_no` varchar(100) NOT NULL,
  `system_amount` decimal(14,2) DEFAULT NULL,
  `channel_amount` decimal(14,2) DEFAULT NULL,
  `difference_type` varchar(32) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reconciliation_item_business`
    (`batch_id`, `business_type`, `business_no`),
  KEY `idx_reconciliation_item_difference`
    (`batch_id`, `difference_type`),
  CONSTRAINT `fk_reconciliation_item_batch`
    FOREIGN KEY (`batch_id`) REFERENCES `reconciliation_batch` (`id`),
  CONSTRAINT `ck_reconciliation_item_type`
    CHECK (`business_type` IN ('PAYMENT', 'REFUND')),
  CONSTRAINT `ck_reconciliation_difference`
    CHECK (`difference_type` IN (
      'MISSING_CHANNEL', 'MISSING_SYSTEM', 'AMOUNT_MISMATCH', 'STATUS_MISMATCH'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='对账差异明细';

CREATE TABLE `reconciliation_resolution` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `batch_id` bigint unsigned NOT NULL,
  `resolution_note` varchar(1000) NOT NULL,
  `evidence_reference` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_reconciliation_resolution_batch`
    (`batch_id`, `created_at`, `id`),
  CONSTRAINT `fk_reconciliation_resolution_batch`
    FOREIGN KEY (`batch_id`) REFERENCES `reconciliation_batch` (`id`),
  CONSTRAINT `fk_reconciliation_resolution_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='对账差异追加处理记录';

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('reconciliation:view', '查看支付对账', 'reconciliation', 'SENSITIVE'),
  ('reconciliation:manage', '处理支付对账', 'reconciliation', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'reconciliation:view'
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'reconciliation:manage'
WHERE r.`role_code` IN ('OWNER', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
