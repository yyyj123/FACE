ALTER TABLE `mall_payment`
  ADD COLUMN `channel_code` varchar(30) DEFAULT NULL AFTER `payment_method`,
  ADD COLUMN `channel_status` varchar(40) DEFAULT NULL AFTER `channel_request_no`,
  ADD COLUMN `external_transaction_no` varchar(100) DEFAULT NULL AFTER `channel_status`,
  ADD COLUMN `channel_event_id` varchar(100) DEFAULT NULL AFTER `external_transaction_no`;

CREATE TABLE `mall_payment_callback_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `mall_payment_id` bigint unsigned NOT NULL,
  `channel_code` varchar(30) NOT NULL,
  `channel_event_id` varchar(100) NOT NULL,
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `processing_status` varchar(20) NOT NULL,
  `failure_code` varchar(80) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_payment_callback_event` (`channel_code`, `channel_event_id`),
  CONSTRAINT `fk_mall_callback_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_callback_payment` FOREIGN KEY (`mall_payment_id`) REFERENCES `mall_payment` (`id`),
  CONSTRAINT `ck_mall_callback_status` CHECK (`processing_status` IN ('VERIFIED', 'PROCESSED', 'IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 商城支付回调去重证据';

CREATE TABLE `points_clawback` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `sales_order_id` bigint unsigned NOT NULL,
  `refund_id` bigint unsigned NOT NULL,
  `requested_points` bigint unsigned NOT NULL,
  `deducted_points` bigint unsigned NOT NULL,
  `outstanding_points` bigint unsigned NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_clawback_refund` (`tenant_id`, `refund_id`),
  KEY `idx_points_clawback_account` (`account_id`, `status`, `id`),
  CONSTRAINT `fk_points_clawback_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_points_clawback_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_points_clawback_account` FOREIGN KEY (`account_id`) REFERENCES `points_account` (`id`),
  CONSTRAINT `fk_points_clawback_order` FOREIGN KEY (`sales_order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_points_clawback_refund` FOREIGN KEY (`refund_id`) REFERENCES `refund_transaction` (`id`),
  CONSTRAINT `ck_points_clawback_status` CHECK (`status` IN ('SETTLED', 'OUTSTANDING')),
  CONSTRAINT `ck_points_clawback_balance` CHECK (`requested_points` = `deducted_points` + `outstanding_points`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SC5 退款积分追回与欠扣事实';
