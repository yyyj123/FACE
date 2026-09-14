param(
    [string]$DatabaseHost = '127.0.0.1',
    [int]$DatabasePort = 3308,
    [string]$DatabaseName = 'face_salon',
    [string]$DatabaseUser = 'face_app',
    [string]$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $MysqlExe)) {
    throw "mysql client not found: $MysqlExe"
}
if (-not $env:DB_PASSWORD) {
    throw 'DB_PASSWORD is required.'
}

$env:MYSQL_PWD = $env:DB_PASSWORD
try {
    $query = @'
SELECT CONCAT('TABLE:', table_name)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'tenant', 'region', 'account_shop_role', 'role_definition',
    'permission_definition', 'role_permission', 'member_shop_profile',
    'member_account', 'member_account_ledger', 'shop_service_price',
    'stock_location', 'stock_balance', 'payment_transaction',
    'refund_transaction', 'idempotency_record'
  )
ORDER BY table_name;

SELECT CONCAT('COLUMN:', table_name, '.', column_name)
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'shop' AND column_name IN ('tenant_id', 'region_id', 'shop_code', 'timezone', 'currency_code'))
    OR (table_name = 'account' AND column_name IN ('tenant_id', 'home_shop_id'))
    OR (table_name = 'member' AND column_name IN ('tenant_id', 'home_shop_id'))
    OR (table_name = 'staff' AND column_name IN ('tenant_id', 'home_shop_id'))
    OR (table_name = 'service_item' AND column_name IN ('tenant_id', 'is_chain_standard'))
    OR (table_name = 'audit_log' AND column_name IN ('tenant_id', 'request_id', 'metadata'))
  )
ORDER BY table_name, column_name;

SELECT CONCAT('NULL_TENANT_ROWS:',
  (SELECT COUNT(*) FROM shop WHERE tenant_id IS NULL) +
  (SELECT COUNT(*) FROM account WHERE tenant_id IS NULL) +
  (SELECT COUNT(*) FROM member WHERE tenant_id IS NULL) +
  (SELECT COUNT(*) FROM staff WHERE tenant_id IS NULL)
);

SELECT CONCAT('TENANTS:', COUNT(*)) FROM tenant;
SELECT CONCAT('SHOP_ROLE_BINDINGS:', COUNT(*)) FROM account_shop_role;
'@

    $output = & $MysqlExe `
        --host=$DatabaseHost `
        --port=$DatabasePort `
        --user=$DatabaseUser `
        --database=$DatabaseName `
        --batch `
        --skip-column-names `
        --execute=$query

    if ($LASTEXITCODE -ne 0) {
        throw "mysql verification failed with exit code $LASTEXITCODE"
    }

    $requiredTables = @(
        'account_shop_role', 'idempotency_record', 'member_account',
        'member_account_ledger', 'member_shop_profile', 'payment_transaction',
        'permission_definition', 'refund_transaction', 'region',
        'role_definition', 'role_permission', 'shop_service_price',
        'stock_balance', 'stock_location', 'tenant'
    )
    $requiredColumns = @(
        'account.home_shop_id', 'account.tenant_id',
        'audit_log.metadata', 'audit_log.request_id', 'audit_log.tenant_id',
        'member.home_shop_id', 'member.tenant_id',
        'service_item.is_chain_standard', 'service_item.tenant_id',
        'shop.currency_code', 'shop.region_id', 'shop.shop_code',
        'shop.tenant_id', 'shop.timezone',
        'staff.home_shop_id', 'staff.tenant_id'
    )

    $actualTables = @(
        $output |
            Where-Object { $_ -like 'TABLE:*' } |
            ForEach-Object { $_.Substring(6) }
    )
    $actualColumns = @(
        $output |
            Where-Object { $_ -like 'COLUMN:*' } |
            ForEach-Object { $_.Substring(7) }
    )

    $missingTables = @($requiredTables | Where-Object { $_ -notin $actualTables })
    $missingColumns = @($requiredColumns | Where-Object { $_ -notin $actualColumns })
    $nullTenantRows = [int](($output | Where-Object { $_ -like 'NULL_TENANT_ROWS:*' }) -replace 'NULL_TENANT_ROWS:', '')
    $tenantCount = [int](($output | Where-Object { $_ -like 'TENANTS:*' }) -replace 'TENANTS:', '')
    $roleBindingCount = [int](($output | Where-Object { $_ -like 'SHOP_ROLE_BINDINGS:*' }) -replace 'SHOP_ROLE_BINDINGS:', '')

    if ($missingTables.Count -gt 0) {
        throw "Missing chain tables: $($missingTables -join ', ')"
    }
    if ($missingColumns.Count -gt 0) {
        throw "Missing tenant columns: $($missingColumns -join ', ')"
    }
    if ($nullTenantRows -ne 0) {
        throw "Tenant backfill incomplete: $nullTenantRows rows have no tenant_id"
    }
    if ($tenantCount -lt 1) {
        throw 'No tenant record exists.'
    }
    if ($roleBindingCount -lt 1) {
        throw 'No account-to-shop role binding exists.'
    }

    [pscustomobject]@{
        Status = 'PASS'
        TenantCount = $tenantCount
        ShopRoleBindings = $roleBindingCount
        RequiredTables = $requiredTables.Count
        RequiredColumns = $requiredColumns.Count
        NullTenantRows = $nullTenantRows
    } | Format-List
}
finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
