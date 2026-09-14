-- M1-02 V3 revocable session foundation.
-- Expand-only: V1/V2 keep using the legacy token table unchanged.

CREATE TABLE `auth_session` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `session_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `tenant_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `home_shop_id` bigint unsigned DEFAULT NULL,
  `access_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `refresh_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `access_expires_at` datetime(3) NOT NULL,
  `refresh_expires_at` datetime(3) NOT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `revoke_reason` varchar(40) DEFAULT NULL,
  `last_seen_at` datetime(3) NOT NULL,
  `version` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_session_id` (`session_id`),
  UNIQUE KEY `uk_auth_session_access_hash` (`access_token_hash`),
  UNIQUE KEY `uk_auth_session_refresh_hash` (`refresh_token_hash`),
  KEY `idx_auth_session_account_active`
    (`account_id`, `revoked_at`, `refresh_expires_at`),
  KEY `idx_auth_session_tenant_created`
    (`tenant_id`, `created_at`),
  CONSTRAINT `fk_auth_session_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_auth_session_account`
    FOREIGN KEY (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_auth_session_home_shop`
    FOREIGN KEY (`home_shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_auth_session_expiry`
    CHECK (`refresh_expires_at` > `access_expires_at`),
  CONSTRAINT `ck_auth_session_revoke_reason`
    CHECK (
      (`revoked_at` IS NULL AND `revoke_reason` IS NULL)
      OR (`revoked_at` IS NOT NULL AND `revoke_reason` IS NOT NULL)
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='V3 revocable sessions; only SHA-256 token hashes are stored';
