CREATE TABLE `training_course` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `course_code` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` int unsigned NOT NULL DEFAULT 1,
  `title` varchar(120) NOT NULL,
  `safe_summary` varchar(500) NOT NULL,
  `pass_score` tinyint unsigned NOT NULL DEFAULT 80,
  `validity_days` int unsigned DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `published_by` bigint unsigned DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `retired_by` bigint unsigned DEFAULT NULL,
  `retired_at` datetime(3) DEFAULT NULL,
  `create_idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_training_course_revision` (`tenant_id`, `shop_id`, `course_code`, `revision`),
  UNIQUE KEY `uk_training_course_create_idempotency` (`tenant_id`, `create_idempotency_key`),
  KEY `idx_training_course_shop_status` (`tenant_id`, `shop_id`, `status`, `updated_at`),
  CONSTRAINT `fk_training_course_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_training_course_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_training_course_created_by` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_training_course_published_by` FOREIGN KEY (`published_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_training_course_retired_by` FOREIGN KEY (`retired_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_training_course_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT `ck_training_course_score` CHECK (`pass_score` BETWEEN 0 AND 100),
  CONSTRAINT `ck_training_course_validity` CHECK (`validity_days` IS NULL OR `validity_days` BETWEEN 1 AND 3650),
  CONSTRAINT `ck_training_course_dates` CHECK (
    (`status` = 'DRAFT' AND `published_at` IS NULL AND `retired_at` IS NULL)
    OR (`status` = 'ACTIVE' AND `published_at` IS NOT NULL AND `retired_at` IS NULL)
    OR (`status` = 'RETIRED' AND `published_at` IS NOT NULL AND `retired_at` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 版本化培训课程，发布后内容不可覆盖';

CREATE TABLE `training_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `record_no` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `course_id` bigint unsigned NOT NULL,
  `staff_id` bigint unsigned NOT NULL,
  `assigned_by` bigint unsigned NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ASSIGNED',
  `due_at` datetime(3) DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `submitted_at` datetime(3) DEFAULT NULL,
  `score` tinyint unsigned DEFAULT NULL,
  `evidence_summary` varchar(500) DEFAULT NULL,
  `verified_by` bigint unsigned DEFAULT NULL,
  `verified_at` datetime(3) DEFAULT NULL,
  `certificate_no` varchar(60) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `valid_until` date DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `assign_idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_training_record_no` (`tenant_id`, `record_no`),
  UNIQUE KEY `uk_training_record_assign_idempotency` (`tenant_id`, `assign_idempotency_key`),
  UNIQUE KEY `uk_training_certificate_no` (`tenant_id`, `certificate_no`),
  KEY `idx_training_record_staff` (`tenant_id`, `shop_id`, `staff_id`, `status`, `due_at`),
  KEY `idx_training_record_course` (`tenant_id`, `shop_id`, `course_id`, `status`),
  CONSTRAINT `fk_training_record_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_training_record_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_training_record_course` FOREIGN KEY (`course_id`) REFERENCES `training_course` (`id`),
  CONSTRAINT `fk_training_record_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_training_record_assigned_by` FOREIGN KEY (`assigned_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_training_record_verified_by` FOREIGN KEY (`verified_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_training_record_status` CHECK (`status` IN (
    'ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'PASSED', 'FAILED', 'EXPIRED', 'CANCELLED'
  )),
  CONSTRAINT `ck_training_record_score` CHECK (`score` IS NULL OR `score` BETWEEN 0 AND 100),
  CONSTRAINT `ck_training_record_result` CHECK (
    (`status` NOT IN ('PASSED', 'FAILED', 'EXPIRED') AND `verified_by` IS NULL AND `verified_at` IS NULL)
    OR (`status` IN ('PASSED', 'FAILED', 'EXPIRED') AND `score` IS NOT NULL AND `verified_by` IS NOT NULL AND `verified_at` IS NOT NULL)
  ),
  CONSTRAINT `ck_training_record_certificate` CHECK (
    (`status` IN ('PASSED', 'EXPIRED') AND `certificate_no` IS NOT NULL)
    OR (`status` NOT IN ('PASSED', 'EXPIRED') AND `certificate_no` IS NULL AND `valid_until` IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 员工培训分配、提交、验证与证书当前状态';

CREATE TABLE `training_record_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `training_record_id` bigint unsigned NOT NULL,
  `from_status` varchar(20) DEFAULT NULL,
  `to_status` varchar(20) NOT NULL,
  `actor_account_id` bigint unsigned NOT NULL,
  `score` tinyint unsigned DEFAULT NULL,
  `safe_reason` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_training_history_idempotency` (`tenant_id`, `training_record_id`, `idempotency_key`),
  KEY `idx_training_history_record` (`tenant_id`, `training_record_id`, `created_at`, `id`),
  CONSTRAINT `fk_training_history_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_training_history_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_training_history_record` FOREIGN KEY (`training_record_id`) REFERENCES `training_record` (`id`),
  CONSTRAINT `fk_training_history_actor` FOREIGN KEY (`actor_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_training_history_status` CHECK (`to_status` IN (
    'ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'PASSED', 'FAILED', 'EXPIRED', 'CANCELLED'
  )),
  CONSTRAINT `ck_training_history_score` CHECK (`score` IS NULL OR `score` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 培训记录不可变状态历史';

CREATE TABLE `integration_client` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `client_code` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `client_name` varchar(120) NOT NULL,
  `safe_description` varchar(500) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `scopes_json` json NOT NULL,
  `secret_version` int unsigned NOT NULL DEFAULT 1,
  `secret_prefix` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `secret_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rate_limit_per_minute` int unsigned NOT NULL DEFAULT 30,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_by` bigint unsigned NOT NULL,
  `rotated_by` bigint unsigned DEFAULT NULL,
  `rotated_at` datetime(3) DEFAULT NULL,
  `revoked_by` bigint unsigned DEFAULT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `last_used_at` datetime(3) DEFAULT NULL,
  `create_idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_client_code` (`tenant_id`, `shop_id`, `client_code`),
  UNIQUE KEY `uk_integration_client_create_idempotency` (`tenant_id`, `create_idempotency_key`),
  KEY `idx_integration_client_shop_status` (`tenant_id`, `shop_id`, `status`, `updated_at`),
  CONSTRAINT `fk_integration_client_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_integration_client_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_integration_client_created_by` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_integration_client_rotated_by` FOREIGN KEY (`rotated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_integration_client_revoked_by` FOREIGN KEY (`revoked_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_integration_client_status` CHECK (`status` IN ('ACTIVE', 'REVOKED')),
  CONSTRAINT `ck_integration_client_rate` CHECK (`rate_limit_per_minute` BETWEEN 1 AND 60),
  CONSTRAINT `ck_integration_client_revoke` CHECK (
    (`status` = 'ACTIVE' AND `revoked_at` IS NULL AND `revoked_by` IS NULL)
    OR (`status` = 'REVOKED' AND `revoked_at` IS NOT NULL AND `revoked_by` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 门店开放平台客户端，完整密钥永不落库';

CREATE TABLE `integration_client_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `integration_client_id` bigint unsigned NOT NULL,
  `action` varchar(20) NOT NULL,
  `secret_version` int unsigned NOT NULL,
  `secret_prefix` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_account_id` bigint unsigned NOT NULL,
  `safe_reason` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_event_idempotency` (`tenant_id`, `integration_client_id`, `idempotency_key`),
  KEY `idx_integration_event_client` (`tenant_id`, `integration_client_id`, `created_at`, `id`),
  CONSTRAINT `fk_integration_event_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_integration_event_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_integration_event_client` FOREIGN KEY (`integration_client_id`) REFERENCES `integration_client` (`id`),
  CONSTRAINT `fk_integration_event_actor` FOREIGN KEY (`actor_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_integration_event_action` CHECK (`action` IN ('CREATED', 'ROTATED', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 集成客户端密钥生命周期不可变事件';

CREATE TABLE `integration_request_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `integration_client_id` bigint unsigned NOT NULL,
  `request_id` varchar(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `nonce` varchar(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scope_code` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `http_method` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_path` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result` varchar(20) NOT NULL,
  `response_code` int unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_request_nonce` (`integration_client_id`, `nonce`),
  UNIQUE KEY `uk_integration_request_id` (`tenant_id`, `request_id`),
  KEY `idx_integration_request_rate` (`integration_client_id`, `created_at`),
  KEY `idx_integration_request_result` (`tenant_id`, `shop_id`, `result`, `created_at`),
  CONSTRAINT `fk_integration_request_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_integration_request_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_integration_request_client` FOREIGN KEY (`integration_client_id`) REFERENCES `integration_client` (`id`),
  CONSTRAINT `ck_integration_request_result` CHECK (`result` IN ('ALLOWED', 'DENIED')),
  CONSTRAINT `ck_integration_request_response` CHECK (`response_code` BETWEEN 100 AND 599)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 开放接口防重放、限流与结果审计账本';

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('training:view', '查看员工培训', 'training', 'NORMAL'),
  ('training:manage', '维护课程与分配培训', 'training', 'SENSITIVE'),
  ('training:verify', '验证培训结果', 'training', 'CRITICAL'),
  ('training:self', '查看与提交本人培训', 'training', 'NORMAL'),
  ('integration:view', '查看开放平台客户端', 'integration', 'SENSITIVE'),
  ('integration:manage', '创建开放平台客户端', 'integration', 'CRITICAL'),
  ('integration:rotate', '轮换或撤销开放平台密钥', 'integration', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` IN (
    'training:view', 'training:manage', 'training:verify',
    'integration:view', 'integration:manage', 'integration:rotate'
  )
WHERE r.`role_code` IN ('OWNER', 'MANAGER', 'REGIONAL_MANAGER')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p ON p.`permission_code` = 'training:self'
WHERE r.`role_code` = 'BEAUTICIAN'
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
