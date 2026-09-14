CREATE TABLE `marketing_campaign` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `campaign_no` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `title` varchar(120) NOT NULL,
  `safe_summary` varchar(500) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `action_path` varchar(255) DEFAULT NULL,
  `scheduled_at` datetime(3) DEFAULT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'DRAFT',
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `submitted_by` bigint unsigned DEFAULT NULL,
  `submitted_at` datetime(3) DEFAULT NULL,
  `approved_by` bigint unsigned DEFAULT NULL,
  `approved_at` datetime(3) DEFAULT NULL,
  `executed_by` bigint unsigned DEFAULT NULL,
  `executed_at` datetime(3) DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `cancelled_by` bigint unsigned DEFAULT NULL,
  `cancelled_at` datetime(3) DEFAULT NULL,
  `cancel_reason` varchar(500) DEFAULT NULL,
  `create_idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `create_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketing_campaign_no` (`tenant_id`, `campaign_no`),
  UNIQUE KEY `uk_marketing_campaign_create_idempotency`
    (`tenant_id`, `created_by`, `create_idempotency_key`),
  KEY `idx_marketing_campaign_shop_status`
    (`tenant_id`, `shop_id`, `status`, `created_at`, `id`),
  KEY `idx_marketing_campaign_approval`
    (`tenant_id`, `status`, `submitted_at`, `id`),
  CONSTRAINT `fk_marketing_campaign_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_marketing_campaign_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_marketing_campaign_creator`
    FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_marketing_campaign_submitter`
    FOREIGN KEY (`submitted_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_marketing_campaign_approver`
    FOREIGN KEY (`approved_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_marketing_campaign_executor`
    FOREIGN KEY (`executed_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_marketing_campaign_canceller`
    FOREIGN KEY (`cancelled_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_marketing_campaign_status`
    CHECK (`status` IN (
      'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED',
      'RUNNING', 'COMPLETED', 'CANCELLED'
    )),
  CONSTRAINT `ck_marketing_campaign_channel`
    CHECK (`channel` IN ('IN_APP', 'SMS', 'EMAIL', 'WECHAT')),
  CONSTRAINT `ck_marketing_campaign_approval_separation`
    CHECK (`approved_by` IS NULL OR
      (`approved_by` <> `created_by` AND `approved_by` <> `submitted_by`)),
  CONSTRAINT `ck_marketing_campaign_status_time`
    CHECK (
      (`status` = 'DRAFT' AND `submitted_at` IS NULL)
      OR (`status` IN ('PENDING_APPROVAL', 'REJECTED') AND `submitted_at` IS NOT NULL)
      OR (`status` = 'APPROVED' AND `approved_at` IS NOT NULL)
      OR (`status` = 'RUNNING' AND `executed_at` IS NOT NULL)
      OR (`status` = 'COMPLETED' AND `executed_at` IS NOT NULL AND `completed_at` IS NOT NULL)
      OR (`status` = 'CANCELLED' AND `cancelled_at` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 营销活动受控状态机，不存放客户敏感资料';

CREATE TABLE `marketing_campaign_status_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `campaign_id` bigint unsigned NOT NULL,
  `from_status` varchar(30) DEFAULT NULL,
  `to_status` varchar(30) NOT NULL,
  `actor_account_id` bigint unsigned NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketing_status_idempotency`
    (`tenant_id`, `campaign_id`, `idempotency_key`),
  KEY `idx_marketing_status_campaign`
    (`tenant_id`, `campaign_id`, `created_at`, `id`),
  CONSTRAINT `fk_marketing_status_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_marketing_status_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_marketing_status_campaign`
    FOREIGN KEY (`campaign_id`) REFERENCES `marketing_campaign` (`id`),
  CONSTRAINT `fk_marketing_status_actor`
    FOREIGN KEY (`actor_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_marketing_status_target`
    CHECK (`to_status` IN (
      'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED',
      'RUNNING', 'COMPLETED', 'CANCELLED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 营销活动不可变状态历史';

CREATE TABLE `member_marketing_consent` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `channel` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `consent_text_version` varchar(40) NOT NULL,
  `consent_text_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source` varchar(30) NOT NULL DEFAULT 'MEMBER_SELF',
  `version` int unsigned NOT NULL DEFAULT 0,
  `granted_at` datetime(3) DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `updated_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_marketing_consent` (`tenant_id`, `member_id`, `channel`),
  KEY `idx_member_marketing_consent_eligible`
    (`tenant_id`, `channel`, `status`, `member_id`),
  CONSTRAINT `fk_member_marketing_consent_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_member_marketing_consent_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_member_marketing_consent_actor`
    FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_member_marketing_consent_channel`
    CHECK (`channel` IN ('IN_APP', 'SMS', 'EMAIL', 'WECHAT')),
  CONSTRAINT `ck_member_marketing_consent_status`
    CHECK (`status` IN ('GRANTED', 'REVOKED')),
  CONSTRAINT `ck_member_marketing_consent_source`
    CHECK (`source` = 'MEMBER_SELF'),
  CONSTRAINT `ck_member_marketing_consent_time`
    CHECK ((`status` = 'GRANTED' AND `granted_at` IS NOT NULL AND `revoked_at` IS NULL)
      OR (`status` = 'REVOKED' AND `revoked_at` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 会员本人营销同意当前水位，缺失记录等同未同意';

CREATE TABLE `member_marketing_consent_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `channel` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `consent_version` int unsigned NOT NULL,
  `consent_text_version` varchar(40) NOT NULL,
  `consent_text_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source` varchar(30) NOT NULL DEFAULT 'MEMBER_SELF',
  `actor_account_id` bigint unsigned NOT NULL,
  `idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `occurred_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_marketing_consent_history_idempotency`
    (`tenant_id`, `member_id`, `channel`, `idempotency_key`),
  KEY `idx_member_marketing_consent_history_member`
    (`tenant_id`, `member_id`, `channel`, `occurred_at`, `id`),
  CONSTRAINT `fk_member_marketing_consent_history_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_member_marketing_consent_history_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_member_marketing_consent_history_actor`
    FOREIGN KEY (`actor_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_member_marketing_consent_history_channel`
    CHECK (`channel` IN ('IN_APP', 'SMS', 'EMAIL', 'WECHAT')),
  CONSTRAINT `ck_member_marketing_consent_history_status`
    CHECK (`status` IN ('GRANTED', 'REVOKED')),
  CONSTRAINT `ck_member_marketing_consent_history_source`
    CHECK (`source` = 'MEMBER_SELF')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 会员营销同意与撤回不可变历史';

CREATE TABLE `marketing_campaign_audience` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `campaign_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `recipient_account_id` bigint unsigned NOT NULL,
  `consent_id` bigint unsigned NOT NULL,
  `consent_version` int unsigned NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ELIGIBLE',
  `frozen_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketing_audience_member`
    (`tenant_id`, `campaign_id`, `member_id`),
  KEY `idx_marketing_audience_campaign`
    (`tenant_id`, `campaign_id`, `status`, `id`),
  CONSTRAINT `fk_marketing_audience_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_marketing_audience_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_marketing_audience_campaign`
    FOREIGN KEY (`campaign_id`) REFERENCES `marketing_campaign` (`id`),
  CONSTRAINT `fk_marketing_audience_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_marketing_audience_account`
    FOREIGN KEY (`recipient_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_marketing_audience_consent`
    FOREIGN KEY (`consent_id`) REFERENCES `member_marketing_consent` (`id`),
  CONSTRAINT `ck_marketing_audience_status`
    CHECK (`status` = 'ELIGIBLE')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 活动执行时冻结的最小标识受众快照';

CREATE TABLE `marketing_delivery_attempt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `campaign_id` bigint unsigned NOT NULL,
  `audience_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `recipient_account_id` bigint unsigned NOT NULL,
  `channel` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `event_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `attempt_count` int unsigned NOT NULL DEFAULT 0,
  `last_error_code` varchar(80) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketing_delivery_member_channel`
    (`tenant_id`, `campaign_id`, `member_id`, `channel`),
  KEY `idx_marketing_delivery_campaign`
    (`tenant_id`, `campaign_id`, `status`, `id`),
  CONSTRAINT `fk_marketing_delivery_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_marketing_delivery_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_marketing_delivery_campaign`
    FOREIGN KEY (`campaign_id`) REFERENCES `marketing_campaign` (`id`),
  CONSTRAINT `fk_marketing_delivery_audience`
    FOREIGN KEY (`audience_id`) REFERENCES `marketing_campaign_audience` (`id`),
  CONSTRAINT `fk_marketing_delivery_member`
    FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_marketing_delivery_account`
    FOREIGN KEY (`recipient_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_marketing_delivery_event`
    FOREIGN KEY (`event_id`) REFERENCES `outbox_event` (`event_id`),
  CONSTRAINT `ck_marketing_delivery_channel`
    CHECK (`channel` IN ('IN_APP', 'SMS', 'EMAIL', 'WECHAT')),
  CONSTRAINT `ck_marketing_delivery_status`
    CHECK (`status` IN ('DELIVERED', 'UNAVAILABLE', 'FAILED')),
  CONSTRAINT `ck_marketing_delivery_truth`
    CHECK ((`status` = 'DELIVERED' AND `channel` = 'IN_APP'
      AND `event_id` IS NOT NULL AND `delivered_at` IS NOT NULL)
      OR (`status` IN ('UNAVAILABLE', 'FAILED') AND `delivered_at` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 每个冻结受众的幂等真实投递账本';

ALTER TABLE `notification_message`
  DROP CHECK `ck_notification_category`,
  ADD CONSTRAINT `ck_notification_category`
    CHECK (`category` IN (
      'APPROVAL', 'AFTERSALE', 'COMMISSION', 'SETTLEMENT', 'SYSTEM', 'MARKETING'
    ));

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('marketing:view', '查看营销活动', 'marketing', 'NORMAL'),
  ('marketing:manage', '创建与维护营销活动', 'marketing', 'SENSITIVE'),
  ('marketing:approve', '审批营销活动', 'marketing', 'CRITICAL'),
  ('marketing:execute', '执行营销活动', 'marketing', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN (
    'marketing:view', 'marketing:manage', 'marketing:approve', 'marketing:execute'
  )
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'REGIONAL_MANAGER')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
