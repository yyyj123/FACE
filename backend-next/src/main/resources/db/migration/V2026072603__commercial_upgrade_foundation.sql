-- M1-01 commercial upgrade foundation.
-- Compatibility: additive only. Existing V1/V2 applications ignore these
-- tables and the additional index.

CREATE TABLE `data_access_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `account_id` bigint unsigned NOT NULL,
  `resource_type` varchar(80) NOT NULL,
  `resource_id` varchar(100) DEFAULT NULL,
  `action` varchar(40) NOT NULL,
  `result` varchar(20) NOT NULL,
  `request_id` varchar(64) DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_data_access_account_time` (`tenant_id`, `account_id`, `created_at`),
  KEY `idx_data_access_resource_time` (`tenant_id`, `resource_type`, `resource_id`, `created_at`),
  KEY `idx_data_access_request` (`request_id`),
  CONSTRAINT `fk_data_access_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_data_access_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_data_access_account`
    FOREIGN KEY (`account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_data_access_action`
    CHECK (`action` IN ('VIEW', 'EXPORT', 'DOWNLOAD', 'PRINT')),
  CONSTRAINT `ck_data_access_result`
    CHECK (`result` IN ('ALLOWED', 'DENIED', 'FAILED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='敏感数据访问审计，只记录标识和结果，不保存敏感正文';

CREATE TABLE `outbox_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `aggregate_type` varchar(80) NOT NULL,
  `aggregate_id` varchar(100) NOT NULL,
  `event_type` varchar(120) NOT NULL,
  `payload` json NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int unsigned NOT NULL DEFAULT 0,
  `available_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `published_at` datetime(3) DEFAULT NULL,
  `last_error_code` varchar(80) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_dispatch` (`status`, `available_at`, `id`),
  KEY `idx_outbox_aggregate` (`tenant_id`, `aggregate_type`, `aggregate_id`, `created_at`),
  CONSTRAINT `fk_outbox_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_outbox_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_outbox_status`
    CHECK (`status` IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='领域事件事务发件箱，payload 禁止存放支付凭据和客户敏感正文';

CREATE INDEX `idx_account_shop_role_permission_lookup`
  ON `account_shop_role` (
    `account_id`,
    `tenant_id`,
    `status`,
    `role_id`,
    `effective_from`,
    `effective_to`,
    `region_id`,
    `shop_id`
  );
