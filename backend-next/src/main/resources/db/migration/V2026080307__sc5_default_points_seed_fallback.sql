INSERT INTO `points_rule` (
  `tenant_id`, `shop_id`, `rule_code`, `rule_type`, `target_type`,
  `points_per_yuan`, `points_per_currency`, `minimum_points`, `step_points`,
  `max_discount_ratio`, `max_discount_amount`, `validity_months`, `status`, `created_by`
)
SELECT t.`id`, NULL, 'GLOBAL_EARN', 'EARN', 'GLOBAL', 1, 100, 100, 100, 50, 100, 12, 'ACTIVE', a.`id`
FROM `tenant` t
JOIN `account` a ON a.`id` = (
  SELECT MIN(a2.`id`) FROM `account` a2
  WHERE a2.`tenant_id` = t.`id` AND a2.`role_code` IN ('SUPER_ADMIN', 'ADMIN')
)
ON DUPLICATE KEY UPDATE `rule_code` = VALUES(`rule_code`);

INSERT INTO `points_rule` (
  `tenant_id`, `shop_id`, `rule_code`, `rule_type`, `target_type`,
  `points_per_yuan`, `points_per_currency`, `minimum_points`, `step_points`,
  `max_discount_ratio`, `max_discount_amount`, `validity_months`, `status`, `created_by`
)
SELECT t.`id`, NULL, 'GLOBAL_REDEEM', 'REDEEM', 'GLOBAL', 1, 100, 100, 100, 50, 100, 12, 'ACTIVE', a.`id`
FROM `tenant` t
JOIN `account` a ON a.`id` = (
  SELECT MIN(a2.`id`) FROM `account` a2
  WHERE a2.`tenant_id` = t.`id` AND a2.`role_code` IN ('SUPER_ADMIN', 'ADMIN')
)
ON DUPLICATE KEY UPDATE `rule_code` = VALUES(`rule_code`);

INSERT INTO `points_task` (
  `tenant_id`, `shop_id`, `task_code`, `task_type`, `name`, `reward_points`,
  `cycle_days`, `daily_rewards_json`, `cycle_bonus_points`, `repeat_cycle`,
  `status`, `created_by`
)
SELECT t.`id`, NULL, 'DAILY_CHECKIN', 'CHECKIN', '每日签到', 10,
       7, JSON_ARRAY(10, 10, 15, 15, 20, 20, 30), 50, 1, 'ACTIVE', a.`id`
FROM `tenant` t
JOIN `account` a ON a.`id` = (
  SELECT MIN(a2.`id`) FROM `account` a2
  WHERE a2.`tenant_id` = t.`id` AND a2.`role_code` IN ('SUPER_ADMIN', 'ADMIN')
)
ON DUPLICATE KEY UPDATE `task_code` = VALUES(`task_code`);
