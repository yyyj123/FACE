-- 美容院店铺系统 MySQL 8.0 数据库
-- 目标：单门店 MVP，保留未来扩展多门店的结构边界

CREATE DATABASE IF NOT EXISTS `face_salon`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `face_salon`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP VIEW IF EXISTS `v_low_stock_products`;
DROP VIEW IF EXISTS `v_today_appointment_summary`;

DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `consultation`;
DROP TABLE IF EXISTS `banner`;
DROP TABLE IF EXISTS `care_record`;
DROP TABLE IF EXISTS `review`;
DROP TABLE IF EXISTS `inventory_movement`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `product_category`;
DROP TABLE IF EXISTS `sales_order_item`;
DROP TABLE IF EXISTS `sales_order`;
DROP TABLE IF EXISTS `service_record`;
DROP TABLE IF EXISTS `appointment_item`;
DROP TABLE IF EXISTS `appointment`;
DROP TABLE IF EXISTS `staff_schedule`;
DROP TABLE IF EXISTS `staff_service`;
DROP TABLE IF EXISTS `service_item`;
DROP TABLE IF EXISTS `service_category`;
DROP TABLE IF EXISTS `token`;
DROP TABLE IF EXISTS `account`;
DROP TABLE IF EXISTS `staff`;
DROP TABLE IF EXISTS `member`;
DROP TABLE IF EXISTS `shop`;

CREATE TABLE `shop` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `phone` VARCHAR(30) NULL,
  `address` VARCHAR(255) NULL,
  `business_hours` JSON NULL COMMENT '按星期保存营业时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_shop_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB COMMENT='门店';

CREATE TABLE `member` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `member_no` VARCHAR(32) NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `phone` VARCHAR(30) NOT NULL,
  `gender` VARCHAR(20) NULL,
  `birthday` DATE NULL,
  `avatar_url` VARCHAR(500) NULL,
  `source` VARCHAR(40) NULL COMMENT '到店、转介绍、线上等',
  `points` INT NOT NULL DEFAULT 0,
  `notes` VARCHAR(1000) NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_shop_no` (`shop_id`, `member_no`),
  UNIQUE KEY `uk_member_shop_phone` (`shop_id`, `phone`),
  KEY `idx_member_name` (`shop_id`, `name`),
  CONSTRAINT `fk_member_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_member_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB COMMENT='会员/顾客';

CREATE TABLE `staff` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `staff_no` VARCHAR(32) NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `phone` VARCHAR(30) NULL,
  `job_role` VARCHAR(30) NOT NULL COMMENT '店长、前台、美容师',
  `level_name` VARCHAR(50) NULL,
  `avatar_url` VARCHAR(500) NULL,
  `bio` VARCHAR(1000) NULL,
  `hire_date` DATE NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_shop_no` (`shop_id`, `staff_no`),
  KEY `idx_staff_role_status` (`shop_id`, `job_role`, `status`),
  CONSTRAINT `fk_staff_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_staff_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB COMMENT='门店员工';

CREATE TABLE `account` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `username` VARCHAR(80) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `role_code` VARCHAR(30) NOT NULL COMMENT 'OWNER, MANAGER, FRONT_DESK, BEAUTICIAN, MEMBER',
  `staff_id` BIGINT UNSIGNED NULL,
  `member_id` BIGINT UNSIGNED NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_username` (`username`),
  UNIQUE KEY `uk_account_staff` (`staff_id`),
  UNIQUE KEY `uk_account_member` (`member_id`),
  CONSTRAINT `fk_account_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_account_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_account_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `ck_account_role` CHECK (`role_code` IN ('OWNER', 'MANAGER', 'FRONT_DESK', 'BEAUTICIAN', 'MEMBER')),
  CONSTRAINT `ck_account_status` CHECK (`status` IN ('ACTIVE', 'LOCKED', 'INACTIVE')),
  CONSTRAINT `ck_account_subject` CHECK (
    (`role_code` = 'MEMBER' AND `member_id` IS NOT NULL AND `staff_id` IS NULL)
    OR (`role_code` <> 'MEMBER' AND `staff_id` IS NOT NULL AND `member_id` IS NULL)
  )
) ENGINE=InnoDB COMMENT='登录账号';

CREATE TABLE `token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `userid` BIGINT UNSIGNED NOT NULL,
  `username` VARCHAR(80) NOT NULL,
  `tablename` VARCHAR(40) NOT NULL DEFAULT 'account',
  `role` VARCHAR(30) NOT NULL,
  `token` VARCHAR(64) NOT NULL,
  `expiratedtime` DATETIME NOT NULL,
  `addtime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_subject_role` (`userid`, `role`),
  UNIQUE KEY `uk_token_value` (`token`),
  KEY `idx_token_expiration` (`expiratedtime`),
  CONSTRAINT `fk_token_account` FOREIGN KEY (`userid`) REFERENCES `account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='登录会话令牌';

CREATE TABLE `service_category` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(80) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_category_name` (`shop_id`, `name`),
  CONSTRAINT `fk_service_category_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`)
) ENGINE=InnoDB COMMENT='美容项目分类';

CREATE TABLE `service_item` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `category_id` BIGINT UNSIGNED NOT NULL,
  `service_code` VARCHAR(32) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `subtitle` VARCHAR(255) NULL,
  `cover_url` VARCHAR(500) NULL,
  `description` TEXT NULL,
  `duration_minutes` SMALLINT UNSIGNED NOT NULL,
  `cleanup_minutes` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '项目后预留清洁时间',
  `list_price` DECIMAL(10,2) NOT NULL,
  `member_price` DECIMAL(10,2) NULL,
  `applicable_skin_types` JSON NULL,
  `contraindications` TEXT NULL,
  `booking_notice` TEXT NULL,
  `is_featured` TINYINT(1) NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_item_code` (`shop_id`, `service_code`),
  KEY `idx_service_category_status` (`category_id`, `status`),
  CONSTRAINT `fk_service_item_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_service_item_category` FOREIGN KEY (`category_id`) REFERENCES `service_category` (`id`),
  CONSTRAINT `ck_service_duration` CHECK (`duration_minutes` > 0),
  CONSTRAINT `ck_service_price` CHECK (`list_price` >= 0 AND (`member_price` IS NULL OR `member_price` >= 0)),
  CONSTRAINT `ck_service_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB COMMENT='美容项目';

CREATE TABLE `care_package` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL DEFAULT 1,
  `package_name` VARCHAR(120) NOT NULL,
  `package_type` VARCHAR(80) NULL,
  `brand_name` VARCHAR(120) NULL,
  `duration_text` VARCHAR(80) NULL,
  `recommended_interval` VARCHAR(80) NULL,
  `validity_text` VARCHAR(80) NULL,
  `applicable_skin_types` VARCHAR(255) NULL,
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0,
  `service_count` INT UNSIGNED NULL,
  `included_services` TEXT NULL,
  `booking_method` VARCHAR(255) NULL,
  `cover_url` VARCHAR(500) NULL,
  `highlights` TEXT NULL,
  `usage_instructions` TEXT NULL,
  `precautions` TEXT NULL,
  `description` MEDIUMTEXT NULL,
  `click_count` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_care_package_shop_status` (`shop_id`, `status`),
  CONSTRAINT `fk_care_package_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='护理套餐';

CREATE TABLE `staff_service` (
  `staff_id` BIGINT UNSIGNED NOT NULL,
  `service_id` BIGINT UNSIGNED NOT NULL,
  `custom_duration_minutes` SMALLINT UNSIGNED NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`staff_id`, `service_id`),
  CONSTRAINT `fk_staff_service_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_staff_service_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`)
) ENGINE=InnoDB COMMENT='美容师可服务项目';

CREATE TABLE `staff_schedule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `staff_id` BIGINT UNSIGNED NOT NULL,
  `schedule_date` DATE NOT NULL,
  `start_time` TIME NULL,
  `end_time` TIME NULL,
  `schedule_type` VARCHAR(20) NOT NULL DEFAULT 'WORK',
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_schedule_slot` (`staff_id`, `schedule_date`, `start_time`, `end_time`, `schedule_type`),
  KEY `idx_schedule_shop_date` (`shop_id`, `schedule_date`),
  CONSTRAINT `fk_schedule_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_schedule_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `ck_schedule_type` CHECK (`schedule_type` IN ('WORK', 'LEAVE', 'BLOCKED')),
  CONSTRAINT `ck_schedule_time` CHECK (
    (`schedule_type` = 'LEAVE' AND (`start_time` IS NULL OR `end_time` IS NULL OR `end_time` > `start_time`))
    OR (`schedule_type` <> 'LEAVE' AND `start_time` IS NOT NULL AND `end_time` IS NOT NULL AND `end_time` > `start_time`)
  )
) ENGINE=InnoDB COMMENT='员工排班与请假';

CREATE TABLE `appointment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `appointment_no` VARCHAR(40) NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `staff_id` BIGINT UNSIGNED NOT NULL,
  `start_at` DATETIME(3) NOT NULL,
  `end_at` DATETIME(3) NOT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  `source` VARCHAR(30) NOT NULL DEFAULT 'ONLINE',
  `member_note` VARCHAR(1000) NULL,
  `internal_note` VARCHAR(1000) NULL,
  `cancel_reason` VARCHAR(500) NULL,
  `confirmed_by` BIGINT UNSIGNED NULL,
  `confirmed_at` DATETIME(3) NULL,
  `checked_in_at` DATETIME(3) NULL,
  `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment_no` (`appointment_no`),
  KEY `idx_appointment_staff_time` (`staff_id`, `start_at`, `end_at`, `status`),
  KEY `idx_appointment_member_time` (`member_id`, `start_at`),
  KEY `idx_appointment_shop_status_time` (`shop_id`, `status`, `start_at`),
  CONSTRAINT `fk_appointment_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_appointment_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_appointment_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `fk_appointment_confirmer` FOREIGN KEY (`confirmed_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_appointment_time` CHECK (`end_at` > `start_at`),
  CONSTRAINT `ck_appointment_status` CHECK (`status` IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_SERVICE', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
  CONSTRAINT `ck_appointment_source` CHECK (`source` IN ('ONLINE', 'FRONT_DESK', 'PHONE', 'WECHAT'))
) ENGINE=InnoDB COMMENT='预约单；时间重叠必须在服务端事务中校验';

CREATE TABLE `appointment_item` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `appointment_id` BIGINT UNSIGNED NOT NULL,
  `service_id` BIGINT UNSIGNED NOT NULL,
  `service_name_snapshot` VARCHAR(120) NOT NULL,
  `duration_minutes_snapshot` SMALLINT UNSIGNED NOT NULL,
  `price_snapshot` DECIMAL(10,2) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment_service` (`appointment_id`, `service_id`),
  CONSTRAINT `fk_appointment_item_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_appointment_item_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `ck_appointment_item_values` CHECK (`duration_minutes_snapshot` > 0 AND `price_snapshot` >= 0)
) ENGINE=InnoDB COMMENT='预约项目快照';

CREATE TABLE `service_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `appointment_id` BIGINT UNSIGNED NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `staff_id` BIGINT UNSIGNED NOT NULL,
  `actual_start_at` DATETIME(3) NOT NULL,
  `actual_end_at` DATETIME(3) NULL,
  `service_summary` VARCHAR(1000) NULL,
  `next_visit_recommendation` VARCHAR(1000) NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_record_appointment` (`appointment_id`),
  KEY `idx_service_record_member` (`member_id`, `actual_start_at`),
  CONSTRAINT `fk_service_record_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_service_record_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `fk_service_record_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_service_record_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `ck_service_record_status` CHECK (`status` IN ('IN_PROGRESS', 'COMPLETED', 'VOID')),
  CONSTRAINT `ck_service_record_time` CHECK (`actual_end_at` IS NULL OR `actual_end_at` > `actual_start_at`)
) ENGINE=InnoDB COMMENT='实际到店服务记录';

CREATE TABLE `sales_order` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `order_no` VARCHAR(40) NOT NULL,
  `member_id` BIGINT UNSIGNED NULL,
  `appointment_id` BIGINT UNSIGNED NULL,
  `service_record_id` BIGINT UNSIGNED NULL,
  `subtotal_amount` DECIMAL(12,2) NOT NULL DEFAULT 0,
  `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0,
  `payable_amount` DECIMAL(12,2) NOT NULL DEFAULT 0,
  `paid_amount` DECIMAL(12,2) NOT NULL DEFAULT 0,
  `payment_method` VARCHAR(30) NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
  `paid_at` DATETIME(3) NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_order_no` (`order_no`),
  KEY `idx_sales_order_shop_paid_at` (`shop_id`, `paid_at`),
  KEY `idx_sales_order_member` (`member_id`, `created_at`),
  CONSTRAINT `fk_sales_order_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_sales_order_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_sales_order_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `fk_sales_order_service_record` FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_sales_order_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_sales_order_amount` CHECK (`subtotal_amount` >= 0 AND `discount_amount` >= 0 AND `payable_amount` >= 0 AND `paid_amount` >= 0),
  CONSTRAINT `ck_sales_order_status` CHECK (`status` IN ('UNPAID', 'PAID', 'REFUNDED', 'VOID'))
) ENGINE=InnoDB COMMENT='消费订单';

CREATE TABLE `product_category` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(80) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_category_name` (`shop_id`, `name`),
  CONSTRAINT `fk_product_category_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`)
) ENGINE=InnoDB COMMENT='产品/耗材分类';

CREATE TABLE `product` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `category_id` BIGINT UNSIGNED NOT NULL,
  `sku` VARCHAR(50) NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `brand_name` VARCHAR(100) NULL,
  `unit_name` VARCHAR(20) NOT NULL,
  `cost_price` DECIMAL(10,2) NOT NULL DEFAULT 0,
  `sale_price` DECIMAL(10,2) NOT NULL DEFAULT 0,
  `stock_quantity` DECIMAL(12,3) NOT NULL DEFAULT 0,
  `warning_quantity` DECIMAL(12,3) NOT NULL DEFAULT 0,
  `is_consumable` TINYINT(1) NOT NULL DEFAULT 1,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_sku` (`shop_id`, `sku`),
  KEY `idx_product_stock_warning` (`shop_id`, `status`, `stock_quantity`, `warning_quantity`),
  CONSTRAINT `fk_product_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`),
  CONSTRAINT `ck_product_amount` CHECK (`cost_price` >= 0 AND `sale_price` >= 0 AND `stock_quantity` >= 0 AND `warning_quantity` >= 0)
) ENGINE=InnoDB COMMENT='零售产品与美容耗材';

CREATE TABLE `sales_order_item` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `item_type` VARCHAR(20) NOT NULL,
  `service_id` BIGINT UNSIGNED NULL,
  `product_id` BIGINT UNSIGNED NULL,
  `item_name_snapshot` VARCHAR(120) NOT NULL,
  `quantity` DECIMAL(12,3) NOT NULL DEFAULT 1,
  `unit_price` DECIMAL(10,2) NOT NULL,
  `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0,
  `line_amount` DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_item_order` (`order_id`),
  CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_order_item_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `ck_order_item_type` CHECK (
    (`item_type` = 'SERVICE' AND `service_id` IS NOT NULL AND `product_id` IS NULL)
    OR (`item_type` = 'PRODUCT' AND `product_id` IS NOT NULL AND `service_id` IS NULL)
  ),
  CONSTRAINT `ck_order_item_amount` CHECK (`quantity` > 0 AND `unit_price` >= 0 AND `discount_amount` >= 0 AND `line_amount` >= 0)
) ENGINE=InnoDB COMMENT='订单明细快照';

CREATE TABLE `inventory_movement` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `product_id` BIGINT UNSIGNED NOT NULL,
  `movement_type` VARCHAR(30) NOT NULL,
  `quantity_delta` DECIMAL(12,3) NOT NULL COMMENT '入库为正，出库为负',
  `balance_after` DECIMAL(12,3) NOT NULL,
  `order_id` BIGINT UNSIGNED NULL,
  `service_record_id` BIGINT UNSIGNED NULL,
  `reference_no` VARCHAR(80) NULL,
  `remark` VARCHAR(500) NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_inventory_product_time` (`product_id`, `created_at`),
  CONSTRAINT `fk_inventory_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_inventory_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_inventory_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_inventory_service_record` FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_inventory_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_inventory_type` CHECK (`movement_type` IN ('PURCHASE_IN', 'SALE_OUT', 'SERVICE_USE', 'RETURN_IN', 'ADJUSTMENT')),
  CONSTRAINT `ck_inventory_balance` CHECK (`quantity_delta` <> 0 AND `balance_after` >= 0)
) ENGINE=InnoDB COMMENT='库存流水，库存变动的审计依据';

CREATE TABLE `review` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `appointment_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `staff_id` BIGINT UNSIGNED NOT NULL,
  `service_score` TINYINT UNSIGNED NOT NULL,
  `staff_score` TINYINT UNSIGNED NOT NULL,
  `content` VARCHAR(2000) NULL,
  `reply_content` VARCHAR(2000) NULL,
  `replied_at` DATETIME(3) NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_appointment` (`appointment_id`),
  KEY `idx_review_staff` (`staff_id`, `created_at`),
  CONSTRAINT `fk_review_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_review_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `fk_review_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_review_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `ck_review_score` CHECK (`service_score` BETWEEN 1 AND 5 AND `staff_score` BETWEEN 1 AND 5),
  CONSTRAINT `ck_review_status` CHECK (`status` IN ('VISIBLE', 'HIDDEN'))
) ENGINE=InnoDB COMMENT='服务评价';

CREATE TABLE `care_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `service_record_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NOT NULL,
  `skin_type` VARCHAR(50) NULL,
  `concerns` JSON NULL,
  `observations` TEXT NULL,
  `products_used` JSON NULL,
  `home_care_advice` TEXT NULL,
  `next_recommended_at` DATE NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_care_record_service` (`service_record_id`),
  KEY `idx_care_record_member` (`member_id`, `created_at`),
  CONSTRAINT `fk_care_record_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_care_record_service` FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_care_record_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_care_record_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`)
) ENGINE=InnoDB COMMENT='敏感护理档案，仅授权员工和会员本人可见';

CREATE TABLE `banner` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `title` VARCHAR(120) NOT NULL,
  `image_url` VARCHAR(500) NOT NULL,
  `target_type` VARCHAR(30) NOT NULL DEFAULT 'NONE',
  `target_value` VARCHAR(255) NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `start_at` DATETIME(3) NULL,
  `end_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_banner_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_banner_target` CHECK (`target_type` IN ('NONE', 'SERVICE', 'URL')),
  CONSTRAINT `ck_banner_period` CHECK (`end_at` IS NULL OR `start_at` IS NULL OR `end_at` > `start_at`)
) ENGINE=InnoDB COMMENT='首页轮播图';

CREATE TABLE `consultation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `member_id` BIGINT UNSIGNED NULL,
  `assigned_staff_id` BIGINT UNSIGNED NULL,
  `channel` VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  `last_message_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_consultation_status` (`shop_id`, `status`, `last_message_at`),
  CONSTRAINT `fk_consultation_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_consultation_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_consultation_staff` FOREIGN KEY (`assigned_staff_id`) REFERENCES `staff` (`id`),
  CONSTRAINT `ck_consultation_status` CHECK (`status` IN ('OPEN', 'ASSIGNED', 'CLOSED'))
) ENGINE=InnoDB COMMENT='在线咨询会话';

CREATE TABLE `audit_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `account_id` BIGINT UNSIGNED NULL,
  `action` VARCHAR(80) NOT NULL,
  `entity_type` VARCHAR(80) NOT NULL,
  `entity_id` BIGINT UNSIGNED NULL,
  `before_data` JSON NULL,
  `after_data` JSON NULL,
  `ip_address` VARCHAR(64) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_entity` (`entity_type`, `entity_id`, `created_at`),
  KEY `idx_audit_account` (`account_id`, `created_at`),
  CONSTRAINT `fk_audit_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_audit_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`)
) ENGINE=InnoDB COMMENT='关键操作审计日志';

CREATE TABLE `chat` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `addtime` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `userid` BIGINT UNSIGNED NOT NULL COMMENT 'Member account id',
  `adminid` BIGINT UNSIGNED NULL COMMENT 'Staff account id',
  `ask` LONGTEXT NULL,
  `reply` LONGTEXT NULL,
  `isreply` TINYINT NOT NULL DEFAULT 1 COMMENT '1 awaiting reply, 0 historical',
  `isread` TINYINT NOT NULL DEFAULT 0,
  `uname` VARCHAR(200) NULL,
  `uimage` TEXT NULL,
  `type` TINYINT NOT NULL DEFAULT 1 COMMENT '1 text, 2 image, 3 video, 4 file',
  PRIMARY KEY (`id`),
  KEY `idx_chat_user_time` (`userid`, `addtime`),
  KEY `idx_chat_reply_state` (`isreply`, `addtime`),
  CONSTRAINT `fk_chat_user_account` FOREIGN KEY (`userid`) REFERENCES `account` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chat_admin_account` FOREIGN KEY (`adminid`) REFERENCES `account` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Online customer-service conversation';

CREATE VIEW `v_today_appointment_summary` AS
SELECT
  `shop_id`,
  COUNT(*) AS `appointment_count`,
  SUM(`status` = 'PENDING') AS `pending_count`,
  SUM(`status` = 'CONFIRMED') AS `confirmed_count`,
  SUM(`status` = 'CHECKED_IN') AS `checked_in_count`,
  SUM(`status` = 'COMPLETED') AS `completed_count`
FROM `appointment`
WHERE `start_at` >= CURRENT_DATE()
  AND `start_at` < CURRENT_DATE() + INTERVAL 1 DAY
GROUP BY `shop_id`;

CREATE VIEW `v_low_stock_products` AS
SELECT `id`, `shop_id`, `sku`, `name`, `unit_name`, `stock_quantity`, `warning_quantity`
FROM `product`
WHERE `status` = 'ACTIVE' AND `stock_quantity` <= `warning_quantity`;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO `shop` (`id`, `name`, `phone`, `address`, `business_hours`)
VALUES (
  1,
  'FACE 美容护理中心',
  '400-000-0000',
  '请在管理端配置门店地址',
  JSON_OBJECT(
    'monday', JSON_ARRAY('09:30', '21:00'),
    'tuesday', JSON_ARRAY('09:30', '21:00'),
    'wednesday', JSON_ARRAY('09:30', '21:00'),
    'thursday', JSON_ARRAY('09:30', '21:00'),
    'friday', JSON_ARRAY('09:30', '21:00'),
    'saturday', JSON_ARRAY('09:30', '21:00'),
    'sunday', JSON_ARRAY('09:30', '21:00')
  )
);

INSERT INTO `service_category` (`shop_id`, `name`, `sort_order`) VALUES
  (1, '面部护理', 10),
  (1, '身体护理', 20),
  (1, '美甲美睫', 30),
  (1, '护理套餐', 40);

INSERT INTO `staff` (`id`, `shop_id`, `staff_no`, `name`, `phone`, `job_role`, `level_name`, `bio`) VALUES
  (1, 1, 'S0001', '店长', '13800000001', '店长', '资深店长', '负责门店运营与顾客体验'),
  (2, 1, 'S0002', '安然', '13800000002', '美容师', '高级美容师', '擅长面部补水、舒缓修护与敏感肌护理');

INSERT INTO `member` (`id`, `shop_id`, `member_no`, `name`, `phone`, `gender`, `source`) VALUES
  (1, 1, 'M0001', '体验会员', '13900000001', '女', 'ONLINE');

-- 开发环境初始管理员：admin / Face@123。首次登录后请立即修改密码。
INSERT INTO `member` (`id`, `shop_id`, `member_no`, `name`, `phone`, `gender`, `source`) VALUES
  (2, 1, 'M0002', '会员1', '13900000002', '女', 'SYSTEM');

INSERT INTO `account` (`id`, `shop_id`, `username`, `password_hash`, `role_code`, `staff_id`) VALUES
  (1, 1, 'admin', '$2a$10$vbO0KhzeXY9l3spTnx4jjeUC/OfHqn/FUlBtCA1YsqmTE9QxGExIq', 'OWNER', 1);

INSERT INTO `account` (`id`, `shop_id`, `username`, `password_hash`, `role_code`, `member_id`) VALUES
  (2, 1, '会员1', '$2a$10$06i.MHcGHR.AyQjmjgLZQeDlB.gozCavc9y39krd71SquRCtXllES', 'MEMBER', 2);

INSERT INTO `service_item`
  (`id`, `shop_id`, `category_id`, `service_code`, `name`, `subtitle`, `description`, `duration_minutes`, `cleanup_minutes`, `list_price`, `member_price`, `is_featured`)
VALUES
  (1, 1, 1, 'FACE-HYDRATE-60', '深层补水护理', '清洁、补水、锁水一站式护理', '适合干燥缺水、换季紧绷肌肤。', 60, 15, 298.00, 258.00, 1),
  (2, 1, 1, 'FACE-SOOTHE-75', '舒缓修护护理', '温和护理敏感与脆弱肌肤', '服务前由美容师完成基础皮肤咨询。', 75, 15, 398.00, 358.00, 1),
  (3, 1, 2, 'BODY-RELAX-90', '全身舒压护理', '释放疲劳，恢复身体轻盈感', '请提前告知孕期、过敏或其他禁忌情况。', 90, 15, 498.00, 438.00, 1);

INSERT INTO `staff_service` (`staff_id`, `service_id`) VALUES
  (2, 1), (2, 2), (2, 3);

INSERT INTO `staff_schedule` (`shop_id`, `staff_id`, `schedule_date`, `start_time`, `end_time`, `schedule_type`, `remark`)
SELECT 1, 2, CURRENT_DATE + INTERVAL d.n DAY, '09:30:00', '21:00:00', 'WORK', '初始化排班'
FROM (
  SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL
  SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL
  SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13
) d;

INSERT INTO `product_category` (`shop_id`, `name`, `sort_order`) VALUES
  (1, '院线产品', 10),
  (1, '美容耗材', 20),
  (1, '居家护理', 30);

-- 预约冲突校验必须在后端事务内执行：
-- SELECT id FROM appointment
-- WHERE staff_id = :staffId
--   AND status IN ('PENDING','CONFIRMED','CHECKED_IN','IN_SERVICE')
--   AND start_at < :newEndAt AND end_at > :newStartAt
-- FOR UPDATE;
-- 查询有结果时返回 APPOINTMENT_SLOT_CONFLICT，不得写入预约。
