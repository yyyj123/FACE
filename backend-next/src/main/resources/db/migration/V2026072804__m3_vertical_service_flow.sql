-- M3 vertical service flow.
-- Expand-only migration: existing V1/V2/M2 code can ignore every new table
-- and nullable column introduced here.

CREATE TABLE `customer_confirmation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `appointment_id` bigint unsigned DEFAULT NULL,
  `service_record_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `confirmation_type` varchar(40) NOT NULL DEFAULT 'SERVICE_RESULT',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `reject_reason` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(80) DEFAULT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `acted_by` bigint unsigned DEFAULT NULL,
  `acted_at` datetime(3) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `updated_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_confirmation_record_type`
    (`tenant_id`, `service_record_id`, `confirmation_type`),
  UNIQUE KEY `uk_customer_confirmation_idempotency`
    (`tenant_id`, `idempotency_key`),
  KEY `idx_customer_confirmation_member_status`
    (`tenant_id`, `member_id`, `status`, `created_at`),
  KEY `idx_customer_confirmation_shop_time`
    (`tenant_id`, `shop_id`, `created_at`),
  CONSTRAINT `fk_customer_confirmation_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_customer_confirmation_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_customer_confirmation_appointment`
    FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `fk_customer_confirmation_record`
    FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_customer_confirmation_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_customer_confirmation_actor`
    FOREIGN KEY (`acted_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_customer_confirmation_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_customer_confirmation_updater`
    FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_customer_confirmation_type`
    CHECK (`confirmation_type` IN ('SERVICE_RESULT')),
  CONSTRAINT `ck_customer_confirmation_status`
    CHECK (`status` IN ('PENDING', 'CONFIRMED', 'REJECTED')),
  CONSTRAINT `ck_customer_confirmation_rejection`
    CHECK (
      (`status` <> 'REJECTED' AND `reject_reason` IS NULL)
      OR (`status` = 'REJECTED' AND `reject_reason` IS NOT NULL)
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='会员对已完成护理结果的独立确认事实';

CREATE TABLE `service_record_correction` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `service_record_id` bigint unsigned NOT NULL,
  `base_version` int unsigned NOT NULL,
  `correction_type` varchar(40) NOT NULL DEFAULT 'CARE_FACT',
  `reason` varchar(500) NOT NULL,
  `corrected_fields` json NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_correction_idempotency`
    (`tenant_id`, `idempotency_key`),
  KEY `idx_service_correction_record_time`
    (`tenant_id`, `service_record_id`, `created_at`, `id`),
  KEY `idx_service_correction_shop_time`
    (`tenant_id`, `shop_id`, `created_at`),
  CONSTRAINT `fk_service_correction_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_service_correction_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_service_correction_record`
    FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_service_correction_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_service_correction_type`
    CHECK (`correction_type` IN ('CARE_FACT', 'VOID_NOTE')),
  CONSTRAINT `ck_service_correction_reason`
    CHECK (CHAR_LENGTH(TRIM(`reason`)) > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='已完成护理事实的追加式更正，不覆盖原始护理记录';

ALTER TABLE `sales_order`
  ADD COLUMN `create_idempotency_key` varchar(80) DEFAULT NULL AFTER `order_no`,
  ADD COLUMN `create_request_hash`
    char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    AFTER `create_idempotency_key`,
  ADD UNIQUE KEY `uk_sales_order_create_idempotency`
    (`tenant_id`, `create_idempotency_key`);

ALTER TABLE `service_record`
  ADD COLUMN `completion_request_hash`
    char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    AFTER `completion_idempotency_key`;

ALTER TABLE `payment_transaction`
  ADD COLUMN `request_hash`
    char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    AFTER `idempotency_key`;

INSERT INTO `permission_definition` (
  `permission_code`, `permission_name`, `module_code`, `risk_level`
) VALUES
  ('service_record:correct', '追加护理事实更正', 'service_record', 'CRITICAL'),
  ('customer_confirmation:view', '查看顾客护理确认', 'customer_confirmation', 'SENSITIVE');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN (
    'service_record:correct',
    'customer_confirmation:view'
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
  ON p.`permission_code` = 'customer_confirmation:view'
WHERE r.`role_code` = 'MEMBER'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id`
      AND rp.`permission_id` = p.`id`
  );
