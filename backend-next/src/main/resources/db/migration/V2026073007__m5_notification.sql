CREATE TABLE `notification_projection_checkpoint` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `event_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `outbox_event_row_id` bigint unsigned NOT NULL,
  `event_type` varchar(120) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int unsigned NOT NULL DEFAULT 0,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_error_code` varchar(80) DEFAULT NULL,
  `message_count` int unsigned NOT NULL DEFAULT 0,
  `projected_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_projection_event` (`tenant_id`, `event_id`),
  KEY `idx_notification_projection_retry` (`status`, `next_retry_at`, `id`),
  CONSTRAINT `fk_notification_projection_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_notification_projection_outbox`
    FOREIGN KEY (`outbox_event_row_id`) REFERENCES `outbox_event` (`id`),
  CONSTRAINT `ck_notification_projection_status`
    CHECK (`status` IN ('PENDING', 'PROJECTED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 outbox 到站内通知的可重试投影游标';

CREATE TABLE `notification_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `recipient_account_id` bigint unsigned NOT NULL,
  `event_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_type` varchar(120) NOT NULL,
  `business_type` varchar(80) NOT NULL,
  `business_id` varchar(100) NOT NULL,
  `category` varchar(30) NOT NULL,
  `channel` varchar(20) NOT NULL DEFAULT 'IN_APP',
  `delivery_status` varchar(20) NOT NULL DEFAULT 'DELIVERED',
  `external_status` varchar(20) NOT NULL DEFAULT 'UNAVAILABLE',
  `title` varchar(120) NOT NULL,
  `safe_summary` varchar(500) NOT NULL,
  `action_path` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'UNREAD',
  `read_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_event_recipient_channel`
    (`tenant_id`, `event_id`, `recipient_account_id`, `channel`),
  KEY `idx_notification_recipient_status`
    (`tenant_id`, `recipient_account_id`, `status`, `created_at`, `id`),
  KEY `idx_notification_shop_event`
    (`tenant_id`, `shop_id`, `event_type`, `created_at`),
  CONSTRAINT `fk_notification_tenant`
    FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_notification_shop`
    FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_notification_recipient`
    FOREIGN KEY (`recipient_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_notification_event`
    FOREIGN KEY (`event_id`) REFERENCES `outbox_event` (`event_id`),
  CONSTRAINT `ck_notification_category`
    CHECK (`category` IN ('APPROVAL', 'AFTERSALE', 'COMMISSION', 'SETTLEMENT', 'SYSTEM')),
  CONSTRAINT `ck_notification_channel`
    CHECK (`channel` = 'IN_APP'),
  CONSTRAINT `ck_notification_delivery_status`
    CHECK (`delivery_status` = 'DELIVERED'),
  CONSTRAINT `ck_notification_external_status`
    CHECK (`external_status` IN ('UNAVAILABLE', 'NOT_REQUESTED')),
  CONSTRAINT `ck_notification_status`
    CHECK (`status` IN ('UNREAD', 'READ')),
  CONSTRAINT `ck_notification_read_at`
    CHECK ((`status` = 'UNREAD' AND `read_at` IS NULL)
      OR (`status` = 'READ' AND `read_at` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='M5 本人范围站内通知，只保存安全摘要';

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('notification:view:self', '查看本人通知', 'notification', 'NORMAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.`permission_code` = 'notification:view:self'
WHERE r.`role_code` IN (
  'OWNER', 'MANAGER', 'FRONT_DESK', 'BEAUTICIAN', 'FINANCE', 'MEMBER'
)
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
