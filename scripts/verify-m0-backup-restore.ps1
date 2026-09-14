param(
    [int]$DatabasePort = 3320,
    [int]$BackendPort = 8192,
    [string]$BackupDirectory = 'E:\face\backups\m0'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath('E:\face')
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0'
$mysqldExe = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysqlExe = Join-Path $mysqlHome 'bin\mysql.exe'
$mysqldumpExe = Join-Path $mysqlHome 'bin\mysqldump.exe'
$mavenExe = (Get-Command 'mvn.cmd').Source

foreach ($required in @($mysqldExe, $mysqlExe, $mysqldumpExe, $mavenExe)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required executable missing: $required"
    }
}

$taskRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot ('tmp\m0-backup-restore-' + [guid]::NewGuid().ToString('N')))
)
$allowedTempRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'tmp')) +
    [System.IO.Path]::DirectorySeparatorChar
if (-not $taskRoot.StartsWith($allowedTempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe task root: $taskRoot"
}

$resolvedBackupDirectory = [System.IO.Path]::GetFullPath($BackupDirectory)
$allowedBackupRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'backups')) +
    [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedBackupDirectory.StartsWith($allowedBackupRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Backup directory must be inside E:\face\backups: $resolvedBackupDirectory"
}

$dataDirectory = Join-Path $taskRoot 'mysql-data'
$configPath = Join-Path $taskRoot 'my.ini'
$mysqlLog = Join-Path $taskRoot 'mysql.log'
$backendOut = Join-Path $taskRoot 'backend.out.log'
$backendErr = Join-Path $taskRoot 'backend.err.log'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dumpPath = Join-Path $resolvedBackupDirectory "face-salon-m0-synthetic-$timestamp.sql"
$dumpHashPath = Join-Path $resolvedBackupDirectory "face-salon-m0-synthetic-$timestamp.sha256"
$summaryPath = Join-Path $resolvedBackupDirectory "face-salon-m0-recovery-$timestamp.txt"
$mysqlProcess = $null
$backendProcess = $null
$temporaryPassword = $null
$previousMysqlPassword = $env:MYSQL_PWD
$previousDbPassword = $env:DB_PASSWORD
$previousDbUrl = $env:DB_URL
$previousDbUsername = $env:DB_USERNAME
$previousServerPort = $env:SERVER_PORT
$primaryError = $null

function Test-LocalPort {
    param([int]$Port)
    return [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().
        GetActiveTcpListeners().
        Port -contains $Port
}

function Wait-LocalPort {
    param(
        [int]$Port,
        [bool]$ExpectedOpen,
        [int]$Attempts = 120
    )
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        if ((Test-LocalPort -Port $Port) -eq $ExpectedOpen) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Port $Port did not reach expected open state: $ExpectedOpen"
}

function Invoke-MySql {
    param(
        [string]$Sql,
        [string]$Database
    )
    $arguments = @(
        '--protocol=TCP',
        '--host=127.0.0.1',
        "--port=$DatabasePort",
        '--user=root',
        '--batch',
        '--skip-column-names',
        '--default-character-set=utf8mb4'
    )
    if ($Database) {
        $arguments += $Database
    }
    $result = $Sql | & $mysqlExe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL command failed.'
    }
    return @($result | Where-Object { $_ -and $_.Trim() })
}

function Start-Backend {
    param([string]$DatabaseName)
    $env:SERVER_PORT = [string]$BackendPort
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$DatabasePort/${DatabaseName}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    $env:DB_USERNAME = 'root'
    $env:DB_PASSWORD = $temporaryPassword

    $process = Start-Process `
        -FilePath $mavenExe `
        -ArgumentList @('-q', 'spring-boot:run') `
        -WorkingDirectory (Join-Path $repoRoot 'backend-next') `
        -WindowStyle Hidden `
        -RedirectStandardOutput $backendOut `
        -RedirectStandardError $backendErr `
        -PassThru
    Wait-LocalPort -Port $BackendPort -ExpectedOpen $true -Attempts 180
    return $process
}

function Stop-Backend {
    param($Process)
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    }
    $listeners = @(
        Get-NetTCPConnection `
            -LocalPort $BackendPort `
            -State Listen `
            -ErrorAction SilentlyContinue
    )
    foreach ($listener in $listeners) {
        $listenerProcess = Get-CimInstance `
            Win32_Process `
            -Filter "ProcessId = $($listener.OwningProcess)" `
            -ErrorAction SilentlyContinue
        if (
            $null -ne $listenerProcess `
            -and $listenerProcess.CommandLine -like '*FaceChainPlatformApplication*'
        ) {
            Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
    Wait-LocalPort -Port $BackendPort -ExpectedOpen $false -Attempts 60
}

try {
    if (Test-LocalPort -Port $DatabasePort) {
        throw "Database port $DatabasePort is already in use."
    }
    if (Test-LocalPort -Port $BackendPort) {
        throw "Backend port $BackendPort is already in use."
    }

    New-Item -ItemType Directory -Path $taskRoot, $dataDirectory, $resolvedBackupDirectory -Force | Out-Null

    $config = @"
[mysqld]
basedir=$($mysqlHome.Replace('\', '/'))
datadir=$($dataDirectory.Replace('\', '/'))
port=$DatabasePort
bind-address=127.0.0.1
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-time-zone=+08:00
log-error=$($mysqlLog.Replace('\', '/'))
secure-file-priv=
"@
    [System.IO.File]::WriteAllText(
        $configPath,
        $config,
        [System.Text.UTF8Encoding]::new($false)
    )

    $initialize = Start-Process `
        -FilePath $mysqldExe `
        -ArgumentList @(
            "--defaults-file=$configPath",
            '--initialize-insecure',
            '--console'
        ) `
        -WorkingDirectory $mysqlHome `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $taskRoot 'initialize.out.log') `
        -RedirectStandardError (Join-Path $taskRoot 'initialize.err.log') `
        -Wait `
        -PassThru
    if ($initialize.ExitCode -ne 0) {
        throw 'Temporary MySQL initialization failed.'
    }

    $mysqlProcess = Start-Process `
        -FilePath $mysqldExe `
        -ArgumentList "--defaults-file=$configPath" `
        -WorkingDirectory $mysqlHome `
        -WindowStyle Hidden `
        -PassThru
    Wait-LocalPort -Port $DatabasePort -ExpectedOpen $true -Attempts 120

    & $mysqlExe `
        --protocol=TCP `
        --host=127.0.0.1 `
        --port=$DatabasePort `
        --user=root `
        --skip-password `
        --default-character-set=utf8mb4 `
        --execute='SOURCE E:/face/docs/database/face_salon_mysql8.sql'
    if ($LASTEXITCODE -ne 0) {
        throw 'Base schema load failed.'
    }

    $temporaryPassword = [guid]::NewGuid().ToString('N') + 'Aa1!'
    & $mysqlExe `
        --protocol=TCP `
        --host=127.0.0.1 `
        --port=$DatabasePort `
        --user=root `
        --skip-password `
        --execute="ALTER USER 'root'@'localhost' IDENTIFIED BY '$temporaryPassword'"
    if ($LASTEXITCODE -ne 0) {
        throw 'Temporary root password setup failed.'
    }
    $env:MYSQL_PWD = $temporaryPassword

    $backendProcess = Start-Backend -DatabaseName 'face_salon'
    $health = Invoke-RestMethod `
        -Uri "http://127.0.0.1:$BackendPort/face-next/actuator/health/readiness" `
        -TimeoutSec 15
    if ($health.status -ne 'UP') {
        throw 'Source database readiness is not UP.'
    }

    $markerSql = @"
INSERT INTO data_access_log (
  tenant_id, shop_id, account_id, resource_type, resource_id,
  action, result, request_id, ip_address
) VALUES (
  1, 1, 1, 'M0_SYNTHETIC_FIXTURE', 'M0-01',
  'VIEW', 'ALLOWED', 'm0-synthetic-request', '127.0.0.1'
);
"@
    Invoke-MySql -Sql $markerSql -Database 'face_salon' | Out-Null

    $sourceSummarySql = @'
SELECT CONCAT('LATEST=', version, ';SUCCESS=', success)
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 1;
SELECT CONCAT('TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
SELECT CONCAT('tenant=', COUNT(*)) FROM tenant;
SELECT CONCAT('shop=', COUNT(*)) FROM shop;
SELECT CONCAT('member=', COUNT(*)) FROM member;
SELECT CONCAT('staff=', COUNT(*)) FROM staff;
SELECT CONCAT('appointment=', COUNT(*)) FROM appointment;
SELECT CONCAT('service_record=', COUNT(*)) FROM service_record;
SELECT CONCAT('stock_balance=', COUNT(*)) FROM stock_balance;
SELECT CONCAT('sales_order=', COUNT(*)) FROM sales_order;
SELECT CONCAT('data_access_log=', COUNT(*)) FROM data_access_log;
SELECT CONCAT('outbox_event=', COUNT(*)) FROM outbox_event;
'@
    $sourceSummary = Invoke-MySql -Sql $sourceSummarySql -Database 'face_salon'

    $dumpArguments = @(
        '--protocol=TCP',
        '--host=127.0.0.1',
        "--port=$DatabasePort",
        '--user=root',
        '--single-transaction',
        '--routines',
        '--triggers',
        '--events',
        '--hex-blob',
        '--default-character-set=utf8mb4',
        '--set-gtid-purged=OFF',
        'face_salon'
    )
    $dump = Start-Process `
        -FilePath $mysqldumpExe `
        -ArgumentList $dumpArguments `
        -WorkingDirectory $mysqlHome `
        -WindowStyle Hidden `
        -RedirectStandardOutput $dumpPath `
        -RedirectStandardError (Join-Path $taskRoot 'dump.err.log') `
        -Wait `
        -PassThru
    if ($dump.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $dumpPath)) {
        throw 'Database backup failed.'
    }
    if ((Get-Item -LiteralPath $dumpPath).Length -le 0) {
        throw 'Database backup is empty.'
    }

    Invoke-MySql -Sql @'
DROP DATABASE IF EXISTS face_salon_restore;
CREATE DATABASE face_salon_restore
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
'@ -Database '' | Out-Null

    $restore = Start-Process `
        -FilePath $mysqlExe `
        -ArgumentList @(
            '--protocol=TCP',
            '--host=127.0.0.1',
            "--port=$DatabasePort",
            '--user=root',
            '--default-character-set=utf8mb4',
            'face_salon_restore'
        ) `
        -WorkingDirectory $mysqlHome `
        -WindowStyle Hidden `
        -RedirectStandardInput $dumpPath `
        -RedirectStandardOutput (Join-Path $taskRoot 'restore.out.log') `
        -RedirectStandardError (Join-Path $taskRoot 'restore.err.log') `
        -Wait `
        -PassThru
    if ($restore.ExitCode -ne 0) {
        throw 'Database restore failed.'
    }

    $restoreSummary = Invoke-MySql -Sql $sourceSummarySql -Database 'face_salon_restore'
    if (($sourceSummary -join "`n") -ne ($restoreSummary -join "`n")) {
        Write-Output 'SOURCE_SUMMARY:'
        $sourceSummary | ForEach-Object { Write-Output $_ }
        Write-Output 'RESTORE_SUMMARY:'
        $restoreSummary | ForEach-Object { Write-Output $_ }
        throw 'Source and restored database summaries differ.'
    }

    $markerCount = @(Invoke-MySql -Sql @'
SELECT COUNT(*)
FROM data_access_log
WHERE resource_type = 'M0_SYNTHETIC_FIXTURE'
  AND resource_id = 'M0-01'
  AND request_id = 'm0-synthetic-request';
'@ -Database 'face_salon_restore')
    if ($markerCount.Count -ne 1 -or $markerCount[0] -ne '1') {
        throw 'Synthetic M0 marker did not survive restore.'
    }

    Stop-Backend -Process $backendProcess
    $backendProcess = $null
    $backendProcess = Start-Backend -DatabaseName 'face_salon_restore'
    $restoreHealth = Invoke-RestMethod `
        -Uri "http://127.0.0.1:$BackendPort/face-next/actuator/health/readiness" `
        -TimeoutSec 15
    if ($restoreHealth.status -ne 'UP') {
        throw 'Restored database readiness is not UP.'
    }

    $dumpHash = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash
    [System.IO.File]::WriteAllText(
        $dumpHashPath,
        "$dumpHash  $([System.IO.Path]::GetFileName($dumpPath))`r`n",
        [System.Text.UTF8Encoding]::new($false)
    )
    $summary = @(
        'M0 development/isolated backup recovery evidence',
        "verified_at=$(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK')",
        "database_port=$DatabasePort",
        "backend_port=$BackendPort",
        "dump_file=$([System.IO.Path]::GetFileName($dumpPath))",
        "dump_sha256=$dumpHash",
        'source_readiness=UP',
        'restore_readiness=UP',
        'synthetic_marker=1',
        ($sourceSummary -join ';')
    )
    [System.IO.File]::WriteAllLines(
        $summaryPath,
        $summary,
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Output "BACKUP_FILE=$dumpPath"
    Write-Output "BACKUP_SHA256=$dumpHash"
    $sourceSummary | ForEach-Object { Write-Output "SOURCE_$_" }
    Write-Output 'RESTORE_SUMMARY_MATCH=PASS'
    Write-Output 'RESTORE_READINESS=UP'
    Write-Output 'SYNTHETIC_MARKER_RESTORED=PASS'
    Write-Output 'M0_BACKUP_RESTORE=PASS'
}
catch {
    $primaryError = $_
    Write-Output ("M0_BACKUP_RESTORE_FAILURE=" + $_.Exception.Message)
    if (Test-Path -LiteralPath $backendErr) {
        Write-Output 'BACKEND_ERROR_TAIL_BEGIN'
        Get-Content -LiteralPath $backendErr -Tail 80
        Write-Output 'BACKEND_ERROR_TAIL_END'
    }
}
finally {
    $env:MYSQL_PWD = $previousMysqlPassword
    $env:DB_PASSWORD = $previousDbPassword
    $env:DB_URL = $previousDbUrl
    $env:DB_USERNAME = $previousDbUsername
    $env:SERVER_PORT = $previousServerPort

    if ($null -ne $backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($null -ne $mysqlProcess -and -not $mysqlProcess.HasExited) {
        Stop-Process -Id $mysqlProcess.Id -Force -ErrorAction SilentlyContinue
    }

    $listeners = @(
        Get-NetTCPConnection `
            -LocalPort $BackendPort, $DatabasePort `
            -State Listen `
            -ErrorAction SilentlyContinue
    )
    foreach ($listener in $listeners) {
        $listenerProcess = Get-CimInstance `
            Win32_Process `
            -Filter "ProcessId = $($listener.OwningProcess)" `
            -ErrorAction SilentlyContinue
        $isTaskMySql = $listener.LocalPort -eq $DatabasePort `
            -and $listenerProcess.CommandLine -like "*$taskRoot*"
        $isTaskBackend = $listener.LocalPort -eq $BackendPort `
            -and $listenerProcess.CommandLine -like '*FaceChainPlatformApplication*'
        if ($isTaskMySql -or $isTaskBackend) {
            Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }

    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if (
            -not (Test-LocalPort -Port $BackendPort) `
            -and -not (Test-LocalPort -Port $DatabasePort)
        ) {
            break
        }
        Start-Sleep -Milliseconds 250
    }

    $portsClear = -not (Test-LocalPort -Port $BackendPort) `
        -and -not (Test-LocalPort -Port $DatabasePort)
    if (-not $portsClear) {
        Write-Output 'M0_CLEANUP_WARNING=Task ports are still open; temporary files were preserved.'
    }
    elseif (Test-Path -LiteralPath $taskRoot) {
        try {
            $verifiedTaskRoot = [System.IO.Path]::GetFullPath($taskRoot)
            if (-not $verifiedTaskRoot.StartsWith($allowedTempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to remove unsafe task root: $verifiedTaskRoot"
            }
            Remove-Item -LiteralPath $verifiedTaskRoot -Recurse -Force
        }
        catch {
            Write-Output ("M0_CLEANUP_WARNING=" + $_.Exception.Message)
        }
    }
}

if ($null -ne $primaryError) {
    throw $primaryError
}
