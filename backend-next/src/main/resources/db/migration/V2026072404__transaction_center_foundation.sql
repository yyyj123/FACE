ALTER TABLE `sales_order`
  ADD COLUMN `refunded_amount` decimal(12,2) NOT NULL DEFAULT 0.00 AFTER `paid_amount`,
  ADD COLUMN `version` int unsigned NOT NULL DEFAULT 0 AFTER `status`,
  ADD COLUMN `notes` varchar(500) DEFAULT NULL AFTER `version`,
  ADD COLUMN `updated_by` bigint unsigned DEFAULT NULL AFTER `created_by`,
  ADD UNIQUE KEY `uk_sales_order_appointment` (`appointment_id`),
  ADD KEY `idx_sales_order_tenant_shop_status` (`tenant_id`, `shop_id`, `status`, `business_date`),
  ADD CONSTRAINT `fk_sales_order_updater`
    FOREIGN KEY (`updated_by`) REFERENCES `account` (`id`);

ALTER TABLE `sales_order`
  DROP CHECK `ck_sales_order_status`,
  ADD CONSTRAINT `ck_sales_order_status`
    CHECK (`status` IN (
      'UNPAID', 'PARTIALLY_PAID', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED', 'VOID'
    )),
  ADD CONSTRAINT `ck_sales_order_refund_amount`
    CHECK (`refunded_amount` >= 0 AND `refunded_amount` <= `paid_amount`);

ALTER TABLE `payment_transaction`
  ADD COLUMN `refunded_amount` decimal(14,2) NOT NULL DEFAULT 0.00 AFTER `amount`,
  ADD CONSTRAINT `ck_payment_refunded_amount`
    CHECK (`refunded_amount` >= 0 AND `refunded_amount` <= `amount`);

ALTER TABLE `refund_transaction`
  ADD COLUMN `decision_note` varchar(500) DEFAULT NULL AFTER `reason`,
  ADD COLUMN `reviewed_at` datetime(3) DEFAULT NULL AFTER `approved_by`,
  ADD COLUMN `version` int unsigned NOT NULL DEFAULT 0 AFTER `status`;

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role_definition` r
JOIN `permission_definition` p
  ON p.permission_code IN ('order:view', 'finance:view')
WHERE r.role_code = 'REGIONAL_MANAGER'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role_definition` r
JOIN `permission_definition` p ON p.permission_code = 'refund:approve'
WHERE r.role_code = 'MANAGER'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO `product_category` (
  `tenant_id`, `shop_id`, `name`, `sort_order`, `status`
)
SELECT 1, 1, px.peijianzhonglei, 100 + ROW_NUMBER() OVER (ORDER BY px.peijianzhonglei), 'ACTIVE'
FROM (
  SELECT DISTINCT peijianzhonglei
  FROM peijianxinxi
  WHERE peijianzhonglei IS NOT NULL AND peijianzhonglei <> ''
) px
WHERE NOT EXISTS (
  SELECT 1
  FROM product_category pc
  WHERE pc.tenant_id = 1
    AND pc.shop_id = 1
    AND pc.name COLLATE utf8mb4_unicode_ci = px.peijianzhonglei COLLATE utf8mb4_unicode_ci
);

INSERT INTO `product` (
  `tenant_id`, `shop_id`, `category_id`, `sku`, `name`, `brand_name`,
  `unit_name`, `cost_price`, `sale_price`, `stock_quantity`,
  `warning_quantity`, `is_consumable`, `status`
)
SELECT
  1,
  1,
  pc.id,
  px.peijianbianhao,
  px.peijianmingcheng,
  px.pinpai,
  '件',
  0.00,
  COALESCE(px.shoujia, 0.00),
  px.shuliang,
  5.000,
  0,
  'ACTIVE'
FROM peijianxinxi px
JOIN product_category pc
  ON pc.tenant_id = 1
 AND pc.shop_id = 1
 AND pc.name COLLATE utf8mb4_unicode_ci = px.peijianzhonglei COLLATE utf8mb4_unicode_ci
WHERE NOT EXISTS (
  SELECT 1
  FROM product p
  WHERE p.shop_id = 1
    AND p.sku COLLATE utf8mb4_unicode_ci = px.peijianbianhao COLLATE utf8mb4_unicode_ci
);

INSERT INTO `sales_order` (
  `tenant_id`, `shop_id`, `order_no`, `business_date`, `member_id`,
  `subtotal_amount`, `discount_amount`, `payable_amount`, `paid_amount`,
  `refunded_amount`, `currency_code`, `payment_method`, `status`,
  `version`, `notes`, `paid_at`, `created_by`, `updated_by`, `created_at`
)
SELECT
  1,
  1,
  w.weixiubianhao,
  DATE(w.weixiushijian),
  m.id,
  CAST(w.zongjia AS DECIMAL(12,2)),
  0.00,
  CAST(w.zongjia AS DECIMAL(12,2)),
  CAST(w.zongjia AS DECIMAL(12,2)),
  0.00,
  'CNY',
  'LEGACY',
  CASE WHEN w.ispay = '已支付' THEN 'PAID' ELSE 'UNPAID' END,
  0,
  CONCAT('由旧服务记录迁移，原记录ID：', w.id),
  CASE WHEN w.ispay = '已支付' THEN w.weixiushijian ELSE NULL END,
  a.id,
  a.id,
  w.weixiushijian
FROM weixiujilu w
JOIN member m
  ON m.tenant_id = 1
 AND m.member_no COLLATE utf8mb4_unicode_ci = w.zhanghao COLLATE utf8mb4_unicode_ci
JOIN account a
  ON a.tenant_id = 1
 AND a.username = 'admin'
WHERE NOT EXISTS (
  SELECT 1
  FROM sales_order so
  WHERE so.tenant_id = 1
    AND so.order_no COLLATE utf8mb4_unicode_ci = w.weixiubianhao COLLATE utf8mb4_unicode_ci
);

INSERT INTO `sales_order_item` (
  `order_id`, `item_type`, `service_id`, `product_id`,
  `item_name_snapshot`, `quantity`, `unit_price`, `discount_amount`, `line_amount`
)
SELECT
  so.id,
  'SERVICE',
  si.id,
  NULL,
  w.fuwumingcheng,
  1.000,
  CAST(w.jiage AS DECIMAL(10,2)),
  0.00,
  CAST(w.jiage AS DECIMAL(12,2))
FROM weixiujilu w
JOIN sales_order so
  ON so.tenant_id = 1
 AND so.order_no COLLATE utf8mb4_unicode_ci = w.weixiubianhao COLLATE utf8mb4_unicode_ci
JOIN service_item si
  ON si.tenant_id = 1
 AND si.shop_id = 1
 AND si.name COLLATE utf8mb4_unicode_ci = w.fuwumingcheng COLLATE utf8mb4_unicode_ci
WHERE NOT EXISTS (
  SELECT 1
  FROM sales_order_item soi
  WHERE soi.order_id = so.id AND soi.item_type = 'SERVICE'
);

INSERT INTO `sales_order_item` (
  `order_id`, `item_type`, `service_id`, `product_id`,
  `item_name_snapshot`, `quantity`, `unit_price`, `discount_amount`, `line_amount`
)
SELECT
  so.id,
  'PRODUCT',
  NULL,
  p.id,
  w.peijianmingcheng,
  1.000,
  CAST(w.allshoujia AS DECIMAL(10,2)),
  0.00,
  CAST(w.allshoujia AS DECIMAL(12,2))
FROM weixiujilu w
JOIN sales_order so
  ON so.tenant_id = 1
 AND so.order_no COLLATE utf8mb4_unicode_ci = w.weixiubianhao COLLATE utf8mb4_unicode_ci
JOIN product p
  ON p.tenant_id = 1
 AND p.shop_id = 1
 AND p.name COLLATE utf8mb4_unicode_ci = w.peijianmingcheng COLLATE utf8mb4_unicode_ci
WHERE w.peijianmingcheng IS NOT NULL
  AND w.peijianmingcheng <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM sales_order_item soi
    WHERE soi.order_id = so.id AND soi.item_type = 'PRODUCT'
  );

INSERT INTO `payment_transaction` (
  `tenant_id`, `shop_id`, `order_id`, `payment_no`, `payment_method`,
  `amount`, `refunded_amount`, `currency_code`, `external_transaction_no`,
  `idempotency_key`, `status`, `paid_at`, `created_by`, `created_at`
)
SELECT
  1,
  1,
  so.id,
  CONCAT('LEGACY-PAY-', w.weixiubianhao),
  'LEGACY',
  CAST(w.zongjia AS DECIMAL(14,2)),
  0.00,
  'CNY',
  w.weixiubianhao,
  CONCAT('LEGACY-PAY-', w.id),
  'SUCCESS',
  w.weixiushijian,
  a.id,
  w.weixiushijian
FROM weixiujilu w
JOIN sales_order so
  ON so.tenant_id = 1
 AND so.order_no COLLATE utf8mb4_unicode_ci = w.weixiubianhao COLLATE utf8mb4_unicode_ci
JOIN account a
  ON a.tenant_id = 1
 AND a.username = 'admin'
WHERE w.ispay = '已支付'
  AND NOT EXISTS (
    SELECT 1
    FROM payment_transaction pt
    WHERE pt.tenant_id = 1
      AND pt.idempotency_key = CONCAT('LEGACY-PAY-', w.id)
  );

UPDATE member_account ma
JOIN member m
  ON m.id = ma.member_id
 AND m.tenant_id = ma.tenant_id
SET ma.balance = 1000.00,
    ma.version = ma.version + 1
WHERE ma.tenant_id = 1
  AND m.member_no = 'M0001'
  AND ma.account_type = 'BALANCE'
  AND NOT EXISTS (
    SELECT 1
    FROM member_account_ledger mal
    WHERE mal.tenant_id = ma.tenant_id
      AND mal.idempotency_key = 'OPENING-BALANCE-M0001'
  );

INSERT INTO member_account_ledger (
  tenant_id, account_id, shop_id, entry_type, amount_delta, balance_after,
  reference_type, reference_id, idempotency_key, remark, created_by
)
SELECT
  ma.tenant_id,
  ma.id,
  1,
  'OPENING_BALANCE',
  1000.00,
  ma.balance,
  'MIGRATION',
  NULL,
  'OPENING-BALANCE-M0001',
  '演示会员期初余额',
  a.id
FROM member_account ma
JOIN member m
  ON m.id = ma.member_id
 AND m.tenant_id = ma.tenant_id
JOIN account a
  ON a.tenant_id = ma.tenant_id
 AND a.username = 'admin'
WHERE ma.tenant_id = 1
  AND m.member_no = 'M0001'
  AND ma.account_type = 'BALANCE'
  AND NOT EXISTS (
    SELECT 1
    FROM member_account_ledger mal
    WHERE mal.tenant_id = ma.tenant_id
      AND mal.idempotency_key = 'OPENING-BALANCE-M0001'
  );
