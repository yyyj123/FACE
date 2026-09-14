-- SC6 fulfillment confirmation, versioned reviews and after-sale closure.
-- Expand-only: historical technical statuses remain readable while fulfillment
-- semantics are projected independently for the V3 responsive applications.

ALTER TABLE `service_record`
  ADD COLUMN `fulfillment_status` varchar(40) NOT NULL DEFAULT 'COMPLETED' AFTER `status`,
  ADD COLUMN `customer_confirmed_at` datetime(3) DEFAULT NULL AFTER `fulfillment_status`,
  ADD KEY `idx_service_record_fulfillment`
    (`tenant_id`, `shop_id`, `fulfillment_status`, `actual_end_at`),
  ADD CONSTRAINT `ck_service_record_fulfillment` CHECK (`fulfillment_status` IN (
    'IN_SERVICE', 'PENDING_CUSTOMER_CONFIRMATION', 'COMPLETED', 'AFTER_SALES_PROCESSING'
  ));

UPDATE `service_record`
SET `fulfillment_status` = CASE
  WHEN `status` = 'IN_PROGRESS' THEN 'IN_SERVICE'
  ELSE 'COMPLETED'
END;

ALTER TABLE `appointment`
  ADD COLUMN `fulfillment_status` varchar(40) NOT NULL DEFAULT 'COMPLETED' AFTER `status`,
  ADD KEY `idx_appointment_fulfillment`
    (`tenant_id`, `shop_id`, `fulfillment_status`, `start_at`),
  ADD CONSTRAINT `ck_appointment_fulfillment` CHECK (`fulfillment_status` IN (
    'CONFIRMED', 'ARRIVED', 'IN_SERVICE', 'PENDING_CUSTOMER_CONFIRMATION',
    'COMPLETED', 'AFTER_SALES_PROCESSING'
  ));

UPDATE `appointment`
SET `fulfillment_status` = CASE `status`
  WHEN 'CHECKED_IN' THEN 'ARRIVED'
  WHEN 'IN_SERVICE' THEN 'IN_SERVICE'
  WHEN 'COMPLETED' THEN 'COMPLETED'
  ELSE 'CONFIRMED'
END;

ALTER TABLE `customer_confirmation`
  DROP CHECK `ck_customer_confirmation_status`,
  DROP CHECK `ck_customer_confirmation_rejection`,
  ADD COLUMN `due_at` datetime(3) DEFAULT NULL AFTER `status`,
  ADD COLUMN `finalization_source` varchar(30) DEFAULT NULL AFTER `acted_at`,
  ADD COLUMN `finalized_at` datetime(3) DEFAULT NULL AFTER `finalization_source`,
  ADD COLUMN `after_sale_case_id` bigint unsigned DEFAULT NULL AFTER `finalized_at`,
  ADD KEY `idx_customer_confirmation_due` (`status`, `due_at`),
  ADD CONSTRAINT `fk_confirmation_after_sale`
    FOREIGN KEY (`after_sale_case_id`) REFERENCES `after_sale_case` (`id`),
  ADD CONSTRAINT `ck_customer_confirmation_status` CHECK (`status` IN (
    'PENDING', 'CONFIRMED', 'SYSTEM_AUTO_CONFIRMED', 'REJECTED'
  )),
  ADD CONSTRAINT `ck_customer_confirmation_rejection` CHECK (
    (`status` <> 'REJECTED' AND `reject_reason` IS NULL)
    OR (`status` = 'REJECTED' AND `reject_reason` IS NOT NULL)
  ),
  ADD CONSTRAINT `ck_customer_confirmation_source` CHECK (
    `finalization_source` IS NULL
    OR `finalization_source` IN ('MEMBER', 'SYSTEM', 'DISPUTE')
  );

UPDATE `customer_confirmation`
SET `due_at` = DATE_ADD(`created_at`, INTERVAL 24 HOUR)
WHERE `due_at` IS NULL;

CREATE TABLE `service_fulfillment_fact` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `confirmation_id` bigint unsigned NOT NULL,
  `service_record_id` bigint unsigned NOT NULL,
  `appointment_id` bigint unsigned DEFAULT NULL,
  `order_id` bigint unsigned DEFAULT NULL,
  `member_id` bigint unsigned NOT NULL,
  `outcome` varchar(20) NOT NULL,
  `entitlement_consumed_count` int unsigned NOT NULL DEFAULT 0,
  `points_batch_id` bigint unsigned DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `finalized_by` bigint unsigned NOT NULL,
  `finalized_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_fulfillment_confirmation` (`tenant_id`, `confirmation_id`),
  UNIQUE KEY `uk_service_fulfillment_record` (`tenant_id`, `service_record_id`),
  UNIQUE KEY `uk_service_fulfillment_key` (`tenant_id`, `idempotency_key`),
  CONSTRAINT `fk_service_fulfillment_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_service_fulfillment_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_service_fulfillment_confirmation` FOREIGN KEY (`confirmation_id`) REFERENCES `customer_confirmation` (`id`),
  CONSTRAINT `fk_service_fulfillment_record` FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_service_fulfillment_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`),
  CONSTRAINT `fk_service_fulfillment_order` FOREIGN KEY (`order_id`) REFERENCES `sales_order` (`id`),
  CONSTRAINT `fk_service_fulfillment_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_service_fulfillment_actor` FOREIGN KEY (`finalized_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_service_fulfillment_outcome` CHECK (`outcome` IN ('CONFIRMED', 'SYSTEM_AUTO_CONFIRMED', 'DISPUTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC6 one-time service fulfillment fact';

CREATE TABLE `service_review` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `service_record_id` bigint unsigned NOT NULL,
  `confirmation_id` bigint unsigned NOT NULL,
  `staff_rating` tinyint unsigned NOT NULL,
  `effect_rating` tinyint unsigned NOT NULL,
  `environment_rating` tinyint unsigned NOT NULL,
  `average_rating` decimal(2,1) NOT NULL,
  `visibility` varchar(20) NOT NULL,
  `moderation_status` varchar(20) NOT NULL,
  `current_version_no` int unsigned NOT NULL DEFAULT 1,
  `wants_contact` tinyint(1) NOT NULL DEFAULT 0,
  `reply_text` varchar(500) DEFAULT NULL,
  `replied_by` bigint unsigned DEFAULT NULL,
  `replied_at` datetime(3) DEFAULT NULL,
  `hidden_reason` varchar(500) DEFAULT NULL,
  `after_sale_case_id` bigint unsigned DEFAULT NULL,
  `deleted_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_review_record` (`tenant_id`, `service_record_id`),
  KEY `idx_service_review_moderation` (`tenant_id`, `shop_id`, `moderation_status`, `created_at`),
  CONSTRAINT `fk_service_review_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_service_review_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_service_review_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_service_review_record` FOREIGN KEY (`service_record_id`) REFERENCES `service_record` (`id`),
  CONSTRAINT `fk_service_review_confirmation` FOREIGN KEY (`confirmation_id`) REFERENCES `customer_confirmation` (`id`),
  CONSTRAINT `fk_service_review_reply_actor` FOREIGN KEY (`replied_by`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_service_review_after_sale` FOREIGN KEY (`after_sale_case_id`) REFERENCES `after_sale_case` (`id`),
  CONSTRAINT `ck_service_review_ratings` CHECK (
    `staff_rating` BETWEEN 1 AND 5 AND `effect_rating` BETWEEN 1 AND 5
    AND `environment_rating` BETWEEN 1 AND 5
  ),
  CONSTRAINT `ck_service_review_visibility` CHECK (`visibility` IN ('PUBLIC', 'SHOP_ONLY')),
  CONSTRAINT `ck_service_review_moderation` CHECK (`moderation_status` IN ('PENDING', 'APPROVED', 'HIDDEN', 'NOT_REQUIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC6 current review projection';

CREATE TABLE `service_review_version` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `review_id` bigint unsigned NOT NULL,
  `version_no` int unsigned NOT NULL,
  `staff_rating` tinyint unsigned NOT NULL,
  `effect_rating` tinyint unsigned NOT NULL,
  `environment_rating` tinyint unsigned NOT NULL,
  `average_rating` decimal(2,1) NOT NULL,
  `visibility` varchar(20) NOT NULL,
  `content` varchar(1000) DEFAULT NULL,
  `wants_contact` tinyint(1) NOT NULL DEFAULT 0,
  `change_type` varchar(20) NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_review_version` (`tenant_id`, `review_id`, `version_no`),
  CONSTRAINT `fk_review_version_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_review_version_review` FOREIGN KEY (`review_id`) REFERENCES `service_review` (`id`),
  CONSTRAINT `fk_review_version_actor` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_review_version_change` CHECK (`change_type` IN ('CREATED', 'EDITED', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC6 immutable review versions';

CREATE TABLE `service_review_moderation_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `review_id` bigint unsigned NOT NULL,
  `action` varchar(20) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_moderation_key` (`tenant_id`, `idempotency_key`),
  CONSTRAINT `fk_review_moderation_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_review_moderation_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_review_moderation_review` FOREIGN KEY (`review_id`) REFERENCES `service_review` (`id`),
  CONSTRAINT `fk_review_moderation_actor` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_review_moderation_action` CHECK (`action` IN ('APPROVE', 'HIDE', 'REPLY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC6 append-only public review moderation';

ALTER TABLE `after_sale_case`
  ADD COLUMN `origin_type` varchar(30) NOT NULL DEFAULT 'MANUAL' AFTER `category`,
  ADD COLUMN `entry_deadline_at` datetime(3) DEFAULT NULL AFTER `summary`,
  ADD COLUMN `late_create_reason` varchar(500) DEFAULT NULL AFTER `entry_deadline_at`,
  ADD COLUMN `resolution_type` varchar(30) DEFAULT NULL AFTER `status`,
  ADD COLUMN `resolution_note` varchar(500) DEFAULT NULL AFTER `resolution_type`,
  ADD COLUMN `risk_amount` decimal(12,2) NOT NULL DEFAULT 0 AFTER `resolution_note`,
  ADD COLUMN `requires_super_admin` tinyint(1) NOT NULL DEFAULT 0 AFTER `risk_amount`,
  ADD COLUMN `customer_response_due_at` datetime(3) DEFAULT NULL AFTER `requires_super_admin`,
  ADD COLUMN `customer_confirmed_at` datetime(3) DEFAULT NULL AFTER `customer_response_due_at`,
  ADD COLUMN `reopen_count` tinyint unsigned NOT NULL DEFAULT 0 AFTER `customer_confirmed_at`,
  ADD COLUMN `evidence_json` json DEFAULT NULL AFTER `reopen_count`,
  ADD CONSTRAINT `ck_after_sale_origin` CHECK (`origin_type` IN (
    'MANUAL', 'SERVICE_DISPUTE', 'LOW_SCORE_REVIEW', 'CONTACT_REQUEST', 'MALL_RETURN'
  )),
  ADD CONSTRAINT `ck_after_sale_resolution` CHECK (`resolution_type` IS NULL OR `resolution_type` IN (
    'REFUND', 'REDO_SERVICE', 'RESTORE_ENTITLEMENT', 'COMPENSATION_COUPON', 'REJECT'
  )),
  ADD CONSTRAINT `ck_after_sale_reopen_count` CHECK (`reopen_count` <= 1);

CREATE TABLE `after_sale_asset_ledger` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `case_id` bigint unsigned NOT NULL,
  `asset_type` varchar(30) NOT NULL,
  `direction` varchar(10) NOT NULL,
  `amount` decimal(14,2) NOT NULL DEFAULT 0,
  `reference_type` varchar(40) NOT NULL,
  `reference_id` bigint unsigned DEFAULT NULL,
  `business_key` varchar(160) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `evidence_json` json DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_after_sale_asset_business` (`tenant_id`, `business_key`),
  KEY `idx_after_sale_asset_case` (`tenant_id`, `case_id`, `created_at`),
  CONSTRAINT `fk_after_sale_asset_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_after_sale_asset_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_after_sale_asset_case` FOREIGN KEY (`case_id`) REFERENCES `after_sale_case` (`id`),
  CONSTRAINT `fk_after_sale_asset_actor` FOREIGN KEY (`created_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_after_sale_asset_type` CHECK (`asset_type` IN ('CASH', 'POINTS', 'FREIGHT', 'ENTITLEMENT', 'COUPON', 'STOCK')),
  CONSTRAINT `ck_after_sale_asset_direction` CHECK (`direction` IN ('RETURN', 'RESTORE', 'GRANT', 'REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC6 immutable after-sale asset actions';

CREATE TABLE `mall_return_request` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `shop_id` bigint unsigned NOT NULL,
  `member_id` bigint unsigned NOT NULL,
  `mall_order_id` bigint unsigned NOT NULL,
  `after_sale_case_id` bigint unsigned NOT NULL,
  `reason_code` varchar(30) NOT NULL,
  `reason_detail` varchar(500) NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'SUBMITTED',
  `return_tracking_no` varchar(80) DEFAULT NULL,
  `inspection_result` varchar(20) DEFAULT NULL,
  `inspection_reason` varchar(500) DEFAULT NULL,
  `evidence_json` json DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 1,
  `submitted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `inspected_at` datetime(3) DEFAULT NULL,
  `inspected_by` bigint unsigned DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_return_key` (`tenant_id`, `idempotency_key`),
  KEY `idx_mall_return_status` (`tenant_id`, `shop_id`, `status`, `submitted_at`),
  CONSTRAINT `fk_mall_return_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenant` (`id`),
  CONSTRAINT `fk_mall_return_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_mall_return_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_mall_return_order` FOREIGN KEY (`mall_order_id`) REFERENCES `mall_order` (`id`),
  CONSTRAINT `fk_mall_return_case` FOREIGN KEY (`after_sale_case_id`) REFERENCES `after_sale_case` (`id`),
  CONSTRAINT `fk_mall_return_inspector` FOREIGN KEY (`inspected_by`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_mall_return_status` CHECK (`status` IN (
    'SUBMITTED', 'APPROVED', 'RETURNING', 'PENDING_INSPECTION', 'INSPECTION_PASSED', 'INSPECTION_FAILED', 'CLOSED'
  )),
  CONSTRAINT `ck_mall_return_inspection` CHECK (`inspection_result` IS NULL OR `inspection_result` IN ('PASS', 'FAIL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC6 physical return and inspection gate';

CREATE TABLE `mall_return_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `return_request_id` bigint unsigned NOT NULL,
  `mall_order_item_id` bigint unsigned NOT NULL,
  `quantity` int unsigned NOT NULL,
  `stock_disposition` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_return_item` (`return_request_id`, `mall_order_item_id`),
  CONSTRAINT `fk_mall_return_item_request` FOREIGN KEY (`return_request_id`) REFERENCES `mall_return_request` (`id`),
  CONSTRAINT `fk_mall_return_item_order_item` FOREIGN KEY (`mall_order_item_id`) REFERENCES `mall_order_item` (`id`),
  CONSTRAINT `ck_mall_return_item_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `ck_mall_return_disposition` CHECK (`stock_disposition` IS NULL OR `stock_disposition` IN ('RESTORE', 'DAMAGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SC6 return item inspection result';

INSERT INTO `permission_definition`
  (`permission_code`, `permission_name`, `module_code`, `risk_level`)
VALUES
  ('fulfillment:view', '查看履约确认', 'fulfillment', 'SENSITIVE'),
  ('review:moderate', '审核公开评价', 'review', 'SENSITIVE'),
  ('aftersale:asset:execute', '执行售后资产操作', 'aftersale', 'CRITICAL'),
  ('mall:return:inspect', '商城退货验货', 'mall', 'CRITICAL')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `module_code` = VALUES(`module_code`),
  `risk_level` = VALUES(`risk_level`);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role_definition` r
JOIN `permission_definition` p ON p.`permission_code` IN (
  'aftersale:view', 'aftersale:create', 'aftersale:manage',
  'fulfillment:view', 'review:moderate', 'aftersale:asset:execute', 'mall:return:inspect'
)
WHERE r.`role_code` IN ('ADMIN', 'SUPER_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
