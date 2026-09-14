-- Service completion is the settlement boundary: completed care records must have
-- exactly one unpaid order so the front desk can collect payment without re-entry.
INSERT INTO sales_order (
    tenant_id,
    shop_id,
    order_no,
    create_idempotency_key,
    create_request_hash,
    business_date,
    member_id,
    appointment_id,
    service_record_id,
    subtotal_amount,
    discount_amount,
    payable_amount,
    paid_amount,
    refunded_amount,
    currency_code,
    status,
    version,
    notes,
    created_by,
    updated_by
)
SELECT
    sr.tenant_id,
    sr.shop_id,
    CONCAT('SO-AUTO-', sr.record_no),
    CONCAT('service-completion-order:', sr.id),
    SHA2(CONCAT(sr.tenant_id, ':', sr.shop_id, ':', sr.appointment_id, ':', sr.member_id), 256),
    DATE(COALESCE(sr.actual_end_at, sr.actual_start_at)),
    sr.member_id,
    sr.appointment_id,
    sr.id,
    SUM(sri.price_snapshot),
    0,
    SUM(sri.price_snapshot),
    0,
    0,
    'CNY',
    'UNPAID',
    0,
    '历史已完成服务自动补齐的待收款订单',
    sr.updated_by,
    sr.updated_by
FROM service_record sr
JOIN service_record_item sri ON sri.service_record_id = sr.id
LEFT JOIN sales_order existing
  ON existing.tenant_id = sr.tenant_id
 AND (existing.appointment_id = sr.appointment_id OR existing.service_record_id = sr.id)
WHERE sr.status = 'COMPLETED'
  AND sr.appointment_id IS NOT NULL
  AND existing.id IS NULL
GROUP BY
    sr.id,
    sr.tenant_id,
    sr.shop_id,
    sr.record_no,
    sr.appointment_id,
    sr.member_id,
    sr.actual_end_at,
    sr.actual_start_at,
    sr.updated_by
HAVING SUM(sri.price_snapshot) > 0;

INSERT INTO sales_order_item (
    order_id,
    item_type,
    service_id,
    product_id,
    package_product_id,
    item_name_snapshot,
    category_id_snapshot,
    category_name_snapshot,
    brand_name_snapshot,
    dimension_snapshot_quality,
    quantity,
    unit_price,
    discount_amount,
    line_amount
)
SELECT
    so.id,
    'SERVICE',
    sri.service_id,
    NULL,
    NULL,
    sri.service_name_snapshot,
    si.category_id,
    sc.name,
    NULL,
    CASE WHEN si.category_id IS NULL THEN 'MISSING' ELSE 'TRANSACTION_TIME' END,
    1,
    sri.price_snapshot,
    0,
    sri.price_snapshot
FROM sales_order so
JOIN service_record sr ON sr.id = so.service_record_id
JOIN service_record_item sri ON sri.service_record_id = sr.id
LEFT JOIN service_item si ON si.id = sri.service_id
LEFT JOIN service_category sc ON sc.id = si.category_id
LEFT JOIN sales_order_item existing_item
  ON existing_item.order_id = so.id
 AND existing_item.service_id = sri.service_id
WHERE so.create_idempotency_key = CONCAT('service-completion-order:', sr.id)
  AND existing_item.id IS NULL;
