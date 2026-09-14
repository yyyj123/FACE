param(
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3308 }),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'face_salon' }),
    [string]$UserName = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'face_app' })
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $env:DB_PASSWORD) {
    throw 'DB_PASSWORD is required. The verifier never uses a hard-coded database password.'
}

$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
if (-not (Test-Path -LiteralPath $mysql)) {
    throw "MySQL client not found: $mysql"
}

$connection = Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue
if (-not $connection.TcpTestSucceeded) {
    throw "Database TCP connection failed: $HostName`:$Port"
}

$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:DB_PASSWORD
try {
    $sql = @'
SELECT CONCAT('DB=', DATABASE(), ';VERSION=', VERSION(), ';TZ=', @@session.time_zone) AS connection_status;

SELECT CONCAT('REQUIRED_TABLES=', COUNT(*), '/7') AS required_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'tenant',
    'account_shop_role',
    'appointment',
    'service_record',
    'stock_balance',
    'data_access_log',
    'outbox_event'
  );

SELECT CONCAT('LATEST_FLYWAY=', version, ';SUCCESS=', success) AS flyway_status
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 1;

SELECT CONCAT('PERMISSION_INDEX=', COUNT(*)) AS permission_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'account_shop_role'
  AND index_name = 'idx_account_shop_role_permission_lookup';
'@

    $result = $sql | & $mysql `
        --protocol=TCP `
        --host=$HostName `
        --port=$Port `
        --user=$UserName `
        --database=$Database `
        --batch `
        --skip-column-names

    if ($LASTEXITCODE -ne 0) {
        throw 'Database authentication or verification query failed.'
    }

    $lines = @($result | Where-Object { $_ -and $_.Trim() })
    $lines | ForEach-Object { Write-Output $_ }

    if (-not ($lines -match '^REQUIRED_TABLES=7/7$')) {
        throw 'Required database tables are missing.'
    }
    if (-not ($lines -match ';SUCCESS=1$')) {
        throw 'Latest Flyway migration is not successful.'
    }
    if (-not ($lines -match '^PERMISSION_INDEX=1$')) {
        throw 'Permission lookup index is missing.'
    }

    Write-Output 'DATABASE_VERIFICATION=PASS'
}
finally {
    $env:MYSQL_PWD = $previousPassword
}
