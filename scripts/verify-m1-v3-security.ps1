param(
    [int]$DatabasePort = 3321,
    [int]$BackendPort = 8193,
    [switch]$VerifyM2,
    [switch]$VerifyM2Ui,
    [switch]$VerifyM3,
    [switch]$VerifyM3Ui,
    [switch]$VerifyM4,
    [switch]$VerifyM4Ui,
    [switch]$VerifyM4Refund,
    [switch]$VerifyM4Purchase,
    [switch]$VerifyM4Payment,
    [switch]$VerifyM5CommissionRules,
    [switch]$VerifyM5CommissionEntries,
    [switch]$VerifyM5CommissionSettlements,
    [switch]$VerifyM5AfterSaleApproval,
    [switch]$VerifyM5Notification,
    [switch]$VerifyM6Report,
    [switch]$VerifyM6Marketing,
    [switch]$VerifyM6TrainingOpen,
    [switch]$VerifyM6Release
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($VerifyM2Ui) {
    $VerifyM2 = $true
}
if ($VerifyM3) {
    $VerifyM2 = $true
}
if ($VerifyM3Ui) {
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM4) {
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM4Ui) {
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM4Refund) {
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM4Purchase) {
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM4Payment) {
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM5CommissionRules) {
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM5CommissionEntries) {
    $VerifyM5CommissionRules = $true
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM5CommissionSettlements) {
    $VerifyM5CommissionEntries = $true
    $VerifyM5CommissionRules = $true
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM5AfterSaleApproval) {
    $VerifyM5CommissionSettlements = $true
    $VerifyM5CommissionEntries = $true
    $VerifyM5CommissionRules = $true
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM5Notification) {
    $VerifyM5AfterSaleApproval = $true
    $VerifyM5CommissionSettlements = $true
    $VerifyM5CommissionEntries = $true
    $VerifyM5CommissionRules = $true
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM6Marketing) {
    $VerifyM6Report = $true
    $VerifyM5Notification = $true
    $VerifyM5AfterSaleApproval = $true
    $VerifyM5CommissionSettlements = $true
    $VerifyM5CommissionEntries = $true
    $VerifyM5CommissionRules = $true
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}
if ($VerifyM6Release) {
    $VerifyM6TrainingOpen = $true
}
if ($VerifyM6TrainingOpen) {
    $VerifyM6Marketing = $true
    $VerifyM6Report = $true
    $VerifyM5Notification = $true
    $VerifyM5AfterSaleApproval = $true
    $VerifyM5CommissionSettlements = $true
    $VerifyM5CommissionEntries = $true
    $VerifyM5CommissionRules = $true
    $VerifyM4 = $true
    $VerifyM3 = $true
    $VerifyM2 = $true
}

$repoRoot = [System.IO.Path]::GetFullPath('E:\face')
$migrationDirectory = Join-Path $repoRoot 'backend-next\src\main\resources\db\migration'
$latestMigrationFile = Get-ChildItem -LiteralPath $migrationDirectory -File |
    Where-Object { $_.Name -match '^V\d+(?:\.\d+)*__.+\.sql$' } |
    Sort-Object Name |
    Select-Object -Last 1
if (-not $latestMigrationFile -or $latestMigrationFile.Name -notmatch '^V(?<Version>\d+(?:\.\d+)*)__') {
    throw 'Could not derive the latest Flyway version from the migration directory.'
}
$latestMigrationVersion = $Matches.Version
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0'
$mysqldExe = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysqlExe = Join-Path $mysqlHome 'bin\mysql.exe'
$mavenExe = (Get-Command 'mvn.cmd').Source

foreach ($required in @($mysqldExe, $mysqlExe, $mavenExe)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required executable missing: $required"
    }
}

$taskRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot ('tmp\m1-v3-security-' + [guid]::NewGuid().ToString('N')))
)
$allowedTempRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'tmp')) +
    [System.IO.Path]::DirectorySeparatorChar
if (-not $taskRoot.StartsWith($allowedTempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe task root: $taskRoot"
}

$dataDirectory = Join-Path $taskRoot 'mysql-data'
$configPath = Join-Path $taskRoot 'my.ini'
$mysqlLog = Join-Path $taskRoot 'mysql.log'
$backendOut = Join-Path $taskRoot 'backend.out.log'
$backendErr = Join-Path $taskRoot 'backend.err.log'
$m3StateFile = Join-Path $taskRoot 'm3-ui-state.json'
$m4StateFile = Join-Path $taskRoot 'm4-ui-state.json'
$mysqlProcess = $null
$backendProcess = $null
$temporaryPassword = $null
$primaryError = $null
$previousMysqlPassword = $env:MYSQL_PWD
$previousDbPassword = $env:DB_PASSWORD
$previousDbUrl = $env:DB_URL
$previousDbUsername = $env:DB_USERNAME
$previousServerPort = $env:SERVER_PORT
$previousCorsAllowedOrigins = $env:CORS_ALLOWED_ORIGINS
$previousSandboxPaymentSecret = $env:FACE_PAYMENT_SANDBOX_SECRET

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
        [string]$Database = 'face_salon'
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

function Invoke-ExpectedHttpFailure {
    param(
        [string]$Method,
        [string]$Uri,
        [int]$ExpectedStatus,
        [hashtable]$Headers = @{},
        [string]$Body
    )
    try {
        $parameters = @{
            Method = $Method
            Uri = $Uri
            Headers = $Headers
            TimeoutSec = 15
            UseBasicParsing = $true
        }
        if ($Body) {
            $parameters.ContentType = 'application/json'
            $parameters.Body = $Body
        }
        $null = Invoke-WebRequest @parameters
    }
    catch {
        if ($null -eq $_.Exception.Response) {
            throw
        }
        $actualStatus = [int]$_.Exception.Response.StatusCode
        if ($actualStatus -ne $ExpectedStatus) {
            throw "Expected HTTP $ExpectedStatus but received $actualStatus for $Uri"
        }
        return
    }
    throw "Expected HTTP $ExpectedStatus but request succeeded: $Uri"
}

function Stop-Backend {
    if ($null -ne $script:backendProcess -and -not $script:backendProcess.HasExited) {
        Stop-Process -Id $script:backendProcess.Id -Force -ErrorAction SilentlyContinue
    }
    $listeners = @(
        Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue
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
}

try {
    if (Test-LocalPort -Port $DatabasePort) {
        throw "Database port $DatabasePort is already in use."
    }
    if (Test-LocalPort -Port $BackendPort) {
        throw "Backend port $BackendPort is already in use."
    }

    New-Item -ItemType Directory -Path $taskRoot, $dataDirectory -Force | Out-Null
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
        -ArgumentList @("--defaults-file=$configPath", '--initialize-insecure', '--console') `
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
    $env:SERVER_PORT = [string]$BackendPort
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$DatabasePort/face_salon?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    $env:DB_USERNAME = 'root'
    $env:DB_PASSWORD = $temporaryPassword
    $env:CORS_ALLOWED_ORIGINS = @(
        'http://127.0.0.1:8081',
        'http://localhost:8081',
        'http://127.0.0.1:8082',
        'http://localhost:8082',
        'http://127.0.0.1:8083',
        'http://localhost:8083',
        'http://127.0.0.1:8084',
        'http://localhost:8084',
        'http://127.0.0.1:8085',
        'http://localhost:8085'
    ) -join ','
    $env:FACE_PAYMENT_SANDBOX_SECRET = 'm4-synthetic-payment-secret'

    $backendProcess = Start-Process `
        -FilePath $mavenExe `
        -ArgumentList @('-q', 'spring-boot:run') `
        -WorkingDirectory (Join-Path $repoRoot 'backend-next') `
        -WindowStyle Hidden `
        -RedirectStandardOutput $backendOut `
        -RedirectStandardError $backendErr `
        -PassThru
    Wait-LocalPort -Port $BackendPort -ExpectedOpen $true -Attempts 180

    $baseUri = "http://127.0.0.1:$BackendPort/face-next"
    $readiness = Invoke-RestMethod `
        -Uri "$baseUri/api/v3/health/readiness" `
        -Headers @{ 'X-Request-Id' = 'm1-readiness' } `
        -TimeoutSec 15
    if ($readiness.code -ne 'SUCCESS' -or $readiness.data.status -ne 'UP') {
        throw 'V3 readiness is not UP.'
    }

    if ($VerifyM2) {
        Invoke-MySql -Sql @'
INSERT IGNORE INTO account_shop_role (
  tenant_id, account_id, shop_id, role_id, effective_from, status
)
SELECT a.tenant_id, a.id, a.home_shop_id, r.id, CURRENT_TIMESTAMP(3), 'ACTIVE'
FROM account a
JOIN role_definition r
  ON r.tenant_id = a.tenant_id
 AND r.role_code = 'MANAGER'
WHERE a.username = 'jishi01';
'@ | Out-Null
    }

    for ($attempt = 1; $attempt -le 5; $attempt++) {
        Invoke-ExpectedHttpFailure `
            -Method 'POST' `
            -Uri "$baseUri/api/v3/auth/login" `
            -ExpectedStatus 401 `
            -Headers @{ 'X-Request-Id' = "m1-login-failure-$attempt" } `
            -Body (@{ username = 'jishi01'; password = 'Synthetic-Wrong-Password' } | ConvertTo-Json)
    }
    Invoke-ExpectedHttpFailure `
        -Method 'POST' `
        -Uri "$baseUri/api/v3/auth/login" `
        -ExpectedStatus 429 `
        -Headers @{ 'X-Request-Id' = 'm1-login-rate-limited' } `
        -Body (@{ username = 'jishi01'; password = 'Face@123' } | ConvertTo-Json)
    $lockEvidence = @(Invoke-MySql -Sql @'
SELECT CONCAT(
  'LOGIN_GUARD=',
  failed_login_count,
  ';LOCKED=',
  CASE WHEN locked_until > CURRENT_TIMESTAMP(3) THEN 1 ELSE 0 END
)
FROM account
WHERE username = 'jishi01';
'@)
    if ($lockEvidence.Count -ne 1 -or $lockEvidence[0] -ne 'LOGIN_GUARD=5;LOCKED=1') {
        throw 'Database login rate limit did not lock the synthetic account.'
    }
    Invoke-MySql -Sql @'
UPDATE account
SET failed_login_count = 0, locked_until = NULL
WHERE username = 'jishi01';
'@ | Out-Null

    $login = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUri/api/v3/auth/login" `
        -Headers @{ 'X-Request-Id' = 'm1-login' } `
        -ContentType 'application/json' `
        -Body (@{ username = 'jishi01'; password = 'Face@123' } | ConvertTo-Json) `
        -TimeoutSec 15
    if ($login.code -ne 'SUCCESS' -or -not $login.data.access_token -or -not $login.data.refresh_token) {
        throw 'V3 login did not return a complete session.'
    }
    $oldAccessToken = [string]$login.data.access_token
    $oldRefreshToken = [string]$login.data.refresh_token

    $context = Invoke-RestMethod `
        -Uri "$baseUri/api/v3/me/context" `
        -Headers @{
            Authorization = "Bearer $oldAccessToken"
            'X-Request-Id' = 'm1-context'
        } `
        -TimeoutSec 15
    if (
        $context.code -ne 'SUCCESS' `
        -or $context.data.username -ne 'jishi01' `
        -or @($context.data.permissions).Count -lt 1
    ) {
        throw 'V3 tenant context did not resolve from the server-side session.'
    }

    $shops = Invoke-RestMethod `
        -Uri "$baseUri/api/v3/shops" `
        -Headers @{
            Authorization = "Bearer $oldAccessToken"
            'X-Request-Id' = 'm1-shops'
        } `
        -TimeoutSec 15
    if ($shops.code -ne 'SUCCESS' -or @($shops.data).Count -lt 1) {
        throw 'V3 accessible shop scope is empty.'
    }

    if ($VerifyM2) {
        $shopId = [long]$shops.data[0].id
        $v3Headers = @{
            Authorization = "Bearer $oldAccessToken"
            'X-Request-Id' = 'm2-master-data'
        }
        $services = Invoke-RestMethod `
            -Uri "$baseUri/api/v3/services?shop_id=$shopId" `
            -Headers $v3Headers `
            -TimeoutSec 15
        $staff = Invoke-RestMethod `
            -Uri "$baseUri/api/v3/staff?shop_id=$shopId" `
            -Headers $v3Headers `
            -TimeoutSec 15
        if (@($services.data).Count -lt 1 -or @($staff.data).Count -lt 2) {
            throw 'M2 service or staff catalog is incomplete.'
        }
        $service = $services.data[0]
        $staffA = $staff.data[0]
        $staffB = $staff.data[1]

        $resourceKey = 'm2-resource-create-001'
        $resourceBody = @{
            shop_id = $shopId
            resource_code = 'M2_ROOM_01'
            resource_name = 'M2 Concurrency Room'
            resource_type = 'ROOM'
            capacity = 1
        } | ConvertTo-Json
        $resourceHeaders = $v3Headers.Clone()
        $resourceHeaders['Idempotency-Key'] = $resourceKey
        $createdResource = Invoke-RestMethod `
            -Method Post `
            -Uri "$baseUri/api/v3/resources" `
            -Headers $resourceHeaders `
            -ContentType 'application/json' `
            -Body $resourceBody `
            -TimeoutSec 15
        $replayedResource = Invoke-RestMethod `
            -Method Post `
            -Uri "$baseUri/api/v3/resources" `
            -Headers $resourceHeaders `
            -ContentType 'application/json' `
            -Body $resourceBody `
            -TimeoutSec 15
        if (
            $createdResource.code -ne 'SUCCESS' `
            -or $replayedResource.code -ne 'SUCCESS'
        ) {
            throw 'M2 resource idempotent create failed.'
        }
        $conflictingBody = @{
            shop_id = $shopId
            resource_code = 'M2_ROOM_DIFFERENT'
            resource_name = 'Different Request'
            resource_type = 'ROOM'
            capacity = 1
        } | ConvertTo-Json
        Invoke-ExpectedHttpFailure `
            -Method 'POST' `
            -Uri "$baseUri/api/v3/resources" `
            -ExpectedStatus 409 `
            -Headers $resourceHeaders `
            -Body $conflictingBody

        $resources = Invoke-RestMethod `
            -Uri "$baseUri/api/v3/resources?shop_id=$shopId" `
            -Headers $v3Headers `
            -TimeoutSec 15
        $resource = @($resources.data | Where-Object { $_.resourceCode -eq 'M2_ROOM_01' })[0]
        if ($null -eq $resource -or [int]$resource.version -ne 1) {
            throw 'M2 resource catalog did not return the created resource.'
        }

        $serviceHeaders = $v3Headers.Clone()
        $serviceHeaders['Idempotency-Key'] = 'm2-service-update-001'
        $serviceUpdate = @{
            shop_id = $shopId
            name = [string]$service.name
            duration_minutes = [int]$service.durationMinutes
            cleanup_minutes = [int]$service.cleanupMinutes
            list_price = [decimal]$service.listPrice
            member_price = [decimal]$service.memberPrice
            status = [string]$service.status
            version = [int]$service.version
        } | ConvertTo-Json
        $serviceResult = Invoke-RestMethod `
            -Method Put `
            -Uri "$baseUri/api/v3/services/$($service.id)" `
            -Headers $serviceHeaders `
            -ContentType 'application/json' `
            -Body $serviceUpdate `
            -TimeoutSec 15
        if ($serviceResult.code -ne 'SUCCESS') {
            throw 'M2 service optimistic update failed.'
        }

        $testDate = [datetime]'2030-08-01'
        foreach ($staffItem in @($staffA, $staffB)) {
            $scheduleHeaders = $v3Headers.Clone()
            $scheduleHeaders['Idempotency-Key'] = "m2-schedule-$($staffItem.id)"
            $scheduleBody = @{
                shop_id = $shopId
                schedule_date = $testDate.ToString('yyyy-MM-dd')
                start_time = '09:00:00'
                end_time = '18:00:00'
                schedule_type = 'WORK'
                remark = 'M2 synthetic acceptance schedule'
            } | ConvertTo-Json
            $scheduleResult = Invoke-RestMethod `
                -Method Post `
                -Uri "$baseUri/api/v3/staff/$($staffItem.id)/schedules" `
                -Headers $scheduleHeaders `
                -ContentType 'application/json' `
                -Body $scheduleBody `
                -TimeoutSec 15
            if ($scheduleResult.code -ne 'SUCCESS') {
                throw 'M2 staff schedule create failed.'
            }
        }

        Invoke-MySql -Sql @"
INSERT INTO staff_service (staff_id, service_id, custom_duration_minutes, enabled)
VALUES
  ($($staffA.id), $($service.id), NULL, 1),
  ($($staffB.id), $($service.id), NULL, 1)
ON DUPLICATE KEY UPDATE enabled = 1;
"@ | Out-Null

        $v2Login = Invoke-RestMethod `
            -Method Post `
            -Uri "$baseUri/api/v2/auth/login" `
            -ContentType 'application/json' `
            -Body (@{ username = 'jishi01'; password = 'Face@123' } | ConvertTo-Json) `
            -TimeoutSec 15
        $legacyToken = [string]$v2Login.data.token
        if (-not $legacyToken) {
            throw 'M2 legacy adapter login failed.'
        }
        $memberId = [long](@(Invoke-MySql -Sql @"
SELECT id FROM member
WHERE tenant_id = 1 AND status = 'ACTIVE'
ORDER BY id
LIMIT 1;
"@)[0])
        $appointmentHeaders = @{ Token = $legacyToken }
        $appointmentStart = '2030-08-01T10:00:00'
        $appointmentBodies = @(
            (@{
                shopId = $shopId
                memberId = $memberId
                staffId = [long]$staffA.id
                serviceIds = @([long]$service.id)
                resourceIds = @([long]$resource.id)
                startAt = $appointmentStart
                source = 'FRONT_DESK'
                internalNote = 'M2 synthetic concurrency A'
            } | ConvertTo-Json),
            (@{
                shopId = $shopId
                memberId = $memberId
                staffId = [long]$staffB.id
                serviceIds = @([long]$service.id)
                resourceIds = @([long]$resource.id)
                startAt = $appointmentStart
                source = 'FRONT_DESK'
                internalNote = 'M2 synthetic concurrency B'
            } | ConvertTo-Json)
        )
        $jobs = foreach ($appointmentBody in $appointmentBodies) {
            Start-Job -ScriptBlock {
                param($uri, $headers, $body)
                try {
                    $response = Invoke-WebRequest `
                        -Method Post `
                        -Uri $uri `
                        -Headers $headers `
                        -ContentType 'application/json' `
                        -Body $body `
                        -UseBasicParsing `
                        -TimeoutSec 30
                    [int]$response.StatusCode
                }
                catch {
                    if ($null -eq $_.Exception.Response) {
                        throw
                    }
                    [int]$_.Exception.Response.StatusCode
                }
            } -ArgumentList "$baseUri/api/v2/appointments", $appointmentHeaders, $appointmentBody
        }
        $null = $jobs | Wait-Job -Timeout 45
        $statuses = @($jobs | Receive-Job)
        $jobs | Remove-Job -Force
        if (
            @($statuses | Where-Object { $_ -eq 200 }).Count -ne 1 `
            -or @($statuses | Where-Object { $_ -eq 409 }).Count -ne 1
        ) {
            throw "M2 resource concurrency expected one 200 and one 409; received: $($statuses -join ',')"
        }

        $m2Evidence = @(Invoke-MySql -Sql @"
SELECT CONCAT('M2_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('staff_skill_version', 'service_resource', 'resource_booking');
SELECT CONCAT('M2_RESOURCE_ROWS=', COUNT(*))
FROM service_resource
WHERE resource_code = 'M2_ROOM_01';
SELECT CONCAT('M2_RESERVED_ROWS=', COUNT(*))
FROM resource_booking
WHERE resource_id = $($resource.id) AND status = 'RESERVED';
SELECT CONCAT('M2_APPOINTMENT_ROWS=', COUNT(*))
FROM appointment
WHERE start_at = '2030-08-01 10:00:00'
  AND internal_note LIKE 'M2 synthetic concurrency%';
SELECT CONCAT('M2_IDEMPOTENCY_ROWS=', COUNT(*))
FROM idempotency_record
WHERE idempotency_key LIKE 'm2-%' AND status = 'COMPLETED';
SELECT CONCAT('M2_AUDIT_ROWS=', COUNT(*))
FROM audit_log
WHERE action IN ('RESOURCE_CREATE', 'SERVICE_CATALOG_UPDATE', 'STAFF_SCHEDULE_CREATE');
"@)
        $expectedM2Evidence = @(
            'M2_TABLES=3',
            'M2_RESOURCE_ROWS=1',
            'M2_RESERVED_ROWS=1',
            'M2_APPOINTMENT_ROWS=1'
        )
        foreach ($expected in $expectedM2Evidence) {
            if ($m2Evidence -notcontains $expected) {
                throw "Missing M2 evidence: $expected"
            }
        }
        $m2Evidence | Write-Output
        'M2_MASTER_DATA_AND_RESOURCE_CONCURRENCY=PASS' | Write-Output

        Invoke-MySql -Sql @'
DELETE ar
FROM account_shop_role ar
JOIN account a
  ON a.id = ar.account_id
 AND a.tenant_id = ar.tenant_id
JOIN role_definition r
  ON r.id = ar.role_id
 AND r.tenant_id = ar.tenant_id
WHERE a.username = 'jishi01'
  AND r.role_code = 'MANAGER';
'@ | Out-Null
        $temporaryManagerRoleCount = @(Invoke-MySql -Sql @'
SELECT COUNT(*)
FROM account_shop_role ar
JOIN account a
  ON a.id = ar.account_id
 AND a.tenant_id = ar.tenant_id
JOIN role_definition r
  ON r.id = ar.role_id
 AND r.tenant_id = ar.tenant_id
WHERE a.username = 'jishi01'
  AND r.role_code = 'MANAGER';
'@)
        if (
            $temporaryManagerRoleCount.Count -ne 1 `
            -or $temporaryManagerRoleCount[0] -ne '0'
        ) {
            throw 'M2 temporary manager role cleanup failed.'
        }
        'M2_TEMP_MANAGER_ROLE_CLEANUP=PASS' | Write-Output
    }

    $refresh = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUri/api/v3/auth/refresh" `
        -Headers @{ 'X-Request-Id' = 'm1-refresh' } `
        -ContentType 'application/json' `
        -Body (@{ refresh_token = $oldRefreshToken } | ConvertTo-Json) `
        -TimeoutSec 15
    $newAccessToken = [string]$refresh.data.access_token
    $newRefreshToken = [string]$refresh.data.refresh_token
    if (
        $refresh.code -ne 'SUCCESS' `
        -or $newAccessToken -eq $oldAccessToken `
        -or $newRefreshToken -eq $oldRefreshToken
    ) {
        throw 'V3 refresh did not rotate both tokens.'
    }

    Invoke-ExpectedHttpFailure `
        -Method 'POST' `
        -Uri "$baseUri/api/v3/auth/refresh" `
        -ExpectedStatus 401 `
        -Headers @{ 'X-Request-Id' = 'm1-refresh-replay' } `
        -Body (@{ refresh_token = $oldRefreshToken } | ConvertTo-Json)
    Invoke-ExpectedHttpFailure `
        -Method 'GET' `
        -Uri "$baseUri/api/v3/me/context" `
        -ExpectedStatus 401 `
        -Headers @{
            Authorization = "Bearer $oldAccessToken"
            'X-Request-Id' = 'm1-old-access'
        }

    $null = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUri/api/v3/auth/logout" `
        -Headers @{
            Authorization = "Bearer $newAccessToken"
            'X-Request-Id' = 'm1-logout'
        } `
        -ContentType 'application/json' `
        -Body '{}' `
        -TimeoutSec 15
    Invoke-ExpectedHttpFailure `
        -Method 'GET' `
        -Uri "$baseUri/api/v3/me/context" `
        -ExpectedStatus 401 `
        -Headers @{
            Authorization = "Bearer $newAccessToken"
            'X-Request-Id' = 'm1-revoked-access'
        }

    $databaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('LATEST=', version, ';SUCCESS=', success)
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 1;
SELECT CONCAT('AUTH_SESSION_ROWS=', COUNT(*)) FROM auth_session;
SELECT CONCAT('REVOKED_ROWS=', COUNT(*)) FROM auth_session WHERE revoked_at IS NOT NULL;
SELECT CONCAT('HASH_ONLY_ROWS=', COUNT(*))
FROM auth_session
WHERE CHAR_LENGTH(access_token_hash) = 64
  AND CHAR_LENGTH(refresh_token_hash) = 64;
SELECT CONCAT('AUDIT_ROWS=', COUNT(*))
FROM data_access_log
WHERE request_id IN ('m1-context', 'm1-shops');
SELECT CONCAT('REGIONAL_MANAGER_ROLES=', COUNT(*))
FROM role_definition
WHERE role_code = 'REGIONAL_MANAGER';
'@
    $expectedEvidence = @(
        "LATEST=$latestMigrationVersion;SUCCESS=1",
        'AUTH_SESSION_ROWS=1',
        'REVOKED_ROWS=1',
        'HASH_ONLY_ROWS=1',
        'AUDIT_ROWS=2',
        'REGIONAL_MANAGER_ROLES=1'
    )
    foreach ($expected in $expectedEvidence) {
        if ($expected -notin $databaseEvidence) {
            throw "Missing database evidence: $expected"
        }
    }
    $tokenLeakCount = @(Invoke-MySql -Sql @"
SELECT COUNT(*)
FROM auth_session
WHERE access_token_hash IN ('$oldAccessToken', '$newAccessToken')
   OR refresh_token_hash IN ('$oldRefreshToken', '$newRefreshToken');
"@)
    if ($tokenLeakCount.Count -ne 1 -or $tokenLeakCount[0] -ne '0') {
        throw 'A raw V3 token was persisted.'
    }
    if ($VerifyM3) {
        $m3DatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M3_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('customer_confirmation', 'service_record_correction');
SELECT CONCAT('M3_COLUMNS=', COUNT(*))
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
       (table_name = 'sales_order'
        AND column_name IN ('create_idempotency_key', 'create_request_hash'))
    OR (table_name = 'service_record' AND column_name = 'completion_request_hash')
    OR (table_name = 'payment_transaction' AND column_name = 'request_hash')
  );
SELECT CONCAT('M3_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'service_record:correct',
  'customer_confirmation:view'
);
'@
        foreach ($expected in @('M3_TABLES=2', 'M3_COLUMNS=4', 'M3_PERMISSIONS=2')) {
            if ($expected -notin $m3DatabaseEvidence) {
                throw "Missing M3 database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m3-service-flow.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword" `
            "--state-file=$m3StateFile"
        if ($LASTEXITCODE -ne 0) {
            throw 'M3 service flow verification failed.'
        }
        $m3DatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M3_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM4) {
        $m4DatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M4_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'package_product',
    'package_product_item',
    'package_instance',
    'package_instance_item',
    'package_ledger'
  );
SELECT CONCAT('M4_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'package:view',
  'package:manage',
  'package:writeoff',
  'package:reverse',
  'account:view',
  'account:manage'
);
SELECT CONCAT('M4_LEDGER_COLUMNS=', COUNT(*))
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'member_account_ledger'
  AND column_name IN ('request_hash', 'reversal_of_ledger_id');
'@
        foreach ($expected in @('M4_TABLES=5', 'M4_PERMISSIONS=6', 'M4_LEDGER_COLUMNS=2')) {
            if ($expected -notin $m4DatabaseEvidence) {
                throw "Missing M4 database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m4-package-account.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword" `
            "--state-file=$m4StateFile"
        if ($LASTEXITCODE -ne 0) {
            throw 'M4 package-account verification failed.'
        }
        if ($VerifyM4Refund) {
            & node `
                (Join-Path $repoRoot 'scripts\verify-m4-refund.mjs') `
                "--backend=$baseUri" `
                "--db-port=$DatabasePort" `
                "--db-password=$temporaryPassword" `
                "--state-file=$m4StateFile"
            if ($LASTEXITCODE -ne 0) {
                throw 'M4 refund verification failed.'
            }
        }
        if ($VerifyM4Purchase) {
            $m4PurchaseDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M4_PURCHASE_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'purchase_order',
    'purchase_order_item',
    'purchase_receipt',
    'purchase_receipt_item',
    'stock_batch',
    'stock_batch_movement'
  );
SELECT CONCAT('M4_PURCHASE_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'purchase:view',
  'purchase:manage',
  'purchase:approve',
  'purchase:receive'
);
SELECT CONCAT('M4_OPENING_BATCH_PARITY=', opening_batches, '/', opening_movements)
FROM (
  SELECT
    (SELECT COUNT(*) FROM stock_batch WHERE source_type = 'MIGRATION_OPENING')
      AS opening_batches,
    (SELECT COUNT(*) FROM stock_batch_movement
     WHERE movement_type = 'MIGRATION_OPENING')
      AS opening_movements
) evidence;
SELECT CONCAT('M4_OPENING_BATCH_ORPHANS=', COUNT(*))
FROM stock_batch b
LEFT JOIN stock_balance sb
  ON sb.id = b.source_id
 AND sb.tenant_id = b.tenant_id
WHERE b.source_type = 'MIGRATION_OPENING'
  AND sb.id IS NULL;
SELECT CONCAT('M4_OPENING_BATCH_DUPLICATES=', COUNT(*))
FROM (
  SELECT tenant_id, source_id
  FROM stock_batch
  WHERE source_type = 'MIGRATION_OPENING'
  GROUP BY tenant_id, source_id
  HAVING COUNT(*) > 1
) duplicated;
'@
            foreach ($expected in @(
                'M4_PURCHASE_TABLES=6',
                'M4_PURCHASE_PERMISSIONS=4',
                'M4_OPENING_BATCH_ORPHANS=0',
                'M4_OPENING_BATCH_DUPLICATES=0'
            )) {
                if ($expected -notin $m4PurchaseDatabaseEvidence) {
                    throw "Missing M4 purchase database evidence: $expected"
                }
            }
            $openingParity = @(
                $m4PurchaseDatabaseEvidence |
                    Where-Object { $_ -like 'M4_OPENING_BATCH_PARITY=*' }
            )
            if ($openingParity.Count -ne 1) {
                throw 'Missing M4 opening batch parity evidence.'
            }
            $parityValues = $openingParity[0].Substring(
                'M4_OPENING_BATCH_PARITY='.Length
            ).Split('/')
            if (
                $parityValues.Count -ne 2 `
                -or $parityValues[0] -ne $parityValues[1]
            ) {
                throw "M4 opening batch migration parity failed: $($openingParity[0])"
            }
            & node `
                (Join-Path $repoRoot 'scripts\verify-m4-purchase-batch.mjs') `
                "--backend=$baseUri" `
                "--db-port=$DatabasePort" `
                "--db-password=$temporaryPassword"
            if ($LASTEXITCODE -ne 0) {
                throw 'M4 purchase-batch verification failed.'
            }
            & node `
                (Join-Path $repoRoot 'scripts\verify-m4-purchase-ui.mjs') `
                "--backend=$baseUri" `
                "--state-file=$m4StateFile" `
                '--admin-port=8083' `
                '--debug-port=9345'
            if ($LASTEXITCODE -ne 0) {
                throw 'M4 purchase UI verification failed.'
            }
            $m4PurchaseDatabaseEvidence | ForEach-Object { Write-Output $_ }
            Write-Output 'M4_PURCHASE_DATABASE_CONTRACT=PASS'
        }
        if ($VerifyM4Payment) {
            $m4PaymentDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M4_PAYMENT_RECONCILIATION_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'payment_callback_event',
    'reconciliation_batch',
    'reconciliation_item',
    'reconciliation_resolution'
  );
SELECT CONCAT('M4_PAYMENT_RECONCILIATION_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'reconciliation:view',
  'reconciliation:manage'
);
SELECT CONCAT('M4_PAYMENT_CHANNEL_COLUMNS=', COUNT(*))
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'payment_transaction'
  AND column_name IN (
    'channel_code',
    'channel_status',
    'channel_request_no',
    'channel_event_id',
    'confirmed_at'
  );
'@
            foreach ($expected in @(
                'M4_PAYMENT_RECONCILIATION_TABLES=4',
                'M4_PAYMENT_RECONCILIATION_PERMISSIONS=2',
                'M4_PAYMENT_CHANNEL_COLUMNS=5'
            )) {
                if ($expected -notin $m4PaymentDatabaseEvidence) {
                    throw "Missing M4 payment database evidence: $expected"
                }
            }
            & node `
                (Join-Path $repoRoot 'scripts\verify-m4-payment-reconciliation.mjs') `
                "--backend=$baseUri" `
                "--db-port=$DatabasePort" `
                "--db-password=$temporaryPassword" `
                "--state-file=$m4StateFile" `
                '--sandbox-secret=m4-synthetic-payment-secret'
            if ($LASTEXITCODE -ne 0) {
                throw 'M4 payment and reconciliation verification failed.'
            }
            & node `
                (Join-Path $repoRoot 'scripts\verify-m4-reconciliation-ui.mjs') `
                "--backend=$baseUri"
            if ($LASTEXITCODE -ne 0) {
                throw 'M4 reconciliation UI verification failed.'
            }
            $m4PaymentDatabaseEvidence | ForEach-Object { Write-Output $_ }
            Write-Output 'M4_PAYMENT_RECONCILIATION_DATABASE_CONTRACT=PASS'
        }
        $m4DatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M4_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM4Ui) {
        $m4UiRefundMode = if ($VerifyM4Refund) { 'true' } else { 'false' }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m4-package-account-ui.mjs') `
            "--backend=$baseUri" `
            "--state-file=$m4StateFile" `
            "--refund-mode=$m4UiRefundMode" `
            '--front-port=8084' `
            '--admin-port=8083' `
            '--debug-port=9344'
        if ($LASTEXITCODE -ne 0) {
            throw 'M4 three-client UI verification failed.'
        }
    }
    if ($VerifyM5CommissionRules) {
        $m5CommissionDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M5_COMMISSION_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'commission_rule_version',
    'commission_rule_scope',
    'commission_source_snapshot'
  );
SELECT CONCAT('M5_COMMISSION_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'commission:rule:view',
  'commission:rule:manage',
  'commission:entry:view'
);
SELECT CONCAT('M5_COMMISSION_CONSTRAINTS=', COUNT(*))
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
    'uk_commission_rule_version',
    'uk_commission_rule_scope',
    'uk_commission_source_snapshot',
    'ck_commission_rule_status',
    'ck_commission_rule_effective_range'
  );
'@
        foreach ($expected in @(
            'M5_COMMISSION_TABLES=3',
            'M5_COMMISSION_PERMISSIONS=3',
            'M5_COMMISSION_CONSTRAINTS=5'
        )) {
            if ($expected -notin $m5CommissionDatabaseEvidence) {
                throw "Missing M5 commission database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m5-commission-rules.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword"
        if ($LASTEXITCODE -ne 0) {
            throw 'M5 commission rule verification failed.'
        }
        $m5CommissionDatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M5_COMMISSION_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM5CommissionEntries) {
        $m5EntryDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M5_ENTRY_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'commission_entry',
    'commission_entry_history',
    'commission_event_projection'
  );
SELECT CONCAT('M5_ENTRY_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code = 'commission:freeze';
SELECT CONCAT('M5_ENTRY_CONSTRAINTS=', COUNT(*))
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
    'uk_commission_entry_source_key',
    'uk_commission_entry_history_idempotency',
    'uk_commission_projection_outbox',
    'ck_commission_entry_direction',
    'ck_commission_entry_link'
  );
'@
        foreach ($expected in @(
            'M5_ENTRY_TABLES=3',
            'M5_ENTRY_PERMISSIONS=1',
            'M5_ENTRY_CONSTRAINTS=5'
        )) {
            if ($expected -notin $m5EntryDatabaseEvidence) {
                throw "Missing M5 entry database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m5-commission-entries.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword"
        if ($LASTEXITCODE -ne 0) {
            throw 'M5 commission entry lifecycle verification failed.'
        }
        $m5EntryDatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M5_COMMISSION_ENTRY_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM5CommissionSettlements) {
        $m5SettlementDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M5_SETTLEMENT_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'commission_settlement_batch',
    'commission_settlement_item'
  );
SELECT CONCAT('M5_SETTLEMENT_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'commission:settlement:view',
  'commission:settlement:manage',
  'commission:settlement:approve'
);
SELECT CONCAT('M5_SETTLEMENT_CONSTRAINTS=', COUNT(*))
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
    'uk_commission_settlement_no',
    'uk_commission_settlement_create_key',
    'uk_commission_settlement_active_entry',
    'ck_commission_settlement_period',
    'ck_commission_settlement_status'
  );
'@
        foreach ($expected in @(
            'M5_SETTLEMENT_TABLES=2',
            'M5_SETTLEMENT_PERMISSIONS=3',
            'M5_SETTLEMENT_CONSTRAINTS=5'
        )) {
            if ($expected -notin $m5SettlementDatabaseEvidence) {
                throw "Missing M5 settlement database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m5-commission-settlements.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword"
        if ($LASTEXITCODE -ne 0) {
            throw 'M5 commission settlement verification failed.'
        }
        $m5SettlementDatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M5_COMMISSION_SETTLEMENT_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM5AfterSaleApproval) {
        $m5AfterSaleDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M5_AFTERSALE_APPROVAL_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'after_sale_case',
    'after_sale_case_log',
    'approval_instance',
    'approval_step',
    'commission_adjustment_request'
  );
SELECT CONCAT('M5_AFTERSALE_APPROVAL_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'commission:entry:adjust',
  'aftersale:view',
  'aftersale:create',
  'aftersale:manage',
  'approval:view',
  'approval:decide'
);
SELECT CONCAT('M5_AFTERSALE_APPROVAL_CONSTRAINTS=', COUNT(*))
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
    'uk_after_sale_case_no',
    'uk_after_sale_log_idempotency',
    'uk_approval_active_business',
    'uk_approval_step_decision_key',
    'uk_commission_adjustment_approval',
    'ck_commission_adjustment_amount'
  );
'@
        foreach ($expected in @(
            'M5_AFTERSALE_APPROVAL_TABLES=5',
            'M5_AFTERSALE_APPROVAL_PERMISSIONS=6',
            'M5_AFTERSALE_APPROVAL_CONSTRAINTS=6'
        )) {
            if ($expected -notin $m5AfterSaleDatabaseEvidence) {
                throw "Missing M5 after-sale database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m5-aftersale-approval.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword"
        if ($LASTEXITCODE -ne 0) {
            throw 'M5 after-sale, approval and adjustment verification failed.'
        }
        $m5AfterSaleDatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M5_AFTERSALE_APPROVAL_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM5Notification) {
        $m5NotificationDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M5_NOTIFICATION_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'notification_message',
    'notification_projection_checkpoint'
  );
SELECT CONCAT('M5_NOTIFICATION_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code = 'notification:view:self';
SELECT CONCAT('M5_NOTIFICATION_CONSTRAINTS=', COUNT(*))
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
    'uk_notification_event_recipient_channel',
    'uk_notification_projection_event',
    'ck_notification_status',
    'ck_notification_external_status'
  );
'@
        foreach ($expected in @(
            'M5_NOTIFICATION_TABLES=2',
            'M5_NOTIFICATION_PERMISSIONS=1',
            'M5_NOTIFICATION_CONSTRAINTS=4'
        )) {
            if ($expected -notin $m5NotificationDatabaseEvidence) {
                throw "Missing M5 notification database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m5-notification.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword"
        if ($LASTEXITCODE -ne 0) {
            throw 'M5 notification verification failed.'
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m5-notification-ui.mjs') `
            "--backend=$baseUri" `
            "--state-file=$m4StateFile" `
            '--front-port=8085' `
            '--admin-port=8084' `
            '--debug-port=9356'
        if ($LASTEXITCODE -ne 0) {
            throw 'M5 notification and three-client UI verification failed.'
        }
        $m5NotificationDatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M5_NOTIFICATION_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM6Report) {
        $m6ReportDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M6_REPORT_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'report_snapshot';
SELECT CONCAT('M6_REPORT_INDEXES=', COUNT(DISTINCT index_name))
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'report_snapshot'
  AND index_name IN (
    'uk_report_snapshot_idempotency',
    'idx_report_snapshot_requester',
    'idx_report_snapshot_expiry'
  );
SELECT CONCAT('M6_REPORT_CONSTRAINTS=', COUNT(*))
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'report_snapshot'
  AND constraint_name IN (
    'ck_report_snapshot_status',
    'ck_report_snapshot_ready_content',
    'ck_report_snapshot_hashes'
  );
SELECT CONCAT('M6_LATEST_FLYWAY=', version, ';SUCCESS=', success)
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
'@
        foreach ($expected in @(
            'M6_REPORT_TABLES=1',
            'M6_REPORT_INDEXES=3',
            'M6_REPORT_CONSTRAINTS=3',
            "M6_LATEST_FLYWAY=$latestMigrationVersion;SUCCESS=1"
        )) {
            if ($expected -notin $m6ReportDatabaseEvidence) {
                throw "Missing M6 report database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m6-report-snapshot.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword"
        if ($LASTEXITCODE -ne 0) {
            throw 'M6 report snapshot verification failed.'
        }
        $m6ReportDatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M6_REPORT_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM6Marketing) {
        $m6MarketingDatabaseEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M6_MARKETING_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'marketing_campaign', 'marketing_campaign_status_history',
    'member_marketing_consent', 'member_marketing_consent_history',
    'marketing_campaign_audience', 'marketing_delivery_attempt'
  );
SELECT CONCAT('M6_MARKETING_UNIQUE_KEYS=', COUNT(DISTINCT CONCAT(table_name, ':', index_name)))
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN (
    'uk_marketing_campaign_create_idempotency',
    'uk_marketing_status_idempotency',
    'uk_member_marketing_consent',
    'uk_member_marketing_consent_history_idempotency',
    'uk_marketing_audience_member',
    'uk_marketing_delivery_member_channel'
  );
SELECT CONCAT('M6_MARKETING_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'marketing:view', 'marketing:manage', 'marketing:approve', 'marketing:execute'
);
SELECT CONCAT('M6_MARKETING_LATEST_FLYWAY=', version, ';SUCCESS=', success)
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
'@
        foreach ($expected in @(
            'M6_MARKETING_TABLES=6',
            'M6_MARKETING_UNIQUE_KEYS=6',
            'M6_MARKETING_PERMISSIONS=4',
            "M6_MARKETING_LATEST_FLYWAY=$latestMigrationVersion;SUCCESS=1"
        )) {
            if ($expected -notin $m6MarketingDatabaseEvidence) {
                throw "Missing M6 marketing database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m6-marketing.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword"
        if ($LASTEXITCODE -ne 0) {
            throw 'M6 marketing governance verification failed.'
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m6-marketing-ui.mjs') `
            "--backend=$baseUri" `
            "--state-file=$m4StateFile" `
            '--front-port=8085' `
            '--admin-port=8084' `
            '--debug-port=9357'
        if ($LASTEXITCODE -ne 0) {
            throw 'M6 marketing UI verification failed.'
        }
        $m6MarketingDatabaseEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M6_MARKETING_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM6TrainingOpen) {
        $m6TrainingOpenEvidence = Invoke-MySql -Sql @'
SELECT CONCAT('M6_TRAINING_OPEN_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'training_course', 'training_record', 'training_record_history',
    'integration_client', 'integration_client_event', 'integration_request_log'
  );
SELECT CONCAT('M6_TRAINING_OPEN_UNIQUE_KEYS=', COUNT(DISTINCT CONCAT(table_name, ':', index_name)))
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN (
    'uk_training_course_revision', 'uk_training_record_assign_idempotency',
    'uk_training_history_idempotency', 'uk_integration_client_code',
    'uk_integration_event_idempotency', 'uk_integration_request_nonce'
  );
SELECT CONCAT('M6_TRAINING_OPEN_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN (
  'training:view', 'training:manage', 'training:verify', 'training:self',
  'integration:view', 'integration:manage', 'integration:rotate'
);
SELECT CONCAT('M6_TRAINING_OPEN_LATEST_FLYWAY=', version, ';SUCCESS=', success)
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
'@
        foreach ($expected in @(
            'M6_TRAINING_OPEN_TABLES=6',
            'M6_TRAINING_OPEN_UNIQUE_KEYS=6',
            'M6_TRAINING_OPEN_PERMISSIONS=7',
            "M6_TRAINING_OPEN_LATEST_FLYWAY=$latestMigrationVersion;SUCCESS=1"
        )) {
            if ($expected -notin $m6TrainingOpenEvidence) {
                throw "Missing M6 training/open-platform database evidence: $expected"
            }
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m6-training-open.mjs') `
            "--backend=$baseUri" `
            "--db-port=$DatabasePort" `
            "--db-password=$temporaryPassword"
        if ($LASTEXITCODE -ne 0) {
            throw 'M6 training/open-platform verification failed.'
        }
        & node `
            (Join-Path $repoRoot 'scripts\verify-m6-training-open-ui.mjs') `
            "--backend=$baseUri" `
            "--state-file=$m4StateFile" `
            '--front-port=8085' `
            '--admin-port=8084' `
            '--debug-port=9357'
        if ($LASTEXITCODE -ne 0) {
            throw 'M6 training/open-platform UI verification failed.'
        }
        $m6TrainingOpenEvidence | ForEach-Object { Write-Output $_ }
        Write-Output 'M6_TRAINING_OPEN_DATABASE_CONTRACT=PASS'
    }
    if ($VerifyM6Release) {
        & node `
            (Join-Path $repoRoot 'scripts\verify-m6-capacity-security.mjs') `
            "--backend=$baseUri" `
            '--concurrency=20' `
            '--requests=500' `
            '--p95-ms=500' `
            '--p99-ms=1000'
        if ($LASTEXITCODE -ne 0) {
            throw 'M6 capacity/security verification failed.'
        }
    }
    if ($VerifyM3Ui) {
        & node `
            (Join-Path $repoRoot 'scripts\verify-m3-three-client-ui.mjs') `
            "--backend=$baseUri" `
            "--state-file=$m3StateFile" `
            '--front-port=8082' `
            '--admin-port=8081' `
            '--debug-port=9343'
        if ($LASTEXITCODE -ne 0) {
            throw 'M3 three-client UI verification failed.'
        }
    }
    if ($VerifyM2Ui) {
        & node `
            (Join-Path $repoRoot 'scripts\verify-m2-master-data-ui.mjs') `
            "--backend=$baseUri" `
            '--admin-port=8081'
        if ($LASTEXITCODE -ne 0) {
            throw 'M2 master data UI verification failed.'
        }
    }

    $databaseEvidence | ForEach-Object { Write-Output $_ }
    $lockEvidence | ForEach-Object { Write-Output $_ }
    Write-Output 'LOGIN_RATE_LIMIT=PASS'
    Write-Output 'LOGIN=PASS'
    Write-Output 'TENANT_CONTEXT_AND_SHOP_SCOPE=PASS'
    Write-Output 'REFRESH_ROTATION=PASS'
    Write-Output 'REFRESH_REPLAY_REJECTED=PASS'
    Write-Output 'OLD_ACCESS_REJECTED=PASS'
    Write-Output 'LOGOUT_REVOCATION=PASS'
    Write-Output 'RAW_TOKEN_PERSISTENCE=0'
    Write-Output 'M1_V3_SECURITY=PASS'
}
catch {
    $primaryError = $_
    Write-Output ("M1_V3_SECURITY_FAILURE=" + $_.Exception.Message)
    if (Test-Path -LiteralPath $backendOut) {
        Write-Output 'BACKEND_EXCEPTION_SUMMARY_BEGIN'
        Select-String `
            -LiteralPath $backendOut `
            -Pattern 'ERROR .*Servlet|Exception:|Caused by:|AnalyticsReportSnapshotService' `
            -Context 1,3 | Select-Object -Last 40
        Write-Output 'BACKEND_EXCEPTION_SUMMARY_END'
    }
    if (Test-Path -LiteralPath $backendErr) {
        Write-Output 'BACKEND_ERROR_TAIL_BEGIN'
        Get-Content -LiteralPath $backendErr -Tail 300
        Write-Output 'BACKEND_ERROR_TAIL_END'
    }
    if (Test-Path -LiteralPath $backendOut) {
        Write-Output 'BACKEND_OUTPUT_TAIL_BEGIN'
        Get-Content -LiteralPath $backendOut -Tail 30
        Write-Output 'BACKEND_OUTPUT_TAIL_END'
    }
}
finally {
    $env:MYSQL_PWD = $previousMysqlPassword
    $env:DB_PASSWORD = $previousDbPassword
    $env:DB_URL = $previousDbUrl
    $env:DB_USERNAME = $previousDbUsername
    $env:SERVER_PORT = $previousServerPort
    $env:CORS_ALLOWED_ORIGINS = $previousCorsAllowedOrigins
    $env:FACE_PAYMENT_SANDBOX_SECRET = $previousSandboxPaymentSecret

    Stop-Backend
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
    if (
        -not (Test-LocalPort -Port $BackendPort) `
        -and -not (Test-LocalPort -Port $DatabasePort) `
        -and (Test-Path -LiteralPath $taskRoot)
    ) {
        $verifiedTaskRoot = [System.IO.Path]::GetFullPath($taskRoot)
        if (-not $verifiedTaskRoot.StartsWith($allowedTempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unsafe task root: $verifiedTaskRoot"
        }
        Remove-Item -LiteralPath $verifiedTaskRoot -Recurse -Force
    }
}

if ($null -ne $primaryError) {
    throw $primaryError
}
