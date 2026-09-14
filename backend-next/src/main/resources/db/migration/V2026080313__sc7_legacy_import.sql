-- SC7: idempotent legacy member/card import. SC8 demo provisioning remains out of scope.

CREATE TABLE `legacy_import_batch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `batch_no` varchar(48) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PREFLIGHTED',
  `total_rows` int unsigned NOT NULL DEFAULT 0,
  `ready_rows` int unsigned NOT NULL DEFAULT 0,
  `conflict_rows` int unsigned NOT NULL DEFAULT 0,
  `error_rows` int unsigned NOT NULL DEFAULT 0,
  `imported_members` int unsigned NOT NULL DEFAULT 0,
  `imported_cards` int unsigned NOT NULL DEFAULT 0,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `started_at` datetime(3) NULL,
  `completed_at` datetime(3) NULL,
  `created_by` bigint unsigned NOT NULL,
  `executed_by` bigint unsigned NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legacy_import_batch_no` (`tenant_id`, `batch_no`),
  UNIQUE KEY `uk_legacy_import_file` (`tenant_id`, `shop_id`, `file_sha256`),
  KEY `idx_legacy_import_history` (`tenant_id`, `shop_id`, `created_at`),
  CONSTRAINT `fk_legacy_import_batch_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_legacy_import_batch_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_legacy_import_batch_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_legacy_import_batch_executor` FOREIGN KEY (`executed_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_legacy_import_batch_status` CHECK (`status` IN ('PREFLIGHTED', 'BLOCKED', 'RUNNING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC7 历史数据导入批次';

CREATE TABLE `legacy_import_row` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `batch_id` bigint unsigned NOT NULL,
  `sheet_name` varchar(40) NOT NULL,
  `source_row_number` int unsigned NOT NULL,
  `record_type` varchar(24) NOT NULL,
  `source_system` varchar(60) NULL,
  `source_record_no` varchar(100) NULL,
  `masked_subject` varchar(120) NULL,
  `payload_json` json NOT NULL,
  `status` varchar(24) NOT NULL,
  `match_type` varchar(24) NULL,
  `matched_member_id` bigint unsigned NULL,
  `result_entity_type` varchar(30) NULL,
  `result_entity_id` bigint unsigned NULL,
  `issue_code` varchar(50) NULL,
  `issue_message` varchar(500) NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legacy_import_row` (`batch_id`, `sheet_name`, `source_row_number`),
  KEY `idx_legacy_import_row_status` (`batch_id`, `status`, `record_type`),
  CONSTRAINT `fk_legacy_import_row_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_legacy_import_row_batch` FOREIGN KEY (`batch_id`) REFERENCES `legacy_import_batch` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_legacy_import_row_member` FOREIGN KEY (`matched_member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `ck_legacy_import_row_type` CHECK (`record_type` IN ('MEMBER', 'COMBO_CARD', 'COMBO_DETAIL', 'STORED_VALUE_CARD', 'DISCOUNT_CARD')),
  CONSTRAINT `ck_legacy_import_row_status` CHECK (`status` IN ('READY_NEW', 'READY_MATCHED', 'CONFLICT', 'ERROR', 'IMPORTED', 'SKIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC7 历史数据导入预检行';

CREATE TABLE `legacy_member_source_map` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `source_system` varchar(60) NOT NULL,
  `original_member_no` varchar(100) NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `batch_id` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legacy_member_source` (`tenant_id`, `source_system`, `original_member_no`),
  KEY `idx_legacy_member_target` (`tenant_id`, `member_id`),
  CONSTRAINT `fk_legacy_member_source_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_legacy_member_source_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_legacy_member_source_batch` FOREIGN KEY (`batch_id`) REFERENCES `legacy_import_batch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC7 来源会员到统一会员映射';

ALTER TABLE `package_instance`
  ADD COLUMN `legacy_source_system` varchar(60) NULL AFTER `source_reference`,
  ADD COLUMN `legacy_original_card_no` varchar(100) NULL AFTER `legacy_source_system`,
  ADD COLUMN `legacy_original_purchase_date` date NULL AFTER `legacy_original_card_no`,
  ADD UNIQUE KEY `uk_package_legacy_card` (`tenant_id`, `legacy_source_system`, `legacy_original_card_no`);

ALTER TABLE `package_ledger`
  DROP CHECK `ck_package_ledger_type`,
  ADD CONSTRAINT `ck_package_ledger_type`
    CHECK (`entry_type` IN ('ISSUE', 'WRITE_OFF', 'REVERSAL', 'ADJUSTMENT', 'LEGACY_IMPORT'));

ALTER TABLE `stored_value_ledger`
  DROP CHECK `ck_stored_ledger_type`,
  ADD CONSTRAINT `ck_stored_ledger_type`
    CHECK (`entry_type` IN ('ISSUE', 'RESERVE', 'CONSUME', 'RELEASE', 'REFUND', 'REVERSAL', 'ADJUSTMENT', 'LEGACY_IMPORT'));

INSERT INTO `permission_definition` (`permission_code`, `permission_name`, `module_code`, `risk_level`) VALUES
  ('import:view', '查看历史数据导入', 'import', 'SENSITIVE'),
  ('import:preflight', '上传并预检历史数据', 'import', 'SENSITIVE'),
  ('import:execute', '正式执行历史数据导入', 'import', 'CRITICAL');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p ON p.`permission_code` IN ('import:view', 'import:preflight')
WHERE r.`role_code` IN ('ADMIN', 'SUPER_ADMIN');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p ON p.`permission_code` = 'import:execute'
WHERE r.`role_code` = 'SUPER_ADMIN';
