-- SC3 extends the existing service, staff schedule and appointment facts.
-- Orders, payments, packages and benefit reservations remain outside this migration.

ALTER TABLE `service_item`
  ADD COLUMN `slot_interval_minutes` smallint unsigned NOT NULL DEFAULT 30 AFTER `cleanup_minutes`,
  ADD COLUMN `buffer_before_minutes` smallint unsigned NOT NULL DEFAULT 0 AFTER `slot_interval_minutes`,
  ADD COLUMN `buffer_after_minutes` smallint unsigned NOT NULL DEFAULT 0 AFTER `buffer_before_minutes`,
  ADD COLUMN `minimum_advance_minutes` int unsigned NOT NULL DEFAULT 30 AFTER `buffer_after_minutes`,
  ADD COLUMN `same_day_booking_allowed` tinyint(1) NOT NULL DEFAULT 1 AFTER `minimum_advance_minutes`,
  ADD COLUMN `free_cancel_minutes` int unsigned NOT NULL DEFAULT 1440 AFTER `same_day_booking_allowed`,
  ADD COLUMN `reschedule_cutoff_minutes` int unsigned NOT NULL DEFAULT 720 AFTER `free_cancel_minutes`,
  ADD COLUMN `max_reschedules` smallint unsigned NOT NULL DEFAULT 1 AFTER `reschedule_cutoff_minutes`,
  ADD COLUMN `late_cancel_policy` varchar(30) NOT NULL DEFAULT 'FULL_REFUND' AFTER `max_reschedules`,
  ADD COLUMN `late_cancel_value` decimal(12,2) NOT NULL DEFAULT 0 AFTER `late_cancel_policy`,
  ADD COLUMN `booking_terms_version` int unsigned NOT NULL DEFAULT 1 AFTER `late_cancel_value`,
  ADD COLUMN `booking_policy_updated_by` bigint unsigned NULL AFTER `booking_terms_version`,
  ADD CONSTRAINT `fk_service_booking_policy_updater`
    FOREIGN KEY (`booking_policy_updated_by`) REFERENCES `account` (`id`),
  ADD CONSTRAINT `ck_service_slot_interval`
    CHECK (`slot_interval_minutes` IN (15, 20, 30, 60)),
  ADD CONSTRAINT `ck_service_booking_buffers`
    CHECK (`buffer_before_minutes` <= 240 AND `buffer_after_minutes` <= 240),
  ADD CONSTRAINT `ck_service_booking_advance`
    CHECK (`minimum_advance_minutes` <= 43200),
  ADD CONSTRAINT `ck_service_reschedule_policy`
    CHECK (`free_cancel_minutes` <= 43200 AND `reschedule_cutoff_minutes` <= 43200 AND `max_reschedules` <= 20),
  ADD CONSTRAINT `ck_service_late_cancel_policy`
    CHECK (`late_cancel_policy` IN ('FULL_REFUND', 'FIXED_FEE', 'PERCENTAGE_FEE', 'DEDUCT_CARD_TIMES', 'NON_REFUNDABLE')),
  ADD CONSTRAINT `ck_service_late_cancel_value`
    CHECK (`late_cancel_value` >= 0);

UPDATE `service_item`
SET `buffer_after_minutes` = `cleanup_minutes`
WHERE `cleanup_minutes` > 0;

ALTER TABLE `staff_schedule`
  DROP CHECK `ck_schedule_type`,
  DROP CHECK `ck_schedule_time`,
  ADD CONSTRAINT `ck_schedule_type`
    CHECK (`schedule_type` IN ('WORK', 'BREAK', 'LEAVE', 'BLOCKED', 'STOP_BOOKING')),
  ADD CONSTRAINT `ck_schedule_time` CHECK (
    (`schedule_type` IN ('LEAVE', 'STOP_BOOKING')
      AND (`start_time` IS NULL OR `end_time` IS NULL OR `end_time` > `start_time`))
    OR (`schedule_type` NOT IN ('LEAVE', 'STOP_BOOKING')
      AND `start_time` IS NOT NULL AND `end_time` IS NOT NULL AND `end_time` > `start_time`)
  );

CREATE TABLE `staff_schedule_rule` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `staff_id` bigint unsigned NOT NULL,
  `day_of_week` tinyint unsigned NOT NULL COMMENT 'ISO-8601 Monday=1 Sunday=7',
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  `rule_type` varchar(20) NOT NULL DEFAULT 'WORK',
  `effective_from` date NOT NULL,
  `effective_to` date NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `version` int unsigned NOT NULL DEFAULT 1,
  `updated_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_schedule_rule_slot`
    (`tenant_id`, `shop_id`, `staff_id`, `day_of_week`, `start_time`, `end_time`, `effective_from`),
  KEY `idx_staff_schedule_rule_lookup`
    (`tenant_id`, `shop_id`, `staff_id`, `day_of_week`, `status`, `effective_from`, `effective_to`),
  CONSTRAINT `fk_schedule_rule_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_schedule_rule_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_schedule_rule_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_schedule_rule_updater` FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_schedule_rule_day` CHECK (`day_of_week` BETWEEN 1 AND 7),
  CONSTRAINT `ck_schedule_rule_time` CHECK (`end_time` > `start_time`),
  CONSTRAINT `ck_schedule_rule_type` CHECK (`rule_type` IN ('WORK', 'BREAK', 'STOP_BOOKING')),
  CONSTRAINT `ck_schedule_rule_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT `ck_schedule_rule_dates` CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC3 技师周固定班次和固定休息规则';

CREATE TABLE `booking_time_lock` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `lock_token` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `staff_id` bigint unsigned NOT NULL,
  `service_id` bigint unsigned NOT NULL,
  `appointment_id` bigint unsigned NULL,
  `waitlist_id` bigint unsigned NULL,
  `start_at` datetime(3) NOT NULL,
  `end_at` datetime(3) NOT NULL,
  `occupied_start_at` datetime(3) NOT NULL,
  `occupied_end_at` datetime(3) NOT NULL,
  `source` varchar(30) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'HELD',
  `expires_at` datetime(3) NOT NULL,
  `released_at` datetime(3) NULL,
  `release_reason` varchar(100) NULL,
  `terms_version` int unsigned NOT NULL,
  `rule_snapshot_json` json NOT NULL,
  `terms_confirmed_at` datetime(3) NULL,
  `created_by` bigint unsigned NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_booking_time_lock_token` (`tenant_id`, `lock_token`),
  UNIQUE KEY `uk_booking_time_lock_appointment` (`appointment_id`),
  KEY `idx_booking_time_lock_staff_range`
    (`tenant_id`, `staff_id`, `status`, `expires_at`, `occupied_start_at`, `occupied_end_at`),
  KEY `idx_booking_time_lock_member` (`tenant_id`, `member_id`, `status`, `created_at`),
  CONSTRAINT `fk_booking_lock_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_booking_lock_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_booking_lock_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_booking_lock_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_booking_lock_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `fk_booking_lock_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `fk_booking_lock_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_booking_lock_time`
    CHECK (`end_at` > `start_at` AND `occupied_start_at` <= `start_at` AND `occupied_end_at` >= `end_at`),
  CONSTRAINT `ck_booking_lock_source`
    CHECK (`source` IN ('MEMBER', 'ADMIN', 'WAITLIST', 'PAYMENT')),
  CONSTRAINT `ck_booking_lock_status`
    CHECK (`status` IN ('HELD', 'CONVERTED', 'RELEASED', 'EXPIRED')),
  CONSTRAINT `ck_booking_lock_release` CHECK (
    (`status` IN ('HELD', 'CONVERTED') AND `released_at` IS NULL)
    OR (`status` IN ('RELEASED', 'EXPIRED') AND `released_at` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC3 完整占用区间时间锁；支付模块后续只引用此事实';

ALTER TABLE `appointment`
  ADD COLUMN `occupied_start_at` datetime(3) NULL AFTER `end_at`,
  ADD COLUMN `occupied_end_at` datetime(3) NULL AFTER `occupied_start_at`,
  ADD COLUMN `terms_version` int unsigned NOT NULL DEFAULT 1 AFTER `internal_note`,
  ADD COLUMN `rule_snapshot_json` json NULL AFTER `terms_version`,
  ADD COLUMN `terms_confirmed_at` datetime(3) NULL AFTER `rule_snapshot_json`,
  ADD COLUMN `free_cancel_deadline` datetime(3) NULL AFTER `terms_confirmed_at`,
  ADD COLUMN `reschedule_deadline` datetime(3) NULL AFTER `free_cancel_deadline`,
  ADD COLUMN `reschedule_count` smallint unsigned NOT NULL DEFAULT 0 AFTER `reschedule_deadline`,
  ADD COLUMN `booking_time_lock_id` bigint unsigned NULL AFTER `reschedule_count`;

UPDATE `appointment`
SET `occupied_start_at` = `start_at`,
    `occupied_end_at` = `end_at`
WHERE `occupied_start_at` IS NULL OR `occupied_end_at` IS NULL;

ALTER TABLE `appointment`
  MODIFY COLUMN `occupied_start_at` datetime(3) NOT NULL,
  MODIFY COLUMN `occupied_end_at` datetime(3) NOT NULL,
  ADD UNIQUE KEY `uk_appointment_booking_lock` (`booking_time_lock_id`),
  ADD KEY `idx_appointment_staff_occupied`
    (`tenant_id`, `staff_id`, `status`, `occupied_start_at`, `occupied_end_at`),
  ADD CONSTRAINT `fk_appointment_booking_lock`
    FOREIGN KEY (`booking_time_lock_id`) REFERENCES `booking_time_lock` (`id`),
  ADD CONSTRAINT `ck_appointment_occupied_time`
    CHECK (`occupied_start_at` <= `start_at` AND `occupied_end_at` >= `end_at`),
  ADD CONSTRAINT `ck_appointment_reschedule_count` CHECK (`reschedule_count` <= 20);

CREATE TABLE `booking_waitlist` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `service_id` bigint unsigned NOT NULL,
  `requested_staff_id` bigint unsigned NULL,
  `matched_staff_id` bigint unsigned NULL,
  `date_from` date NOT NULL,
  `date_to` date NOT NULL,
  `time_from` time NOT NULL,
  `time_to` time NOT NULL,
  `flexibility_minutes` smallint unsigned NOT NULL DEFAULT 0,
  `accept_other_staff` tinyint(1) NOT NULL DEFAULT 0,
  `latest_notify_at` datetime(3) NOT NULL,
  `time_lock_id` bigint unsigned NULL,
  `status` varchar(30) NOT NULL DEFAULT 'WAITING',
  `matched_at` datetime(3) NULL,
  `confirmation_expires_at` datetime(3) NULL,
  `confirmed_at` datetime(3) NULL,
  `cancelled_at` datetime(3) NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_booking_waitlist_lock` (`time_lock_id`),
  KEY `idx_booking_waitlist_match`
    (`tenant_id`, `shop_id`, `status`, `date_from`, `date_to`, `created_at`),
  KEY `idx_booking_waitlist_member` (`tenant_id`, `member_id`, `status`, `created_at`),
  CONSTRAINT `fk_booking_waitlist_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_booking_waitlist_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_booking_waitlist_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_booking_waitlist_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `fk_booking_waitlist_requested_staff` FOREIGN KEY (`requested_staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_booking_waitlist_matched_staff` FOREIGN KEY (`matched_staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_booking_waitlist_lock` FOREIGN KEY (`time_lock_id`) REFERENCES `booking_time_lock` (`id`),
  CONSTRAINT `fk_booking_waitlist_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_booking_waitlist_dates` CHECK (`date_to` >= `date_from`),
  CONSTRAINT `ck_booking_waitlist_time` CHECK (`time_to` > `time_from`),
  CONSTRAINT `ck_booking_waitlist_flexibility` CHECK (`flexibility_minutes` <= 240),
  CONSTRAINT `ck_booking_waitlist_status` CHECK (`status` IN (
    'WAITING', 'MATCHED', 'WAITING_CONFIRMATION', 'CONFIRMED',
    'EXPIRED', 'CANCELLED', 'INVALID'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC3 会员候补意向，不自动生成预约';

ALTER TABLE `booking_time_lock`
  ADD CONSTRAINT `fk_booking_lock_waitlist`
    FOREIGN KEY (`waitlist_id`) REFERENCES `booking_waitlist` (`id`);

CREATE TABLE `booking_waitlist_status_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `waitlist_id` bigint unsigned NOT NULL,
  `from_status` varchar(30) NULL,
  `to_status` varchar(30) NOT NULL,
  `reason` varchar(500) NULL,
  `changed_by` bigint unsigned NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_waitlist_history` (`tenant_id`, `waitlist_id`, `created_at`),
  CONSTRAINT `fk_waitlist_history_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_waitlist_history_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_waitlist_history_waitlist` FOREIGN KEY (`waitlist_id`) REFERENCES `booking_waitlist` (`id`),
  CONSTRAINT `fk_waitlist_history_actor` FOREIGN KEY (`changed_by`) REFERENCES `account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC3 候补追加状态历史';

CREATE TABLE `booking_assignment_cursor` (
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `service_id` bigint unsigned NOT NULL,
  `staff_id` bigint unsigned NOT NULL,
  `assignment_count` bigint unsigned NOT NULL DEFAULT 0,
  `last_assigned_at` datetime(3) NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  PRIMARY KEY (`tenant_id`, `shop_id`, `service_id`, `staff_id`),
  KEY `idx_booking_assignment_order`
    (`tenant_id`, `shop_id`, `service_id`, `assignment_count`, `last_assigned_at`),
  CONSTRAINT `fk_booking_cursor_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_booking_cursor_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_booking_cursor_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `fk_booking_cursor_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC3 同负载技师轮询游标';

INSERT IGNORE INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('schedule:view', '查看技师排班', 'booking', 'NORMAL'),
  ('schedule:manage', '维护技师排班', 'booking', 'SENSITIVE'),
  ('waitlist:view', '查看候补名单', 'booking', 'SENSITIVE'),
  ('waitlist:manage', '处理候补名单', 'booking', 'SENSITIVE');

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT role.`id`, permission.`id`
FROM `role_definition` role
JOIN `permission_definition` permission
WHERE role.`role_code` IN ('ADMIN', 'SUPER_ADMIN')
  AND permission.`permission_code` IN (
    'appointment:view', 'appointment:manage',
    'schedule:view', 'schedule:manage',
    'waitlist:view', 'waitlist:manage'
  );
