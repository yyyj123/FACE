SET @member_version_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'member'
      AND column_name = 'version'
);
SET @member_version_sql = IF(
    @member_version_exists = 0,
    'ALTER TABLE member ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 0 AFTER status',
    'SELECT 1'
);
PREPARE member_version_statement FROM @member_version_sql;
EXECUTE member_version_statement;
DEALLOCATE PREPARE member_version_statement;

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_definition r
JOIN permission_definition p
  ON p.permission_code IN ('member:view', 'member:manage')
WHERE r.role_code = 'REGIONAL_MANAGER';
