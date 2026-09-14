-- The BEAUTICIAN role uses the self-scoped client/technician APIs.
-- It must not authorize access to the chain-management appointment APIs,
-- whose scope is an entire accessible shop.
DELETE rp
FROM role_permission rp
JOIN role_definition rd
  ON rd.id = rp.role_id
JOIN permission_definition pd
  ON pd.id = rp.permission_id
WHERE rd.role_code = 'BEAUTICIAN'
  AND pd.permission_code IN (
    'appointment:view',
    'appointment:manage',
    'dashboard:view'
  );
