CREATE TABLE `report_snapshot` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `requested_by_account_id` bigint unsigned NOT NULL,
  `report_type` varchar(40) NOT NULL,
  `format` varchar(10) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'READY',
  `shop_ids_json` json NOT NULL,
  `from_date` date NOT NULL,
  `to_date` date NOT NULL,
  `item_type` varchar(20) NOT NULL,
  `metric_version` varchar(40) NOT NULL,
  `idempotency_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content` mediumblob NOT NULL,
  `content_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_bytes` bigint unsigned NOT NULL,
  `row_count` int unsigned NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `ready_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_snapshot_idempotency`
    (`tenant_id`, `requested_by_account_id`, `idempotency_key`),
  KEY `idx_report_snapshot_requester`
    (`tenant_id`, `requested_by_account_id`, `created_at`, `id`),
  KEY `idx_report_snapshot_expiry` (`status`, `expires_at`, `id`),
  CONSTRAINT `fk_report_snapshot_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_report_snapshot_requester`
    FOREIGN KEY (`requested_by_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_report_snapshot_type`
    CHECK (`report_type` = 'SALES_OVERVIEW'),
  CONSTRAINT `ck_report_snapshot_format`
    CHECK (`format` = 'CSV'),
  CONSTRAINT `ck_report_snapshot_status`
    CHECK (`status` IN ('READY', 'EXPIRED')),
  CONSTRAINT `ck_report_snapshot_item_type`
    CHECK (`item_type` IN ('ALL', 'SERVICE', 'PRODUCT')),
  CONSTRAINT `ck_report_snapshot_hashes`
    CHECK (`request_hash` REGEXP '^[0-9a-f]{64}$'
      AND `content_sha256` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `ck_report_snapshot_ready_content`
    CHECK (`content_bytes` = OCTET_LENGTH(`content`)
      AND `content_bytes` > 0
      AND `row_count` > 0
      AND `expires_at` > `ready_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M6 immutable, self-scoped analytics report snapshots';
