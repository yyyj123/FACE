ALTER TABLE `order_pricing_decision`
  DROP CHECK `ck_order_pricing_selection`,
  ADD CONSTRAINT `ck_order_pricing_selection`
    CHECK (`selection_type` IN ('NONE', 'ACTIVITY', 'COUPON', 'DISCOUNT_CARD', 'POINTS'));

ALTER TABLE `notification_message`
  DROP CHECK `ck_notification_category`,
  ADD CONSTRAINT `ck_notification_category`
    CHECK (`category` IN ('APPROVAL', 'AFTERSALE', 'COMMISSION', 'SETTLEMENT', 'SYSTEM', 'POINTS', 'MALL'));

CREATE TABLE `points_account` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `available_points` bigint unsigned NOT NULL DEFAULT 0,
  `frozen_points` bigint unsigned NOT NULL DEFAULT 0,
  `total_earned` bigint unsigned NOT NULL DEFAULT 0,
  `total_consumed` bigint unsigned NOT NULL DEFAULT 0,
  `version` int unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_account_member` (`tenant_id`, `member_id`),
  CONSTRAINT `fk_points_account_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_points_account_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `ck_points_account_balance` CHECK (`available_points` >= 0 AND `frozen_points` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 会员积分账户投影';

CREATE TABLE `points_rule` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `rule_code` varchar(48) NOT NULL,
  `rule_type` varchar(24) NOT NULL,
  `target_type` varchar(24) NOT NULL DEFAULT 'GLOBAL',
  `target_id` bigint unsigned DEFAULT NULL,
  `points_per_yuan` decimal(12,4) NOT NULL DEFAULT 1,
  `points_per_currency` int unsigned NOT NULL DEFAULT 100,
  `minimum_points` int unsigned NOT NULL DEFAULT 100,
  `step_points` int unsigned NOT NULL DEFAULT 100,
  `max_discount_ratio` decimal(5,2) NOT NULL DEFAULT 50,
  `max_discount_amount` decimal(12,2) DEFAULT NULL,
  `single_cap_points` int unsigned DEFAULT NULL,
  `member_period_cap_points` int unsigned DEFAULT NULL,
  `validity_months` int unsigned NOT NULL DEFAULT 12,
  `starts_at` datetime(3) DEFAULT NULL,
  `ends_at` datetime(3) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_rule_code` (`tenant_id`, `rule_code`),
  KEY `idx_points_rule_target` (`tenant_id`, `rule_type`, `target_type`, `target_id`, `status`),
  CONSTRAINT `fk_points_rule_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_points_rule_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_points_rule_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_points_rule_type` CHECK (`rule_type` IN ('EARN', 'REDEEM')),
  CONSTRAINT `ck_points_rule_target` CHECK (`target_type` IN ('GLOBAL', 'SERVICE', 'PACKAGE', 'ACTIVITY', 'MALL_SKU')),
  CONSTRAINT `ck_points_rule_validity` CHECK (`validity_months` BETWEEN 1 AND 60),
  CONSTRAINT `ck_points_rule_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 积分获得与抵扣规则';

CREATE TABLE `points_batch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `account_id` bigint unsigned NOT NULL,
  `batch_no` varchar(48) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_rule_id` bigint unsigned DEFAULT NULL,
  `source_reference_type` varchar(40) NOT NULL,
  `source_reference_id` bigint unsigned DEFAULT NULL,
  `granted_points` bigint unsigned NOT NULL,
  `remaining_points` bigint unsigned NOT NULL,
  `frozen_points` bigint unsigned NOT NULL DEFAULT 0,
  `earned_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_batch_no` (`tenant_id`, `batch_no`),
  KEY `idx_points_batch_fefo` (`account_id`, `status`, `expires_at`, `id`),
  CONSTRAINT `fk_points_batch_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_points_batch_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_points_batch_account` FOREIGN KEY (`account_id`) REFERENCES `points_account` (`id`),
  CONSTRAINT `fk_points_batch_rule` FOREIGN KEY (`source_rule_id`) REFERENCES `points_rule` (`id`),
  CONSTRAINT `fk_points_batch_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_points_batch_balance` CHECK (`granted_points` > 0 AND `remaining_points` <= `granted_points` AND `frozen_points` <= `remaining_points`),
  CONSTRAINT `ck_points_batch_status` CHECK (`status` IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 积分有效期资产批次';

CREATE TABLE `points_reservation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `account_id` bigint unsigned NOT NULL,
  `reference_type` varchar(40) NOT NULL,
  `reference_id` bigint unsigned NOT NULL,
  `requested_points` bigint unsigned NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'RESERVED',
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `reserved_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_reservation_reference` (`tenant_id`, `reference_type`, `reference_id`),
  UNIQUE KEY `uk_points_reservation_idempotency` (`tenant_id`, `idempotency_key`),
  CONSTRAINT `fk_points_reservation_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_points_reservation_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_points_reservation_account` FOREIGN KEY (`account_id`) REFERENCES `points_account` (`id`),
  CONSTRAINT `fk_points_reservation_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_points_reservation_points` CHECK (`requested_points` > 0),
  CONSTRAINT `ck_points_reservation_status` CHECK (`status` IN ('RESERVED', 'CONSUMED', 'RELEASED', 'RESTORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 积分冻结主记录';

CREATE TABLE `points_reservation_allocation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `reservation_id` bigint unsigned NOT NULL,
  `batch_id` bigint unsigned NOT NULL,
  `allocated_points` bigint unsigned NOT NULL,
  `restored_points` bigint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_allocation_batch` (`reservation_id`, `batch_id`),
  CONSTRAINT `fk_points_allocation_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `points_reservation` (`id`),
  CONSTRAINT `fk_points_allocation_batch` FOREIGN KEY (`batch_id`) REFERENCES `points_batch` (`id`),
  CONSTRAINT `ck_points_allocation_value` CHECK (`allocated_points` > 0 AND `restored_points` <= `allocated_points`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 积分冻结到原批次分配';

CREATE TABLE `points_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `account_id` bigint unsigned NOT NULL,
  `batch_id` bigint unsigned DEFAULT NULL,
  `reservation_id` bigint unsigned DEFAULT NULL,
  `entry_type` varchar(24) NOT NULL,
  `points_delta` bigint NOT NULL,
  `available_after` bigint unsigned NOT NULL,
  `frozen_after` bigint unsigned NOT NULL,
  `reference_type` varchar(40) NOT NULL,
  `reference_id` bigint unsigned DEFAULT NULL,
  `business_key` varchar(160) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_ledger_business` (`tenant_id`, `business_key`),
  UNIQUE KEY `uk_points_ledger_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_points_ledger_account_time` (`account_id`, `created_at`, `id`),
  CONSTRAINT `fk_points_ledger_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_points_ledger_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_points_ledger_account` FOREIGN KEY (`account_id`) REFERENCES `points_account` (`id`),
  CONSTRAINT `fk_points_ledger_batch` FOREIGN KEY (`batch_id`) REFERENCES `points_batch` (`id`),
  CONSTRAINT `fk_points_ledger_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `points_reservation` (`id`),
  CONSTRAINT `fk_points_ledger_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_points_ledger_type` CHECK (`entry_type` IN ('GRANT', 'RESERVE', 'CONSUME', 'RELEASE', 'REFUND', 'EXPIRE', 'ADJUSTMENT')),
  CONSTRAINT `ck_points_ledger_delta` CHECK (`points_delta` <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 积分不可变流水';

CREATE TABLE `points_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `task_code` varchar(48) NOT NULL,
  `task_type` varchar(24) NOT NULL,
  `name` varchar(120) NOT NULL,
  `reward_points` int unsigned NOT NULL,
  `cycle_days` int unsigned NOT NULL DEFAULT 1,
  `daily_rewards_json` json NOT NULL,
  `cycle_bonus_points` int unsigned NOT NULL DEFAULT 0,
  `repeat_cycle` tinyint(1) NOT NULL DEFAULT 1,
  `member_period_cap_points` int unsigned DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_task_code` (`tenant_id`, `task_code`),
  CONSTRAINT `fk_points_task_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_points_task_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_points_task_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_points_task_type` CHECK (`task_type` IN ('REGISTER', 'PROFILE', 'CHECKIN', 'CARE_COMPLETE', 'REVIEW', 'ACTIVITY', 'ADMIN_COMPENSATION')),
  CONSTRAINT `ck_points_task_cycle` CHECK (`cycle_days` BETWEEN 1 AND 365),
  CONSTRAINT `ck_points_task_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 积分任务和签到阶梯';

CREATE TABLE `member_checkin` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `task_id` bigint unsigned NOT NULL,
  `checkin_date` date NOT NULL,
  `streak_day` int unsigned NOT NULL,
  `cycle_day` int unsigned NOT NULL,
  `reward_points` int unsigned NOT NULL,
  `points_batch_id` bigint unsigned NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_checkin_day` (`tenant_id`, `member_id`, `task_id`, `checkin_date`),
  UNIQUE KEY `uk_member_checkin_idempotency` (`tenant_id`, `idempotency_key`),
  CONSTRAINT `fk_member_checkin_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_member_checkin_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_member_checkin_task` FOREIGN KEY (`task_id`) REFERENCES `points_task` (`id`),
  CONSTRAINT `fk_member_checkin_batch` FOREIGN KEY (`points_batch_id`) REFERENCES `points_batch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 会员每日签到事实';

CREATE TABLE `points_expiry_notice` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `notice_date` date NOT NULL,
  `days_before` int unsigned NOT NULL,
  `expiring_points` bigint unsigned NOT NULL,
  `event_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_expiry_notice` (`tenant_id`, `account_id`, `notice_date`, `days_before`),
  CONSTRAINT `fk_points_notice_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_points_notice_account` FOREIGN KEY (`account_id`) REFERENCES `points_account` (`id`),
  CONSTRAINT `ck_points_notice_days` CHECK (`days_before` IN (1, 7, 30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 积分到期汇总提醒去重';

CREATE TABLE `mall_category` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `category_code` varchar(48) NOT NULL,
  `name` varchar(100) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_category_code` (`tenant_id`, `category_code`),
  CONSTRAINT `fk_mall_category_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `ck_mall_category_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城分类';

CREATE TABLE `mall_product` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned DEFAULT NULL,
  `category_id` bigint unsigned NOT NULL,
  `product_code` varchar(48) NOT NULL,
  `product_type` varchar(24) NOT NULL,
  `name` varchar(160) NOT NULL,
  `brand_name` varchar(100) DEFAULT NULL,
  `cover_url` varchar(500) DEFAULT NULL,
  `description` text,
  `care_service_id` bigint unsigned DEFAULT NULL,
  `delivery_mode` varchar(24) NOT NULL,
  `freight_template_code` varchar(48) DEFAULT NULL,
  `separate_shipping` tinyint(1) NOT NULL DEFAULT 0,
  `after_sale_policy` varchar(500) DEFAULT NULL,
  `sale_starts_at` datetime(3) DEFAULT NULL,
  `sale_ends_at` datetime(3) DEFAULT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'DRAFT',
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_product_code` (`tenant_id`, `product_code`),
  KEY `idx_mall_product_sale` (`tenant_id`, `status`, `category_id`),
  CONSTRAINT `fk_mall_product_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_product_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_mall_product_category` FOREIGN KEY (`category_id`) REFERENCES `mall_category` (`id`),
  CONSTRAINT `fk_mall_product_service` FOREIGN KEY (`care_service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `fk_mall_product_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_mall_product_type` CHECK (`product_type` IN ('PHYSICAL', 'CARE_ENTITLEMENT')),
  CONSTRAINT `ck_mall_product_delivery` CHECK (`delivery_mode` IN ('DELIVERY', 'PICKUP', 'BOTH', 'DIGITAL')),
  CONSTRAINT `ck_mall_product_status` CHECK (`status` IN ('DRAFT', 'SCHEDULED', 'ON_SALE', 'SCHEDULED_OFFLINE', 'OFFLINE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城商品';

CREATE TABLE `mall_sku` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `product_id` bigint unsigned NOT NULL,
  `sku_code` varchar(64) NOT NULL,
  `spec_json` json NOT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  `weight_grams` int unsigned NOT NULL DEFAULT 0,
  `cash_price` decimal(12,2) DEFAULT NULL,
  `points_price` bigint unsigned DEFAULT NULL,
  `combo_cash_price` decimal(12,2) DEFAULT NULL,
  `combo_points_price` bigint unsigned DEFAULT NULL,
  `cash_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `points_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `combo_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `purchase_limit` int unsigned DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `version` int unsigned NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_sku_code` (`tenant_id`, `sku_code`),
  CONSTRAINT `fk_mall_sku_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_sku_product` FOREIGN KEY (`product_id`) REFERENCES `mall_product` (`id`),
  CONSTRAINT `ck_mall_sku_mode` CHECK (`cash_enabled` = 1 OR `points_enabled` = 1 OR `combo_enabled` = 1),
  CONSTRAINT `ck_mall_sku_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城 SKU 三种购买模式';

CREATE TABLE `mall_sku_inventory` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `sku_id` bigint unsigned NOT NULL,
  `available_quantity` int unsigned NOT NULL DEFAULT 0,
  `reserved_quantity` int unsigned NOT NULL DEFAULT 0,
  `sold_quantity` int unsigned NOT NULL DEFAULT 0,
  `return_pending_quantity` int unsigned NOT NULL DEFAULT 0,
  `damaged_quantity` int unsigned NOT NULL DEFAULT 0,
  `warning_threshold` int unsigned NOT NULL DEFAULT 0,
  `version` int unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_inventory_sku_shop` (`tenant_id`, `shop_id`, `sku_id`),
  CONSTRAINT `fk_mall_inventory_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_inventory_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_mall_inventory_sku` FOREIGN KEY (`sku_id`) REFERENCES `mall_sku` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 三种模式共享 SKU 库存';

CREATE TABLE `mall_inventory_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `inventory_id` bigint unsigned NOT NULL,
  `movement_type` varchar(24) NOT NULL,
  `available_delta` int NOT NULL DEFAULT 0,
  `reserved_delta` int NOT NULL DEFAULT 0,
  `sold_delta` int NOT NULL DEFAULT 0,
  `return_pending_delta` int NOT NULL DEFAULT 0,
  `damaged_delta` int NOT NULL DEFAULT 0,
  `available_after` int unsigned NOT NULL,
  `reserved_after` int unsigned NOT NULL,
  `sold_after` int unsigned NOT NULL,
  `reference_type` varchar(40) NOT NULL,
  `reference_id` bigint unsigned DEFAULT NULL,
  `audit_no` varchar(64) NOT NULL,
  `business_key` varchar(160) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_inventory_business` (`tenant_id`, `business_key`),
  UNIQUE KEY `uk_mall_inventory_audit` (`tenant_id`, `audit_no`),
  CONSTRAINT `fk_mall_inventory_ledger_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_inventory_ledger_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_mall_inventory_ledger_inventory` FOREIGN KEY (`inventory_id`) REFERENCES `mall_sku_inventory` (`id`),
  CONSTRAINT `fk_mall_inventory_ledger_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_mall_inventory_movement` CHECK (`movement_type` IN ('MANUAL_IN', 'ADJUST', 'RESERVE', 'RELEASE', 'SHIP', 'RETURN_PENDING', 'RESTORE', 'DAMAGE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城库存不可变流水';

CREATE TABLE `mall_cart_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `sku_id` bigint unsigned NOT NULL,
  `purchase_mode` varchar(20) NOT NULL,
  `quantity` int unsigned NOT NULL,
  `delivery_mode` varchar(20) NOT NULL,
  `pickup_shop_id` bigint unsigned DEFAULT NULL,
  `selected` tinyint(1) NOT NULL DEFAULT 1,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_cart_choice` (`tenant_id`, `member_id`, `sku_id`, `purchase_mode`, `delivery_mode`, `pickup_shop_id`),
  CONSTRAINT `fk_mall_cart_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_cart_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_mall_cart_sku` FOREIGN KEY (`sku_id`) REFERENCES `mall_sku` (`id`),
  CONSTRAINT `fk_mall_cart_pickup` FOREIGN KEY (`pickup_shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_mall_cart_mode` CHECK (`purchase_mode` IN ('CASH', 'POINTS', 'COMBINATION')),
  CONSTRAINT `ck_mall_cart_delivery` CHECK (`delivery_mode` IN ('DELIVERY', 'PICKUP', 'DIGITAL')),
  CONSTRAINT `ck_mall_cart_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城购物车';

CREATE TABLE `mall_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `order_no` varchar(48) NOT NULL,
  `cash_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `points_amount` bigint unsigned NOT NULL DEFAULT 0,
  `status` varchar(24) NOT NULL,
  `address_snapshot_json` json DEFAULT NULL,
  `create_idempotency_key` varchar(80) NOT NULL,
  `create_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `paid_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_order_no` (`tenant_id`, `order_no`),
  UNIQUE KEY `uk_mall_order_idempotency` (`tenant_id`, `create_idempotency_key`),
  CONSTRAINT `fk_mall_order_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_order_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_mall_order_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_mall_order_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_mall_order_status` CHECK (`status` IN ('PENDING_PAYMENT', 'PAID', 'PARTIALLY_SHIPPED', 'SHIPPED', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城母订单';

CREATE TABLE `mall_sub_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `mall_order_id` bigint unsigned NOT NULL,
  `sub_order_no` varchar(52) NOT NULL,
  `split_key` varchar(200) NOT NULL,
  `purchase_mode` varchar(20) NOT NULL,
  `delivery_mode` varchar(20) NOT NULL,
  `pickup_shop_id` bigint unsigned DEFAULT NULL,
  `freight_template_code` varchar(48) DEFAULT NULL,
  `cash_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `points_amount` bigint unsigned NOT NULL DEFAULT 0,
  `freight_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `status` varchar(24) NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_sub_order_no` (`tenant_id`, `sub_order_no`),
  UNIQUE KEY `uk_mall_sub_order_split` (`mall_order_id`, `split_key`),
  CONSTRAINT `fk_mall_sub_order_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_sub_order_master` FOREIGN KEY (`mall_order_id`) REFERENCES `mall_order` (`id`),
  CONSTRAINT `fk_mall_sub_order_pickup` FOREIGN KEY (`pickup_shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `ck_mall_sub_order_mode` CHECK (`purchase_mode` IN ('CASH', 'POINTS', 'COMBINATION')),
  CONSTRAINT `ck_mall_sub_order_delivery` CHECK (`delivery_mode` IN ('DELIVERY', 'PICKUP', 'DIGITAL')),
  CONSTRAINT `ck_mall_sub_order_status` CHECK (`status` IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 按模式和履约键拆分的商城子订单';

CREATE TABLE `mall_order_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `mall_order_id` bigint unsigned NOT NULL,
  `sub_order_id` bigint unsigned NOT NULL,
  `product_id` bigint unsigned NOT NULL,
  `sku_id` bigint unsigned NOT NULL,
  `product_name_snapshot` varchar(160) NOT NULL,
  `sku_snapshot_json` json NOT NULL,
  `purchase_mode` varchar(20) NOT NULL,
  `quantity` int unsigned NOT NULL,
  `cash_unit_price` decimal(12,2) NOT NULL DEFAULT 0,
  `points_unit_price` bigint unsigned NOT NULL DEFAULT 0,
  `cash_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `points_amount` bigint unsigned NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_mall_order_item_master` (`mall_order_id`, `sub_order_id`),
  CONSTRAINT `fk_mall_order_item_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_order_item_master` FOREIGN KEY (`mall_order_id`) REFERENCES `mall_order` (`id`),
  CONSTRAINT `fk_mall_order_item_sub` FOREIGN KEY (`sub_order_id`) REFERENCES `mall_sub_order` (`id`),
  CONSTRAINT `fk_mall_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `mall_product` (`id`),
  CONSTRAINT `fk_mall_order_item_sku` FOREIGN KEY (`sku_id`) REFERENCES `mall_sku` (`id`),
  CONSTRAINT `ck_mall_order_item_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城订单行价格快照';

CREATE TABLE `mall_stock_reservation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `mall_order_id` bigint unsigned NOT NULL,
  `order_item_id` bigint unsigned NOT NULL,
  `inventory_id` bigint unsigned NOT NULL,
  `quantity` int unsigned NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'RESERVED',
  `business_key` varchar(160) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_stock_reservation_item` (`tenant_id`, `order_item_id`),
  UNIQUE KEY `uk_mall_stock_reservation_business` (`tenant_id`, `business_key`),
  CONSTRAINT `fk_mall_stock_reservation_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_stock_reservation_order` FOREIGN KEY (`mall_order_id`) REFERENCES `mall_order` (`id`),
  CONSTRAINT `fk_mall_stock_reservation_item` FOREIGN KEY (`order_item_id`) REFERENCES `mall_order_item` (`id`),
  CONSTRAINT `fk_mall_stock_reservation_inventory` FOREIGN KEY (`inventory_id`) REFERENCES `mall_sku_inventory` (`id`),
  CONSTRAINT `ck_mall_stock_reservation_status` CHECK (`status` IN ('RESERVED', 'SHIPPED', 'RELEASED', 'RETURN_PENDING', 'RESTORED', 'DAMAGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城订单库存冻结';

CREATE TABLE `mall_payment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `mall_order_id` bigint unsigned NOT NULL,
  `payment_no` varchar(48) NOT NULL,
  `payment_method` varchar(30) NOT NULL,
  `cash_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `points_amount` bigint unsigned NOT NULL DEFAULT 0,
  `channel_request_no` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `paid_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_payment_no` (`tenant_id`, `payment_no`),
  UNIQUE KEY `uk_mall_payment_idempotency` (`tenant_id`, `idempotency_key`),
  CONSTRAINT `fk_mall_payment_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_payment_order` FOREIGN KEY (`mall_order_id`) REFERENCES `mall_order` (`id`),
  CONSTRAINT `ck_mall_payment_status` CHECK (`status` IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城人民币与积分支付摘要';

CREATE TABLE `mall_package` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `package_no` varchar(48) NOT NULL,
  `delivery_mode` varchar(20) NOT NULL,
  `logistics_company` varchar(100) DEFAULT NULL,
  `tracking_no` varchar(100) DEFAULT NULL,
  `pickup_shop_id` bigint unsigned DEFAULT NULL,
  `address_snapshot_json` json DEFAULT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'CREATED',
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `shipped_at` datetime(3) DEFAULT NULL,
  `auto_confirm_at` datetime(3) DEFAULT NULL,
  `confirmed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_package_no` (`tenant_id`, `package_no`),
  CONSTRAINT `fk_mall_package_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_package_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_mall_package_pickup` FOREIGN KEY (`pickup_shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_mall_package_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_mall_package_delivery` CHECK (`delivery_mode` IN ('DELIVERY', 'PICKUP', 'DIGITAL')),
  CONSTRAINT `ck_mall_package_status` CHECK (`status` IN ('CREATED', 'SHIPPED', 'READY_FOR_PICKUP', 'RECEIVED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 与订单分离的包裹';

CREATE TABLE `mall_package_sub_order` (
  `package_id` bigint unsigned NOT NULL,
  `sub_order_id` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`package_id`, `sub_order_id`),
  UNIQUE KEY `uk_mall_sub_order_package` (`sub_order_id`),
  CONSTRAINT `fk_mall_package_link_package` FOREIGN KEY (`package_id`) REFERENCES `mall_package` (`id`),
  CONSTRAINT `fk_mall_package_link_sub_order` FOREIGN KEY (`sub_order_id`) REFERENCES `mall_sub_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 一包多子单关系';

CREATE TABLE `care_redemption_entitlement` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `service_id` bigint unsigned NOT NULL,
  `mall_order_item_id` bigint unsigned NOT NULL,
  `entitlement_no` varchar(48) NOT NULL,
  `valid_from` date NOT NULL,
  `valid_until` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'AVAILABLE',
  `appointment_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_care_redemption_no` (`tenant_id`, `entitlement_no`),
  UNIQUE KEY `uk_care_redemption_item` (`mall_order_item_id`),
  CONSTRAINT `fk_care_redemption_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_care_redemption_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_care_redemption_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_care_redemption_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `fk_care_redemption_item` FOREIGN KEY (`mall_order_item_id`) REFERENCES `mall_order_item` (`id`),
  CONSTRAINT `fk_care_redemption_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `ck_care_redemption_status` CHECK (`status` IN ('AVAILABLE', 'RESERVED', 'USED', 'EXPIRED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 积分商城护理兑换权益';

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('points:view', '查看积分账户与规则', 'points', 'SENSITIVE'),
  ('points:manage', '管理积分规则与补偿', 'points', 'CRITICAL'),
  ('mall:view', '查看商城商品订单与库存', 'mall', 'SENSITIVE'),
  ('mall:manage', '管理商城商品订单与库存', 'mall', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT role.`id`, permission.`id`
FROM `role_definition` role
JOIN `permission_definition` permission
  ON permission.`permission_code` IN ('points:view', 'points:manage', 'mall:view', 'mall:manage')
WHERE role.`role_code` IN ('ADMIN', 'SUPER_ADMIN');

INSERT INTO `points_rule` (
  `tenant_id`, `shop_id`, `rule_code`, `rule_type`, `target_type`,
  `points_per_yuan`, `points_per_currency`, `minimum_points`, `step_points`,
  `max_discount_ratio`, `max_discount_amount`, `validity_months`, `status`, `created_by`
)
SELECT t.`id`, NULL, 'GLOBAL_EARN', 'EARN', 'GLOBAL', 1, 100, 100, 100, 50, 100, 12, 'ACTIVE', a.`id`
FROM `tenant` t
JOIN `account` a ON a.`tenant_id` = t.`id` AND a.`role_code` = 'SUPER_ADMIN'
WHERE a.`id` = (SELECT MIN(a2.`id`) FROM `account` a2 WHERE a2.`tenant_id` = t.`id` AND a2.`role_code` = 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE `rule_code` = VALUES(`rule_code`);

INSERT INTO `points_rule` (
  `tenant_id`, `shop_id`, `rule_code`, `rule_type`, `target_type`,
  `points_per_yuan`, `points_per_currency`, `minimum_points`, `step_points`,
  `max_discount_ratio`, `max_discount_amount`, `validity_months`, `status`, `created_by`
)
SELECT t.`id`, NULL, 'GLOBAL_REDEEM', 'REDEEM', 'GLOBAL', 1, 100, 100, 100, 50, 100, 12, 'ACTIVE', a.`id`
FROM `tenant` t
JOIN `account` a ON a.`tenant_id` = t.`id` AND a.`role_code` = 'SUPER_ADMIN'
WHERE a.`id` = (SELECT MIN(a2.`id`) FROM `account` a2 WHERE a2.`tenant_id` = t.`id` AND a2.`role_code` = 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE `rule_code` = VALUES(`rule_code`);

INSERT INTO `points_task` (
  `tenant_id`, `shop_id`, `task_code`, `task_type`, `name`, `reward_points`,
  `cycle_days`, `daily_rewards_json`, `cycle_bonus_points`, `repeat_cycle`,
  `status`, `created_by`
)
SELECT t.`id`, NULL, 'DAILY_CHECKIN', 'CHECKIN', '每日签到', 10,
       7, JSON_ARRAY(10, 10, 15, 15, 20, 20, 30), 50, 1, 'ACTIVE', a.`id`
FROM `tenant` t
JOIN `account` a ON a.`tenant_id` = t.`id` AND a.`role_code` = 'SUPER_ADMIN'
WHERE a.`id` = (SELECT MIN(a2.`id`) FROM `account` a2 WHERE a2.`tenant_id` = t.`id` AND a2.`role_code` = 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE `task_code` = VALUES(`task_code`);
