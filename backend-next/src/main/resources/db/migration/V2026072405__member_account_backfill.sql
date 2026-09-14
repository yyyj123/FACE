INSERT INTO member_account (
  tenant_id, member_id, account_type, currency_code, balance, version, status
)
SELECT
  m.tenant_id,
  m.id,
  account_type.account_type,
  'CNY',
  0.00,
  0,
  'ACTIVE'
FROM member m
CROSS JOIN (
  SELECT 'BALANCE' AS account_type
  UNION ALL SELECT 'GIFT_BALANCE'
  UNION ALL SELECT 'POINTS'
) account_type
WHERE NOT EXISTS (
  SELECT 1
  FROM member_account ma
  WHERE ma.tenant_id = m.tenant_id
    AND ma.member_id = m.id
    AND ma.account_type = account_type.account_type
);

UPDATE member_account ma
JOIN member m
  ON m.id = ma.member_id
 AND m.tenant_id = ma.tenant_id
SET ma.balance = ma.balance + 1000.00,
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
  m.home_shop_id,
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
