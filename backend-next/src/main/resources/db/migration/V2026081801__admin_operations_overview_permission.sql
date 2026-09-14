-- 运营管理平台首页使用真实经营分析数据；所有可见管理角色均需具备只读查看权限。
-- 仅补权限映射，不开放导出权限，也不改变历史订单或账号范围。
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT role.`id`, permission.`id`
FROM `role_definition` role
JOIN `permission_definition` permission
  ON permission.`permission_code` = 'analytics:view'
WHERE role.`role_code` IN ('ADMIN', 'SUPER_ADMIN')
  AND role.`status` = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_permission` existing
    WHERE existing.`role_id` = role.`id`
      AND existing.`permission_id` = permission.`id`
  );
