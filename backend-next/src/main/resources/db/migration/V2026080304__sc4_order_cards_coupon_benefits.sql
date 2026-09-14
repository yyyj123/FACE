-- SC4 extends the existing order, payment and package facts.
-- Points batches, commerce, inventory reservation and legacy import remain outside this stage.

ALTER TABLE `package_product`
  ADD COLUMN `card_type` varchar(24) NOT NULL DEFAULT 'COMBO_TIMES' AFTER `package_code`,
  ADD COLUMN `principal_amount` decimal(12,2) NOT NULL DEFAULT 0 AFTER `sale_price`,
  ADD COLUMN `gift_amount` decimal(12,2) NOT NULL DEFAULT 0 AFTER `principal_amount`,
  ADD COLUMN `discount_percent` decimal(5,2) NULL AFTER `gift_amount`,
  ADD COLUMN `minimum_spend` decimal(12,2) NOT NULL DEFAULT 0 AFTER `discount_percent`,
  ADD COLUMN `maximum_savings` decimal(12,2) NULL AFTER `minimum_spend`,
  ADD COLUMN `usage_limit` int unsigned NULL AFTER `maximum_savings`,
  ADD COLUMN `scope_json` json NULL AFTER `usage_limit`,
  ADD CONSTRAINT `ck_package_product_card_type`
    CHECK (`card_type` IN ('COMBO_TIMES', 'STORED_VALUE', 'DISCOUNT')),
  ADD CONSTRAINT `ck_package_product_value_rules`
    CHECK (`principal_amount` >= 0 AND `gift_amount` >= 0 AND `minimum_spend` >= 0
      AND (`maximum_savings` IS NULL OR `maximum_savings` >= 0)),
  ADD CONSTRAINT `ck_package_product_discount_rules`
    CHECK (`discount_percent` IS NULL OR (`discount_percent` > 0 AND `discount_percent` <= 100));

ALTER TABLE `package_instance`
  MODIFY COLUMN `source_order_id` bigint unsigned NULL,
  MODIFY COLUMN `total_quantity` decimal(12,4) NOT NULL DEFAULT 0,
  MODIFY COLUMN `remaining_quantity` decimal(12,4) NOT NULL DEFAULT 0,
  ADD COLUMN `card_type` varchar(24) NOT NULL DEFAULT 'COMBO_TIMES' AFTER `instance_no`,
  ADD COLUMN `source_type` varchar(30) NOT NULL DEFAULT 'ONLINE_PURCHASE' AFTER `source_order_id`,
  ADD COLUMN `source_reference` varchar(100) NULL AFTER `source_type`,
  ADD COLUMN `frozen_quantity` decimal(12,4) NOT NULL DEFAULT 0 AFTER `remaining_quantity`,
  ADD COLUMN `usage_count` int unsigned NOT NULL DEFAULT 0 AFTER `remaining_quantity`,
  DROP CHECK `ck_package_instance_balance`,
  ADD CONSTRAINT `ck_package_instance_balance`
    CHECK (`total_quantity` >= 0 AND `remaining_quantity` >= 0
      AND `frozen_quantity` >= 0 AND `frozen_quantity` <= `remaining_quantity`
      AND `remaining_quantity` <= `total_quantity`),
  ADD CONSTRAINT `ck_package_instance_card_type`
    CHECK (`card_type` IN ('COMBO_TIMES', 'STORED_VALUE', 'DISCOUNT')),
  ADD CONSTRAINT `ck_package_instance_source`
    CHECK (`source_type` IN ('ONLINE_PURCHASE', 'OFFLINE_SALE', 'GIFT', 'REISSUE', 'LEGACY_IMPORT'));

ALTER TABLE `package_instance_item`
  ADD COLUMN `frozen_quantity` decimal(12,4) NOT NULL DEFAULT 0 AFTER `remaining_quantity`,
  DROP CHECK `ck_package_instance_item_balance`,
  ADD CONSTRAINT `ck_package_instance_item_balance`
    CHECK (`total_quantity` > 0 AND `remaining_quantity` >= 0
      AND `frozen_quantity` >= 0 AND `frozen_quantity` <= `remaining_quantity`
      AND `remaining_quantity` <= `total_quantity`);

CREATE TABLE `stored_value_batch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `package_instance_id` bigint unsigned NOT NULL,
  `batch_no` varchar(48) NOT NULL,
  `principal_total` decimal(12,2) NOT NULL,
  `gift_total` decimal(12,2) NOT NULL,
  `principal_remaining` decimal(12,2) NOT NULL,
  `gift_remaining` decimal(12,2) NOT NULL,
  `principal_frozen` decimal(12,2) NOT NULL DEFAULT 0,
  `gift_frozen` decimal(12,2) NOT NULL DEFAULT 0,
  `valid_from` date NOT NULL,
  `valid_until` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stored_value_batch_no` (`tenant_id`, `batch_no`),
  UNIQUE KEY `uk_stored_value_instance` (`package_instance_id`),
  KEY `idx_stored_value_member_use` (`tenant_id`, `shop_id`, `status`, `valid_until`),
  CONSTRAINT `fk_stored_value_batch_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_stored_value_batch_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_stored_value_batch_instance` FOREIGN KEY (`package_instance_id`) REFERENCES `package_instance` (`id`),
  CONSTRAINT `fk_stored_value_batch_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_stored_value_batch_amounts` CHECK (
    `principal_total` >= 0 AND `gift_total` >= 0
    AND `principal_remaining` >= 0 AND `principal_remaining` <= `principal_total`
    AND `gift_remaining` >= 0 AND `gift_remaining` <= `gift_total`
    AND `principal_frozen` >= 0 AND `principal_frozen` <= `principal_remaining`
    AND `gift_frozen` >= 0 AND `gift_frozen` <= `gift_remaining`
  ),
  CONSTRAINT `ck_stored_value_batch_dates` CHECK (`valid_until` >= `valid_from`),
  CONSTRAINT `ck_stored_value_batch_status` CHECK (`status` IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC4 储值卡本金与赠送金批次';

CREATE TABLE `stored_value_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `batch_id` bigint unsigned NOT NULL,
  `order_id` bigint unsigned NULL,
  `reservation_id` bigint unsigned NULL,
  `entry_type` varchar(24) NOT NULL,
  `principal_delta` decimal(12,2) NOT NULL DEFAULT 0,
  `gift_delta` decimal(12,2) NOT NULL DEFAULT 0,
  `principal_after` decimal(12,2) NOT NULL,
  `gift_after` decimal(12,2) NOT NULL,
  `business_key` varchar(160) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `original_ledger_id` bigint unsigned NULL,
  `reason` varchar(500) NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stored_ledger_business` (`tenant_id`, `business_key`),
  UNIQUE KEY `uk_stored_ledger_idempotency` (`tenant_id`, `idempotency_key`),
  UNIQUE KEY `uk_stored_ledger_reversal` (`tenant_id`, `original_ledger_id`),
  KEY `idx_stored_ledger_batch` (`batch_id`, `created_at`, `id`),
  CONSTRAINT `fk_stored_ledger_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_stored_ledger_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_stored_ledger_batch` FOREIGN KEY (`batch_id`) REFERENCES `stored_value_batch` (`id`),
  CONSTRAINT `fk_stored_ledger_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_stored_ledger_original` FOREIGN KEY (`original_ledger_id`) REFERENCES `stored_value_ledger` (`id`),
  CONSTRAINT `fk_stored_ledger_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_stored_ledger_type` CHECK (`entry_type` IN ('ISSUE', 'RESERVE', 'CONSUME', 'RELEASE', 'REFUND', 'REVERSAL', 'ADJUSTMENT')),
  CONSTRAINT `ck_stored_ledger_delta` CHECK (`principal_delta` <> 0 OR `gift_delta` <> 0),
  CONSTRAINT `ck_stored_ledger_after` CHECK (`principal_after` >= 0 AND `gift_after` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC4 储值卡不可变资金流水';

CREATE TABLE `coupon_template` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NULL,
  `template_code` varchar(48) NOT NULL,
  `name` varchar(120) NOT NULL,
  `coupon_type` varchar(30) NOT NULL,
  `threshold_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `benefit_value` decimal(12,2) NOT NULL DEFAULT 0,
  `service_id` bigint unsigned NULL,
  `validity_days` smallint unsigned NOT NULL,
  `return_on_full_refund` tinyint(1) NOT NULL DEFAULT 1,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `updated_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_template_code` (`tenant_id`, `template_code`),
  KEY `idx_coupon_template_status` (`tenant_id`, `shop_id`, `status`),
  CONSTRAINT `fk_coupon_template_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_coupon_template_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_coupon_template_service` FOREIGN KEY (`service_id`) REFERENCES `service_item` (`id`),
  CONSTRAINT `fk_coupon_template_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_coupon_template_updater` FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_coupon_template_type` CHECK (`coupon_type` IN ('THRESHOLD_REDUCTION', 'CASH', 'DISCOUNT', 'SERVICE_EXPERIENCE')),
  CONSTRAINT `ck_coupon_template_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
  CONSTRAINT `ck_coupon_template_value` CHECK (`threshold_amount` >= 0 AND `benefit_value` >= 0 AND `validity_days` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC4 护理优惠券模板';

CREATE TABLE `member_coupon` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `coupon_no` varchar(48) NOT NULL,
  `source_type` varchar(30) NOT NULL,
  `source_reference` varchar(100) NULL,
  `valid_from` datetime(3) NOT NULL,
  `valid_until` datetime(3) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'AVAILABLE',
  `locked_order_id` bigint unsigned NULL,
  `used_order_id` bigint unsigned NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_coupon_no` (`tenant_id`, `coupon_no`),
  KEY `idx_member_coupon_available` (`tenant_id`, `member_id`, `status`, `valid_until`),
  CONSTRAINT `fk_member_coupon_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_member_coupon_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_member_coupon_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_member_coupon_template` FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`),
  CONSTRAINT `fk_member_coupon_locked_order` FOREIGN KEY (`locked_order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_member_coupon_used_order` FOREIGN KEY (`used_order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_member_coupon_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_member_coupon_source` CHECK (`source_type` IN ('ADMIN_DIRECT', 'ACTIVITY_CLAIM', 'AFTERSALE_COMPENSATION')),
  CONSTRAINT `ck_member_coupon_status` CHECK (`status` IN ('UNCLAIMED', 'AVAILABLE', 'LOCKED', 'USED', 'EXPIRED', 'CANCELLED', 'RETURNED')),
  CONSTRAINT `ck_member_coupon_dates` CHECK (`valid_until` >= `valid_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC4 会员护理优惠券';

CREATE TABLE `coupon_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_coupon_id` bigint unsigned NOT NULL,
  `order_id` bigint unsigned NULL,
  `entry_type` varchar(24) NOT NULL,
  `from_status` varchar(20) NULL,
  `to_status` varchar(20) NOT NULL,
  `business_key` varchar(160) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `original_ledger_id` bigint unsigned NULL,
  `reason` varchar(500) NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_ledger_business` (`tenant_id`, `business_key`),
  UNIQUE KEY `uk_coupon_ledger_idempotency` (`tenant_id`, `idempotency_key`),
  UNIQUE KEY `uk_coupon_ledger_reversal` (`tenant_id`, `original_ledger_id`),
  KEY `idx_coupon_ledger_coupon` (`member_coupon_id`, `created_at`, `id`),
  CONSTRAINT `fk_coupon_ledger_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_coupon_ledger_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_coupon_ledger_coupon` FOREIGN KEY (`member_coupon_id`) REFERENCES `member_coupon` (`id`),
  CONSTRAINT `fk_coupon_ledger_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_coupon_ledger_original` FOREIGN KEY (`original_ledger_id`) REFERENCES `coupon_ledger` (`id`),
  CONSTRAINT `fk_coupon_ledger_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_coupon_ledger_type` CHECK (`entry_type` IN ('ISSUE', 'CLAIM', 'LOCK', 'USE', 'RELEASE', 'EXPIRE', 'CANCEL', 'RETURN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC4 优惠券不可变状态流水';

CREATE TABLE `promotion_activity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NULL,
  `activity_code` varchar(48) NOT NULL,
  `name` varchar(120) NOT NULL,
  `reduction_type` varchar(24) NOT NULL,
  `threshold_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `benefit_value` decimal(12,2) NOT NULL,
  `starts_at` datetime(3) NOT NULL,
  `ends_at` datetime(3) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `updated_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_promotion_activity_code` (`tenant_id`, `activity_code`),
  KEY `idx_promotion_activity_live` (`tenant_id`, `shop_id`, `status`, `starts_at`, `ends_at`),
  CONSTRAINT `fk_promotion_activity_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_promotion_activity_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_promotion_activity_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_promotion_activity_updater` FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_promotion_activity_type` CHECK (`reduction_type` IN ('FIXED', 'PERCENTAGE')),
  CONSTRAINT `ck_promotion_activity_status` CHECK (`status` IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'OFFLINE')),
  CONSTRAINT `ck_promotion_activity_dates` CHECK (`ends_at` > `starts_at`),
  CONSTRAINT `ck_promotion_activity_value` CHECK (`threshold_amount` >= 0 AND `benefit_value` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC4 订单价格活动，不复用营销投放';

CREATE TABLE `order_pricing_decision` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `order_id` bigint unsigned NOT NULL,
  `booking_time_lock_id` bigint unsigned NULL,
  `selection_type` varchar(24) NOT NULL DEFAULT 'NONE',
  `activity_id` bigint unsigned NULL,
  `member_coupon_id` bigint unsigned NULL,
  `discount_card_instance_id` bigint unsigned NULL,
  `subtotal_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL,
  `payable_amount` decimal(12,2) NOT NULL,
  `candidate_snapshot_json` json NOT NULL,
  `selection_snapshot_json` json NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_pricing_order` (`order_id`),
  UNIQUE KEY `uk_order_pricing_idempotency` (`tenant_id`, `idempotency_key`),
  CONSTRAINT `fk_order_pricing_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_order_pricing_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_order_pricing_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_order_pricing_lock` FOREIGN KEY (`booking_time_lock_id`) REFERENCES `booking_time_lock` (`id`),
  CONSTRAINT `fk_order_pricing_activity` FOREIGN KEY (`activity_id`) REFERENCES `promotion_activity` (`id`),
  CONSTRAINT `fk_order_pricing_coupon` FOREIGN KEY (`member_coupon_id`) REFERENCES `member_coupon` (`id`),
  CONSTRAINT `fk_order_pricing_discount_card` FOREIGN KEY (`discount_card_instance_id`) REFERENCES `package_instance` (`id`),
  CONSTRAINT `fk_order_pricing_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_order_pricing_selection` CHECK (`selection_type` IN ('NONE', 'ACTIVITY', 'COUPON', 'DISCOUNT_CARD')),
  CONSTRAINT `ck_order_pricing_amounts` CHECK (`subtotal_amount` >= 0 AND `discount_amount` >= 0 AND `payable_amount` >= 0 AND `payable_amount` = `subtotal_amount` - `discount_amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC4 服务端报价与用户优惠选择快照';

CREATE TABLE `benefit_reservation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `order_id` bigint unsigned NOT NULL,
  `appointment_id` bigint unsigned NULL,
  `benefit_type` varchar(24) NOT NULL,
  `package_instance_id` bigint unsigned NULL,
  `package_instance_item_id` bigint unsigned NULL,
  `stored_value_batch_id` bigint unsigned NULL,
  `member_coupon_id` bigint unsigned NULL,
  `quantity` decimal(12,4) NOT NULL DEFAULT 0,
  `principal_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `gift_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `status` varchar(20) NOT NULL DEFAULT 'RESERVED',
  `business_key` varchar(160) NOT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_by` bigint unsigned NOT NULL,
  `reserved_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_benefit_reservation_business` (`tenant_id`, `business_key`),
  UNIQUE KEY `uk_benefit_reservation_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_benefit_reservation_order` (`tenant_id`, `order_id`, `status`),
  CONSTRAINT `fk_benefit_reservation_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_benefit_reservation_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_benefit_reservation_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_benefit_reservation_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_benefit_reservation_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `fk_benefit_reservation_package` FOREIGN KEY (`package_instance_id`) REFERENCES `package_instance` (`id`),
  CONSTRAINT `fk_benefit_reservation_package_item` FOREIGN KEY (`package_instance_item_id`) REFERENCES `package_instance_item` (`id`),
  CONSTRAINT `fk_benefit_reservation_value_batch` FOREIGN KEY (`stored_value_batch_id`) REFERENCES `stored_value_batch` (`id`),
  CONSTRAINT `fk_benefit_reservation_coupon` FOREIGN KEY (`member_coupon_id`) REFERENCES `member_coupon` (`id`),
  CONSTRAINT `fk_benefit_reservation_creator` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_benefit_reservation_type` CHECK (`benefit_type` IN ('COMBO_TIMES', 'STORED_VALUE', 'COUPON', 'EXPERIENCE_COUPON')),
  CONSTRAINT `ck_benefit_reservation_status` CHECK (`status` IN ('RESERVED', 'CONSUMED', 'RELEASED', 'REVERSAL')),
  CONSTRAINT `ck_benefit_reservation_amounts` CHECK (`quantity` >= 0 AND `principal_amount` >= 0 AND `gift_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC4 订单权益冻结与核销状态';

ALTER TABLE `stored_value_ledger`
  ADD CONSTRAINT `fk_stored_ledger_reservation`
    FOREIGN KEY (`reservation_id`) REFERENCES `benefit_reservation` (`id`);

CREATE TABLE `checkout_fulfillment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `order_id` bigint unsigned NOT NULL,
  `payment_id` bigint unsigned NOT NULL,
  `booking_time_lock_id` bigint unsigned NULL,
  `appointment_id` bigint unsigned NULL,
  `status` varchar(20) NOT NULL DEFAULT 'COMPLETED',
  `fulfillment_key` varchar(160) NOT NULL,
  `card_issued_count` int unsigned NOT NULL DEFAULT 0,
  `benefit_consumed_count` int unsigned NOT NULL DEFAULT 0,
  `completed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkout_fulfillment_order` (`tenant_id`, `order_id`),
  UNIQUE KEY `uk_checkout_fulfillment_payment` (`tenant_id`, `payment_id`),
  UNIQUE KEY `uk_checkout_fulfillment_key` (`tenant_id`, `fulfillment_key`),
  CONSTRAINT `fk_checkout_fulfillment_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_checkout_fulfillment_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_checkout_fulfillment_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_checkout_fulfillment_payment` FOREIGN KEY (`payment_id`) REFERENCES `payment_transaction` (`id`),
  CONSTRAINT `fk_checkout_fulfillment_lock` FOREIGN KEY (`booking_time_lock_id`) REFERENCES `booking_time_lock` (`id`),
  CONSTRAINT `fk_checkout_fulfillment_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `ck_checkout_fulfillment_status` CHECK (`status` IN ('COMPLETED', 'REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC4 支付成功后的单次履约幂等事实';

ALTER TABLE `payment_transaction`
  DROP CHECK `ck_payment_amount`,
  ADD CONSTRAINT `ck_payment_amount` CHECK (
    (`payment_method` = 'ZERO_AMOUNT' AND `amount` = 0)
    OR (`payment_method` <> 'ZERO_AMOUNT' AND `amount` > 0)
  );

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('benefit:view', '查看卡项和优惠券', 'benefit', 'SENSITIVE'),
  ('benefit:manage', '管理卡项和优惠券', 'benefit', 'CRITICAL'),
  ('payment_config:view', '查看支付通道配置状态', 'payment', 'SENSITIVE'),
  ('payment_config:manage', '维护支付通道配置', 'payment', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT role.`id`, permission.`id`
FROM `role_definition` role
JOIN `permission_definition` permission
  ON permission.`permission_code` IN (
    'order:view', 'order:manage', 'finance:view', 'package:view', 'package:manage',
    'account:view', 'account:manage', 'benefit:view', 'benefit:manage',
    'payment_config:view', 'payment_config:manage'
  )
WHERE role.`role_code` IN ('ADMIN', 'SUPER_ADMIN');
