ALTER TABLE `refund_transaction`
  ADD COLUMN `request_hash` char(64) DEFAULT NULL AFTER `idempotency_key`,
  ADD COLUMN `execution_idempotency_key` varchar(80) DEFAULT NULL AFTER `request_hash`,
  ADD COLUMN `execution_request_hash` char(64) DEFAULT NULL AFTER `execution_idempotency_key`,
  ADD COLUMN `execution_mode` varchar(24) DEFAULT NULL AFTER `execution_request_hash`,
  ADD COLUMN `external_refund_no` varchar(100) DEFAULT NULL AFTER `execution_mode`,
  ADD COLUMN `channel_status` varchar(32) DEFAULT NULL AFTER `external_refund_no`,
  ADD COLUMN `failure_code` varchar(80) DEFAULT NULL AFTER `channel_status`,
  ADD COLUMN `failed_at` datetime(3) DEFAULT NULL AFTER `failure_code`,
  ADD COLUMN `executed_by` bigint unsigned DEFAULT NULL AFTER `approved_by`,
  ADD UNIQUE KEY `uk_refund_execution_idempotency`
    (`tenant_id`, `execution_idempotency_key`),
  ADD UNIQUE KEY `uk_refund_external_no`
    (`tenant_id`, `external_refund_no`),
  ADD KEY `idx_refund_payment_status`
    (`tenant_id`, `payment_id`, `status`, `created_at`),
  ADD CONSTRAINT `fk_refund_executor`
    FOREIGN KEY (`executed_by`) REFERENCES `account` (`id`);

ALTER TABLE `refund_transaction`
  DROP CHECK `ck_refund_status`,
  ADD CONSTRAINT `ck_refund_status`
    CHECK (`status` IN (
      'PENDING', 'APPROVED', 'PROCESSING', 'SUCCESS', 'REJECTED', 'FAILED'
    )),
  ADD CONSTRAINT `ck_refund_execution_mode`
    CHECK (`execution_mode` IS NULL OR `execution_mode` IN (
      'LOCAL_LEDGER', 'EXTERNAL_ADAPTER'
    ));

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('refund:request', '申请退款', 'order', 'SENSITIVE'),
  ('refund:execute', '执行退款', 'order', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'refund:request'
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'FRONT_DESK')
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'refund:execute'
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
