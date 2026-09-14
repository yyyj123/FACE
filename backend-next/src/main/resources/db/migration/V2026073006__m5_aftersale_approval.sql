CREATE TABLE `after_sale_case` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `case_no` varchar(40) NOT NULL,
  `member_id` bigint unsigned DEFAULT NULL,
  `order_id` bigint unsigned DEFAULT NULL,
  `service_record_id` bigint unsigned DEFAULT NULL,
  `category` varchar(30) NOT NULL,
  `priority` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `summary` varchar(500) NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'OPEN',
  `assignee_account_id` bigint unsigned DEFAULT NULL,
  `refund_id` bigint unsigned DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `create_idempotency_key` varchar(100) NOT NULL,
  `create_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_sale_case_no` (`tenant_id`, `case_no`),
  UNIQUE KEY `uk_after_sale_create_key` (`tenant_id`, `create_idempotency_key`),
  KEY `idx_after_sale_shop_status` (`tenant_id`, `shop_id`, `status`, `created_at`),
  KEY `idx_after_sale_member` (`tenant_id`, `member_id`, `created_at`),
  KEY `idx_after_sale_order` (`tenant_id`, `order_id`),
  CONSTRAINT `fk_after_sale_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_after_sale_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_after_sale_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_after_sale_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_after_sale_service` FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_after_sale_assignee` FOREIGN KEY (`assignee_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_after_sale_refund` FOREIGN KEY (`refund_id`) REFERENCES `refund_transaction` (`id`),
  CONSTRAINT `fk_after_sale_created_by` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_after_sale_status` CHECK (`status` IN (
    'OPEN', 'TRIAGED', 'PROCESSING', 'WAITING_CUSTOMER',
    'RESOLVED', 'CLOSED', 'REJECTED', 'REOPENED'
  )),
  CONSTRAINT `ck_after_sale_priority` CHECK (`priority` IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
  CONSTRAINT `ck_after_sale_category` CHECK (`category` IN (
    'SERVICE_QUALITY', 'REFUND', 'PACKAGE', 'ACCOUNT', 'PRODUCT', 'OTHER'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 售后工单';

CREATE TABLE `after_sale_case_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `case_id` bigint unsigned NOT NULL,
  `action` varchar(40) NOT NULL,
  `from_status` varchar(30) DEFAULT NULL,
  `to_status` varchar(30) NOT NULL,
  `safe_note` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_sale_log_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_after_sale_log_case` (`tenant_id`, `case_id`, `created_at`, `id`),
  CONSTRAINT `fk_after_sale_log_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_after_sale_log_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_after_sale_log_case` FOREIGN KEY (`case_id`) REFERENCES `after_sale_case` (`id`),
  CONSTRAINT `fk_after_sale_log_created_by` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 售后工单追加式日志';

CREATE TABLE `approval_instance` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `approval_no` varchar(40) NOT NULL,
  `business_type` varchar(40) NOT NULL,
  `business_id` bigint unsigned NOT NULL,
  `approval_type` varchar(40) NOT NULL,
  `safe_summary` varchar(500) NOT NULL,
  `requester_account_id` bigint unsigned NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `version` int unsigned NOT NULL DEFAULT 0,
  `active_flag` tinyint unsigned NOT NULL DEFAULT 1,
  `previous_instance_id` bigint unsigned DEFAULT NULL,
  `create_idempotency_key` varchar(100) NOT NULL,
  `create_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `active_business_key` varchar(180)
    GENERATED ALWAYS AS (
      CASE WHEN `active_flag` = 1
        THEN CONCAT(`business_type`, ':', `business_id`, ':', `approval_type`)
        ELSE NULL END
    ) STORED,
  `decided_by` bigint unsigned DEFAULT NULL,
  `decided_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_no` (`tenant_id`, `approval_no`),
  UNIQUE KEY `uk_approval_create_key` (`tenant_id`, `create_idempotency_key`),
  UNIQUE KEY `uk_approval_active_business` (`tenant_id`, `active_business_key`),
  KEY `idx_approval_requester_status`
    (`tenant_id`, `requester_account_id`, `status`, `created_at`),
  CONSTRAINT `fk_approval_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_approval_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_approval_requester` FOREIGN KEY (`requester_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_approval_previous` FOREIGN KEY (`previous_instance_id`) REFERENCES `approval_instance` (`id`),
  CONSTRAINT `fk_approval_decided_by` FOREIGN KEY (`decided_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_approval_status`
    CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
  CONSTRAINT `ck_approval_active`
    CHECK ((`status` = 'PENDING' AND `active_flag` = 1)
      OR (`status` <> 'PENDING' AND `active_flag` = 0)),
  CONSTRAINT `ck_approval_decision_fields`
    CHECK ((`status` = 'PENDING' AND `decided_by` IS NULL AND `decided_at` IS NULL)
      OR (`status` <> 'PENDING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 通用审批实例';

CREATE TABLE `approval_step` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `approval_instance_id` bigint unsigned NOT NULL,
  `step_no` int unsigned NOT NULL,
  `candidate_permission` varchar(100) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `decision` varchar(20) DEFAULT NULL,
  `decision_reason` varchar(500) DEFAULT NULL,
  `decided_by` bigint unsigned DEFAULT NULL,
  `decided_at` datetime(3) DEFAULT NULL,
  `decision_idempotency_key` varchar(100) DEFAULT NULL,
  `decision_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_step_no` (`tenant_id`, `approval_instance_id`, `step_no`),
  UNIQUE KEY `uk_approval_step_decision_key` (`tenant_id`, `decision_idempotency_key`),
  KEY `idx_approval_step_candidate` (`tenant_id`, `candidate_permission`, `status`, `created_at`),
  CONSTRAINT `fk_approval_step_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_approval_step_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_approval_step_instance`
    FOREIGN KEY (`approval_instance_id`) REFERENCES `approval_instance` (`id`),
  CONSTRAINT `fk_approval_step_decided_by` FOREIGN KEY (`decided_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_approval_step_no` CHECK (`step_no` > 0),
  CONSTRAINT `ck_approval_step_status` CHECK (`status` IN ('PENDING', 'DECIDED', 'CANCELLED')),
  CONSTRAINT `ck_approval_step_decision`
    CHECK (`decision` IS NULL OR `decision` IN ('APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 通用审批步骤与不可变决定';

ALTER TABLE `commission_entry`
  ADD COLUMN `approval_instance_id` bigint unsigned DEFAULT NULL AFTER `refund_id`,
  ADD UNIQUE KEY `uk_commission_entry_approval` (`tenant_id`, `approval_instance_id`),
  ADD CONSTRAINT `fk_commission_entry_approval`
    FOREIGN KEY (`approval_instance_id`) REFERENCES `approval_instance` (`id`),
  DROP CHECK `ck_commission_entry_type`,
  DROP CHECK `ck_commission_entry_direction`,
  DROP CHECK `ck_commission_entry_link`,
  ADD CONSTRAINT `ck_commission_entry_type`
    CHECK (`entry_type` IN ('ACCRUAL', 'REVERSAL', 'ADJUSTMENT')),
  ADD CONSTRAINT `ck_commission_entry_direction`
    CHECK (
      (`entry_type` = 'ACCRUAL' AND `amount` > 0)
      OR (`entry_type` = 'REVERSAL' AND `amount` < 0)
      OR (`entry_type` = 'ADJUSTMENT' AND `amount` <> 0)
    ),
  ADD CONSTRAINT `ck_commission_entry_link`
    CHECK (
      (`entry_type` = 'ACCRUAL' AND `original_entry_id` IS NULL
        AND `refund_id` IS NULL AND `approval_instance_id` IS NULL)
      OR (`entry_type` = 'REVERSAL' AND `original_entry_id` IS NOT NULL
        AND `refund_id` IS NOT NULL AND `approval_instance_id` IS NULL)
      OR (`entry_type` = 'ADJUSTMENT' AND `original_entry_id` IS NOT NULL
        AND `refund_id` IS NULL AND `approval_instance_id` IS NOT NULL)
    );

ALTER TABLE `commission_entry_history`
  DROP CHECK `ck_commission_history_action`,
  ADD CONSTRAINT `ck_commission_history_action`
    CHECK (`action` IN (
      'ACCRUED', 'FROZEN', 'UNFROZEN', 'REVERSED',
      'SETTLED', 'SETTLEMENT_VOIDED', 'ADJUSTED'
    ));

CREATE TABLE `commission_adjustment_request` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `original_entry_id` bigint unsigned NOT NULL,
  `requested_amount` decimal(14,2) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `approval_instance_id` bigint unsigned DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `applied_entry_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_adjustment_key` (`tenant_id`, `idempotency_key`),
  UNIQUE KEY `uk_commission_adjustment_approval` (`tenant_id`, `approval_instance_id`),
  UNIQUE KEY `uk_commission_adjustment_applied_entry` (`tenant_id`, `applied_entry_id`),
  KEY `idx_commission_adjustment_original`
    (`tenant_id`, `shop_id`, `original_entry_id`, `status`),
  CONSTRAINT `fk_commission_adjustment_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_commission_adjustment_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_commission_adjustment_original`
    FOREIGN KEY (`original_entry_id`) REFERENCES `commission_entry` (`id`),
  CONSTRAINT `fk_commission_adjustment_approval`
    FOREIGN KEY (`approval_instance_id`) REFERENCES `approval_instance` (`id`),
  CONSTRAINT `fk_commission_adjustment_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_commission_adjustment_applied`
    FOREIGN KEY (`applied_entry_id`) REFERENCES `commission_entry` (`id`),
  CONSTRAINT `ck_commission_adjustment_amount` CHECK (`requested_amount` <> 0),
  CONSTRAINT `ck_commission_adjustment_status`
    CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'APPLIED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 经审批的提成调整申请';

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('commission:entry:adjust', '申请提成调整', 'commission', 'CRITICAL'),
  ('aftersale:view', '查看售后工单', 'aftersale', 'SENSITIVE'),
  ('aftersale:create', '创建售后工单', 'aftersale', 'SENSITIVE'),
  ('aftersale:manage', '处理售后工单', 'aftersale', 'CRITICAL'),
  ('approval:view', '查看审批', 'approval', 'SENSITIVE'),
  ('approval:decide', '决定审批', 'approval', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p ON p.`permission_code` IN (
  'commission:entry:adjust', 'aftersale:view', 'aftersale:create',
  'aftersale:manage', 'approval:view', 'approval:decide'
)
WHERE r.`role_code` IN ('OWNER', 'MANAGER')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p ON p.`permission_code` IN (
  'aftersale:view', 'aftersale:create', 'approval:view'
)
WHERE r.`role_code` IN ('FRONT_DESK', 'BEAUTICIAN', 'FINANCE')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
