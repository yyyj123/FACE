-- Demo beautician login for the V2 client portal.
-- Username: jishi01
-- Password: Face@123

INSERT INTO `account` (
  `tenant_id`,
  `home_shop_id`,
  `shop_id`,
  `username`,
  `display_name`,
  `password_hash`,
  `role_code`,
  `staff_id`,
  `status`,
  `must_change_password`
)
SELECT
  s.`tenant_id`,
  s.`home_shop_id`,
  s.`home_shop_id`,
  'jishi01',
  s.`name`,
  '$2a$10$vbO0KhzeXY9l3spTnx4jjeUC/OfHqn/FUlBtCA1YsqmTE9QxGExIq',
  'BEAUTICIAN',
  s.`id`,
  'ACTIVE',
  0
FROM `staff` s
WHERE s.`staff_no` = 'S0002'
  AND s.`status` = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM `account` a WHERE a.`username` = 'jishi01'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `account` a WHERE a.`staff_id` = s.`id`
  );

INSERT INTO `account_shop_role` (
  `tenant_id`,
  `account_id`,
  `shop_id`,
  `role_id`,
  `status`
)
SELECT
  a.`tenant_id`,
  a.`id`,
  a.`home_shop_id`,
  r.`id`,
  'ACTIVE'
FROM `account` a
JOIN `role_definition` r
  ON r.`tenant_id` = a.`tenant_id`
 AND r.`role_code` = 'BEAUTICIAN'
WHERE a.`username` = 'jishi01'
  AND NOT EXISTS (
    SELECT 1
    FROM `account_shop_role` ar
    WHERE ar.`account_id` = a.`id`
      AND ar.`shop_id` = a.`home_shop_id`
      AND ar.`role_id` = r.`id`
      AND ar.`status` = 'ACTIVE'
  );
