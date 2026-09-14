-- SC2 identity, content lifecycle, visible administrator roles, and single-shop foundation.
-- Existing member/account/banner facts are reused. Historical roles remain available for compatibility.

ALTER TABLE `account`
  ADD COLUMN `phone` varchar(30) NULL AFTER `display_name`,
  ADD COLUMN `version` int unsigned NOT NULL DEFAULT 1 AFTER `locked_until`;

UPDATE `account` a
JOIN `member` m
  ON m.`id` = a.`member_id`
 AND m.`tenant_id` = a.`tenant_id`
JOIN (
  SELECT `tenant_id`, `phone`
  FROM `member`
  WHERE `phone` IS NOT NULL AND `phone` <> ''
  GROUP BY `tenant_id`, `phone`
  HAVING COUNT(*) = 1
) unique_member_phone
  ON unique_member_phone.`tenant_id` = m.`tenant_id`
 AND unique_member_phone.`phone` = m.`phone`
SET a.`phone` = m.`phone`
WHERE a.`member_id` IS NOT NULL
  AND a.`phone` IS NULL;

ALTER TABLE `account`
  ADD UNIQUE KEY `uk_account_tenant_phone` (`tenant_id`, `phone`),
  DROP CHECK `ck_account_role`,
  DROP CHECK `ck_account_subject`,
  ADD CONSTRAINT `ck_account_role` CHECK (`role_code` IN (
    'OWNER', 'REGIONAL_MANAGER', 'MANAGER', 'FRONT_DESK', 'BEAUTICIAN',
    'WAREHOUSE', 'FINANCE', 'MEMBER', 'ADMIN', 'SUPER_ADMIN'
  )),
  ADD CONSTRAINT `ck_account_subject` CHECK (
    (`role_code` = 'MEMBER' AND `member_id` IS NOT NULL AND `staff_id` IS NULL)
    OR (`role_code` IN ('ADMIN', 'SUPER_ADMIN') AND `member_id` IS NULL AND `staff_id` IS NULL)
    OR (`role_code` NOT IN ('MEMBER', 'ADMIN', 'SUPER_ADMIN') AND `staff_id` IS NOT NULL AND `member_id` IS NULL)
  );

CREATE TABLE `sms_verification_challenge` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `phone` varchar(30) NOT NULL,
  `purpose` varchar(30) NOT NULL,
  `code_hash` varchar(100) NOT NULL,
  `attempt_count` smallint unsigned NOT NULL DEFAULT 0,
  `max_attempts` smallint unsigned NOT NULL DEFAULT 5,
  `expires_at` datetime(3) NOT NULL,
  `consumed_at` datetime(3) NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_sms_challenge_lookup` (`tenant_id`, `phone`, `purpose`, `created_at`),
  KEY `idx_sms_challenge_expiry` (`expires_at`, `consumed_at`),
  CONSTRAINT `fk_sms_challenge_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `ck_sms_challenge_purpose` CHECK (`purpose` IN ('REGISTER_LOGIN', 'PASSWORD_RESET')),
  CONSTRAINT `ck_sms_challenge_attempts` CHECK (`attempt_count` <= `max_attempts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='手机号验证码挑战，仅保存不可逆散列';

ALTER TABLE `banner`
  ADD COLUMN `tenant_id` bigint unsigned NULL AFTER `id`,
  ADD COLUMN `content_type` varchar(30) NOT NULL DEFAULT 'BANNER' AFTER `shop_id`,
  ADD COLUMN `summary` varchar(500) NULL AFTER `title`,
  ADD COLUMN `body_json` json NULL AFTER `summary`,
  ADD COLUMN `version` int unsigned NOT NULL DEFAULT 1 AFTER `status`,
  ADD COLUMN `scheduled_at` datetime(3) NULL AFTER `version`,
  ADD COLUMN `published_at` datetime(3) NULL AFTER `scheduled_at`,
  ADD COLUMN `offline_at` datetime(3) NULL AFTER `published_at`,
  ADD COLUMN `updated_by` bigint unsigned NULL AFTER `offline_at`,
  ADD COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_at`,
  MODIFY COLUMN `image_url` varchar(500) NULL;

UPDATE `banner` b
JOIN `shop` s ON s.`id` = b.`shop_id`
SET b.`tenant_id` = s.`tenant_id`,
    b.`status` = CASE WHEN b.`status` = 'ACTIVE' THEN 'PUBLISHED' ELSE 'OFFLINE' END,
    b.`published_at` = CASE WHEN b.`status` = 'ACTIVE' THEN COALESCE(b.`start_at`, b.`created_at`) ELSE NULL END,
    b.`offline_at` = CASE WHEN b.`status` = 'ACTIVE' THEN NULL ELSE COALESCE(b.`end_at`, CURRENT_TIMESTAMP(3)) END;

ALTER TABLE `banner`
  MODIFY COLUMN `tenant_id` bigint unsigned NOT NULL,
  ADD KEY `idx_banner_content_public` (`tenant_id`, `shop_id`, `content_type`, `status`, `scheduled_at`, `sort_order`),
  ADD CONSTRAINT `fk_banner_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  ADD CONSTRAINT `fk_banner_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`),
  ADD CONSTRAINT `ck_banner_content_type` CHECK (`content_type` IN (
    'BANNER', 'FEATURED_SERVICE', 'FEATURED_PACKAGE', 'ACTIVITY',
    'POINTS_MALL', 'ANNOUNCEMENT', 'SHOP_INTRO', 'CONTACT'
  )),
  ADD CONSTRAINT `ck_banner_content_status` CHECK (`status` IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'OFFLINE'));

INSERT IGNORE INTO `role_definition`
  (`tenant_id`, `role_code`, `role_name`, `scope_type`, `is_system`, `status`)
SELECT t.`id`, visible_role.`role_code`, visible_role.`role_name`, visible_role.`scope_type`, 1, 'ACTIVE'
FROM `tenant` t
JOIN (
  SELECT 'SUPER_ADMIN' AS `role_code`, '超级管理员' AS `role_name`, 'TENANT' AS `scope_type`
  UNION ALL SELECT 'ADMIN', '普通管理员', 'SHOP'
) visible_role;

INSERT IGNORE INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('content:view', '查看首页内容', 'content', 'NORMAL'),
  ('content:manage', '维护首页内容', 'content', 'SENSITIVE'),
  ('content:publish', '发布和下线首页内容', 'content', 'SENSITIVE'),
  ('admin_account:view', '查看管理员账号', 'identity', 'SENSITIVE'),
  ('admin_account:manage', '创建停用和重置管理员账号', 'identity', 'CRITICAL'),
  ('system_config:view', '查看运营配置', 'settings', 'SENSITIVE'),
  ('system_config:manage', '管理生产级运营配置', 'settings', 'CRITICAL');

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT visible_role.`id`, permission.`id`
FROM `role_definition` visible_role
JOIN `permission_definition` permission
WHERE visible_role.`role_code` = 'SUPER_ADMIN';

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT visible_role.`id`, permission.`id`
FROM `role_definition` visible_role
JOIN `permission_definition` permission
WHERE visible_role.`role_code` = 'ADMIN'
  AND permission.`permission_code` IN (
    'dashboard:view', 'shop:view', 'staff:view', 'staff:manage',
    'service:view', 'service:manage',
    'content:view', 'content:manage', 'content:publish'
  );

INSERT IGNORE INTO `account_shop_role`
  (`tenant_id`, `account_id`, `region_id`, `shop_id`, `role_id`, `status`)
SELECT legacy_grant.`tenant_id`, legacy_grant.`account_id`, NULL, NULL, visible_role.`id`, 'ACTIVE'
FROM `account_shop_role` legacy_grant
JOIN `role_definition` legacy_role
  ON legacy_role.`id` = legacy_grant.`role_id`
 AND legacy_role.`tenant_id` = legacy_grant.`tenant_id`
JOIN `role_definition` visible_role
  ON visible_role.`tenant_id` = legacy_grant.`tenant_id`
 AND visible_role.`role_code` = 'SUPER_ADMIN'
WHERE legacy_role.`role_code` = 'OWNER'
  AND legacy_grant.`status` = 'ACTIVE';

INSERT IGNORE INTO `account_shop_role`
  (`tenant_id`, `account_id`, `region_id`, `shop_id`, `role_id`, `status`)
SELECT legacy_grant.`tenant_id`, legacy_grant.`account_id`, NULL,
       COALESCE(legacy_grant.`shop_id`, account.`home_shop_id`),
       visible_role.`id`, 'ACTIVE'
FROM `account_shop_role` legacy_grant
JOIN `account` account
  ON account.`id` = legacy_grant.`account_id`
 AND account.`tenant_id` = legacy_grant.`tenant_id`
JOIN `role_definition` legacy_role
  ON legacy_role.`id` = legacy_grant.`role_id`
 AND legacy_role.`tenant_id` = legacy_grant.`tenant_id`
JOIN `role_definition` visible_role
  ON visible_role.`tenant_id` = legacy_grant.`tenant_id`
 AND visible_role.`role_code` = 'ADMIN'
WHERE legacy_role.`role_code` IN ('MANAGER', 'REGIONAL_MANAGER', 'FRONT_DESK')
  AND legacy_grant.`status` = 'ACTIVE'
  AND COALESCE(legacy_grant.`shop_id`, account.`home_shop_id`) IS NOT NULL;
