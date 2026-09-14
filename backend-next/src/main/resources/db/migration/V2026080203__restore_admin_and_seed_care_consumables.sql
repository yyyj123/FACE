-- Restore the local owner account used by this acceptance environment.
-- The existing bcrypt hash remains unchanged (Face@123 in the local demo data).
UPDATE account
SET status = 'ACTIVE',
    must_change_password = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE tenant_id = 1
  AND username = 'admin'
  AND role_code = 'OWNER';

-- The migrated legacy inventory is empty in this demo database. Seed a practical
-- care-room assortment so technicians can record real consumptions and every
-- deduction starts from an auditable opening balance.
INSERT INTO product (
  tenant_id, shop_id, category_id, sku, name, brand_name, unit_name,
  cost_price, sale_price, stock_quantity, warning_quantity,
  is_consumable, status
)
SELECT 1, 1, pc.id, seed.sku, seed.name, 'FACE 专业线', seed.unit_name,
       seed.cost_price, 0, seed.opening_quantity, seed.warning_quantity,
       1, 'ACTIVE'
FROM product_category pc
JOIN (
  SELECT 'FAC-CONS-001' AS sku, '洁面棉柔巾' AS name, '片' AS unit_name,
         0.50 AS cost_price, 200.000 AS opening_quantity, 40.000 AS warning_quantity
  UNION ALL SELECT 'FAC-CONS-002', '医用无菌棉片', '片', 0.20, 300.000, 60.000
  UNION ALL SELECT 'FAC-CONS-003', '一次性面膜布', '张', 1.20, 120.000, 24.000
  UNION ALL SELECT 'FAC-CONS-004', '玻尿酸补水精华', '支', 12.00, 80.000, 16.000
  UNION ALL SELECT 'FAC-CONS-005', '舒缓修护凝胶', '支', 10.00, 60.000, 12.000
  UNION ALL SELECT 'FAC-CONS-006', '一次性美容床单', '张', 2.50, 100.000, 20.000
  UNION ALL SELECT 'FAC-CONS-007', '丁腈护理手套', '双', 1.00, 150.000, 30.000
  UNION ALL SELECT 'FAC-CONS-008', '皮肤消毒湿巾', '片', 0.60, 240.000, 48.000
  UNION ALL SELECT 'FAC-CONS-009', '海藻软膜粉', '袋', 8.00, 90.000, 18.000
  UNION ALL SELECT 'FAC-CONS-010', '活性炭软膜粉', '袋', 9.00, 70.000, 14.000
) seed
  ON 1 = 1
WHERE pc.tenant_id = 1
  AND pc.shop_id = 1
  AND pc.name = '美容耗材'
  AND NOT EXISTS (
    SELECT 1
    FROM product existing
    WHERE existing.shop_id = 1
      AND existing.sku = seed.sku
  );

INSERT INTO stock_balance (
  tenant_id, location_id, product_id,
  quantity_on_hand, quantity_reserved, version
)
SELECT p.tenant_id, sl.id, p.id, p.stock_quantity, 0, 0
FROM product p
JOIN stock_location sl
  ON sl.tenant_id = p.tenant_id
 AND sl.shop_id = p.shop_id
 AND sl.location_type = 'ROOM'
 AND sl.status = 'ACTIVE'
WHERE p.tenant_id = 1
  AND p.shop_id = 1
  AND p.sku LIKE 'FAC-CONS-%'
  AND NOT EXISTS (
    SELECT 1
    FROM stock_balance sb
    WHERE sb.location_id = sl.id
      AND sb.product_id = p.id
  );

INSERT INTO inventory_movement (
  tenant_id, shop_id, location_id, product_id,
  movement_type, quantity_delta, balance_after,
  reference_no, idempotency_key, remark,
  created_by, created_at
)
SELECT p.tenant_id, p.shop_id, sl.id, p.id,
       'INITIAL_BALANCE', sb.quantity_on_hand, sb.quantity_on_hand,
       'CARE-CONSUMABLES-20260802',
       CONCAT('CARE-CONSUMABLE-OPENING-', p.id),
       '技师护理耗材期初库存',
       a.id, CURRENT_TIMESTAMP(3)
FROM product p
JOIN stock_location sl
  ON sl.tenant_id = p.tenant_id
 AND sl.shop_id = p.shop_id
 AND sl.location_type = 'ROOM'
JOIN stock_balance sb
  ON sb.location_id = sl.id
 AND sb.product_id = p.id
JOIN account a
  ON a.tenant_id = p.tenant_id
 AND a.username = 'admin'
WHERE p.tenant_id = 1
  AND p.shop_id = 1
  AND p.sku LIKE 'FAC-CONS-%'
  AND sb.quantity_on_hand > 0
  AND NOT EXISTS (
    SELECT 1
    FROM inventory_movement im
    WHERE im.tenant_id = p.tenant_id
      AND im.idempotency_key = CONCAT('CARE-CONSUMABLE-OPENING-', p.id)
  );

