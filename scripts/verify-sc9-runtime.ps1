param(
    [ValidateSet(1,2)][int]$Run = 1,
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvTemplate = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.prod.test'),
    [string]$ProjectName,
    [int]$HttpPort = 0,
    [int]$HttpsPort = 0,
    [int]$PrometheusPort = 0,
    [string]$EvidenceDirectory,
    [switch]$KeepRunning
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [IO.Path]::GetFullPath($Root)
$EnvTemplate = [IO.Path]::GetFullPath($EnvTemplate)
if (-not $ProjectName) { $ProjectName = "face-prod-sc9-run-$Run" }
if ($ProjectName -notmatch '^face-prod-[a-z0-9-]+$') { throw 'SC9 project name must start with face-prod-.' }
if (-not $EvidenceDirectory) { $EvidenceDirectory = Join-Path $Root "docs\verification\SC9\run-$Run" }
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null

function Read-Env([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*([^#][^=]*)=(.*)$') { $values[$Matches[1].Trim()] = $Matches[2].Trim() }
    }
    return $values
}

$templateValues = Read-Env $EnvTemplate
if ($HttpPort -eq 0) { $HttpPort = [int]$templateValues['FACE_HTTP_PORT'] }
if ($HttpsPort -eq 0) { $HttpsPort = [int]$templateValues['FACE_HTTPS_PORT'] }
if ($PrometheusPort -eq 0) { $PrometheusPort = [int]$templateValues['FACE_PROMETHEUS_PORT'] }
if (($HttpPort, $HttpsPort, $PrometheusPort | Select-Object -Unique).Count -ne 3) { throw 'SC9 host ports must be distinct.' }

function Assert-PortFree([int]$Port) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Any, $Port)
    try { $listener.Start() } catch { throw "SC9 requested host port is occupied: $Port. Automatic random selection is disabled." }
    finally { try { $listener.Stop() } catch {} }
}

function Write-NoNewline([string]$Path, [string]$Value) {
    [IO.File]::WriteAllText($Path, $Value, [Text.UTF8Encoding]::new($false))
}

function New-RandomSecret([int]$Bytes = 36) {
    $buffer = New-Object byte[] $Bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+','A').Replace('/','B')
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
    & docker @script:Compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw "SC9 docker compose failed: $($Arguments -join ' ')" }
}

function Invoke-ObjectCli([string]$Project, [string]$Script) {
    $network = "${Project}_storage"
    $normalizedScript = ($Script -replace "`r", '') + "`n# end"
    $normalizedScript | & docker run --rm -i --network $network --entrypoint /bin/sh `
        -v "${script:SecretDirectory}:/run/secrets:ro" `
        -v "${script:BackupDirectory}:/backup" `
        minio/mc:RELEASE.2025-04-16T18-13-26Z
    if ($LASTEXITCODE -ne 0) { throw 'SC9 object storage command failed.' }
}

function Sql([string]$Statement) {
    $result = $Statement | & docker @script:Compose exec -T mysql sh -ec `
        'MYSQL_PWD=$(cat /run/secrets/db_root_password) exec mysql -N -B -uroot face_salon'
    if ($LASTEXITCODE -ne 0) { throw 'SC9 SQL assertion failed.' }
    return @($result | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
}

function Database-Fingerprint {
    return (Sql 'SELECT CONCAT((SELECT COUNT(*) FROM account),CHAR(58),(SELECT COUNT(*) FROM appointment),CHAR(58),(SELECT COUNT(*) FROM mall_order),CHAR(58),(SELECT COUNT(*) FROM flyway_schema_history));' | Select-Object -Last 1)
}

function Assert-HealthyServices {
    $required = @('mysql','object-store','backend','admin','client','gateway','prometheus','alertmanager','pushgateway')
    $running = @(& docker @script:Compose ps --services --status running)
    if ($LASTEXITCODE -ne 0) { throw 'SC9 could not inspect Compose services.' }
    foreach ($service in $required) {
        if ($running -notcontains $service) { throw "SC9 required service is not running: $service" }
        $container = (& docker @script:Compose ps -q $service | Select-Object -Last 1).Trim()
        $health = (& docker inspect $container --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}').Trim()
        if ($health -ne 'healthy' -and $health -ne 'running') { throw "SC9 service is not healthy: $service ($health)" }
    }
    if (@(& docker @script:Compose ps --services) -contains 'technician') { throw 'SC9 production exposed the frozen technician service.' }
}

function Assert-ExternalRuntime {
    $hostName = 'face.local'
    $health = & curl.exe -kfsS --resolve "${hostName}:${HttpsPort}:127.0.0.1" "https://${hostName}:${HttpsPort}/healthz"
    if ($LASTEXITCODE -ne 0 -or $health.Trim() -ne 'ok') { throw 'SC9 TLS gateway health failed.' }
    $client = (& curl.exe -kfsS --resolve "${hostName}:${HttpsPort}:127.0.0.1" "https://${hostName}:${HttpsPort}/client/") -join "`n"
    $admin = (& curl.exe -kfsS --resolve "${hostName}:${HttpsPort}:127.0.0.1" "https://${hostName}:${HttpsPort}/admin/") -join "`n"
    if ($client -notmatch '<div id="app"' -or $admin -notmatch '<div id="app"') { throw 'SC9 production frontend route failed.' }
    $headers = (& curl.exe -kisS --resolve "${hostName}:${HttpsPort}:127.0.0.1" "https://${hostName}:${HttpsPort}/healthz") -join "`n"
    if ($headers -notmatch 'Strict-Transport-Security:' -or $headers -notmatch 'Content-Security-Policy:') { throw 'SC9 TLS security headers are missing.' }
    $redirect = (& curl.exe -sSI "http://127.0.0.1:${HttpPort}/client/") -join "`n"
    if ($redirect -notmatch '^HTTP/\S+ 308' -or $redirect -notmatch "(?im)^Location: https://${hostName}:${HttpsPort}/client/") { throw 'SC9 HTTP to HTTPS redirect failed.' }
    $technicianCode = & curl.exe -kisS --resolve "${hostName}:${HttpsPort}:127.0.0.1" -o NUL -w '%{http_code}' "https://${hostName}:${HttpsPort}/technician/"
    if ($technicianCode -ne '404') { throw 'SC9 technician route must remain unavailable.' }
    $readiness = (& docker @script:Compose exec -T backend curl --fail --silent http://127.0.0.1:8090/face-next/actuator/health/readiness) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $readiness -notmatch '"status":"UP"') { throw 'SC9 backend readiness failed.' }
    $targets = (& curl.exe -fsS "http://127.0.0.1:${PrometheusPort}/api/v1/targets") -join "`n"
    $rules = (& curl.exe -fsS "http://127.0.0.1:${PrometheusPort}/api/v1/rules") -join "`n"
    if ($LASTEXITCODE -ne 0 -or $targets -notmatch '"health":"up"' -or $rules -notmatch 'FaceBackendDown' -or $rules -notmatch 'FaceBackupStale') { throw 'SC9 monitoring target or alert rule failed.' }
}

$SecretDirectory = Join-Path $Root ".runtime\sc9-secrets-run-$Run"
$ChannelEvidenceDirectory = Join-Path $Root ".runtime\sc9-channel-evidence-run-$Run"
$BackupDirectory = Join-Path $Root "backups\sc9-acceptance-run-$Run"
$AdapterDirectory = Join-Path $Root 'prod\adapters\installed'
$RuntimeEnv = Join-Path $Root ".runtime\sc9-run-$Run.env"
foreach ($directory in @($SecretDirectory,$ChannelEvidenceDirectory,$BackupDirectory,$AdapterDirectory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}
foreach ($name in @('db_password','db_root_password','object_store_secret_key','sms_http_bearer_token')) {
    Write-NoNewline (Join-Path $SecretDirectory $name) (New-RandomSecret)
}
Write-NoNewline (Join-Path $SecretDirectory 'object_store_access_key') "FACEACCESSKEYSC9RUN$Run"
Write-NoNewline (Join-Path $SecretDirectory 'alert_webhook_url') 'http://127.0.0.1:9999/face-alerts'

$previousOpenSslConfig = $env:OPENSSL_CONF
$previousErrorAction = $ErrorActionPreference
try {
    if (-not $env:OPENSSL_CONF) {
        $candidate = 'E:\anaconda\Library\ssl\openssl.cnf'
        if (Test-Path -LiteralPath $candidate) { $env:OPENSSL_CONF = $candidate }
    }
    $ErrorActionPreference = 'Continue'
    & openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 30 `
        -keyout (Join-Path $SecretDirectory 'tls_private_key.pem') `
        -out (Join-Path $SecretDirectory 'tls_certificate.pem') `
        -subj '/CN=face.local' -addext 'subjectAltName=DNS:face.local' 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'SC9 synthetic acceptance certificate generation failed.' }
} finally {
    $ErrorActionPreference = $previousErrorAction
    $env:OPENSSL_CONF = $previousOpenSslConfig
}

$envLines = Get-Content -LiteralPath $EnvTemplate -Encoding UTF8
$overrides = @{
    FACE_HTTP_PORT=[string]$HttpPort; FACE_HTTPS_PORT=[string]$HttpsPort; FACE_PROMETHEUS_PORT=[string]$PrometheusPort
    FACE_SECRET_DIR=($SecretDirectory -replace '\\','/'); FACE_ADAPTER_DIR=($AdapterDirectory -replace '\\','/')
    FACE_CHANNEL_EVIDENCE_DIR=($ChannelEvidenceDirectory -replace '\\','/'); FACE_BACKUP_DIR=($BackupDirectory -replace '\\','/')
    FACE_PUBLIC_ORIGIN="https://face.local:$HttpsPort"; OBJECT_STORAGE_PUBLIC_BASE_URL="https://face.local:$HttpsPort/objects"
}
$runtimeLines = foreach ($line in $envLines) {
    if ($line -match '^([^#][^=]*)=') {
        $key = $Matches[1].Trim()
        if ($overrides.ContainsKey($key)) { "$key=$($overrides[$key])" } else { $line }
    } else { $line }
}
[IO.File]::WriteAllLines($RuntimeEnv, $runtimeLines, [Text.UTF8Encoding]::new($false))

$Compose = @('compose','--project-name',$ProjectName,'--project-directory',$Root,'--env-file',$RuntimeEnv,'-f',(Join-Path $Root 'docker-compose.prod.yml'))
$restoreProject = "$ProjectName-restore-drill"
$RestoreCompose = @('compose','--project-name',$restoreProject,'--project-directory',$Root,'--env-file',$RuntimeEnv,'-f',(Join-Path $Root 'docker-compose.prod.yml'))
$started = $false
try {
    foreach ($port in @($HttpPort,$HttpsPort,$PrometheusPort)) { Assert-PortFree $port }
    Write-Output "SC9_PORT_PREFLIGHT=PASS;HTTP=$HttpPort;HTTPS=$HttpsPort;PROMETHEUS=$PrometheusPort"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'test-sc9-production-contract.ps1') -Root $Root
    if ($LASTEXITCODE -ne 0) { throw 'SC9 static contract failed.' }
    foreach ($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter 'prod-*.sh') {
        & docker run --rm -v "${Root}:/workspace" maven:3.9.11-eclipse-temurin-21-alpine /bin/sh -n "/workspace/scripts/$($file.Name)"
        if ($LASTEXITCODE -ne 0) { throw "SC9 Linux shell syntax failed: $($file.Name)" }
    }
    Write-Output 'SC9_LINUX_SHELL_SYNTAX=PASS'
    & mvn.cmd -B -ntp -f (Join-Path $Root 'backend-next\pom.xml') test
    if ($LASTEXITCODE -ne 0) { throw 'SC9 backend tests failed.' }
    Write-Output 'SC9_BACKEND_TESTS=PASS'
    & npm.cmd --prefix (Join-Path $Root 'admin-next') run build
    if ($LASTEXITCODE -ne 0) { throw 'SC9 admin build failed.' }
    Write-Output 'SC9_ADMIN_BUILD=PASS'
    & npm.cmd --prefix (Join-Path $Root 'front-next') run build
    if ($LASTEXITCODE -ne 0) { throw 'SC9 client build failed.' }
    Write-Output 'SC9_CLIENT_BUILD=PASS'

    & docker @Compose --profile tools down --volumes --remove-orphans | Out-Null
    & docker @RestoreCompose --profile tools down --volumes --remove-orphans | Out-Null
    $config = & docker @Compose config
    if ($LASTEXITCODE -ne 0) { throw 'SC9 production Compose rendering failed.' }
    foreach ($secretFile in Get-ChildItem -LiteralPath $SecretDirectory -File) {
        $secretValue = Get-Content -LiteralPath $secretFile.FullName -Raw
        if ($secretValue.Length -ge 8 -and $config -match [regex]::Escape($secretValue)) { throw "SC9 rendered config leaked Secret: $($secretFile.Name)" }
    }
    $config | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'compose-rendered-redacted.txt') -Encoding UTF8

    Invoke-Compose up -d mysql object-store --wait --wait-timeout 600
    $started = $true
    Invoke-Compose --profile tools build migration
    Invoke-Compose --profile tools run --rm migration flyway:migrate
    Invoke-Compose --profile tools run --rm migration flyway:validate
    $directoryVersions = @(Get-ChildItem -LiteralPath (Join-Path $Root 'backend-next\src\main\resources\db\migration') -File | ForEach-Object { if ($_.Name -match '^V(?<v>\d+(?:\.\d+)*)__.+\.sql$') { $Matches.v } } | Sort-Object -Unique)
    $historyVersions = @(Sql "SELECT version FROM flyway_schema_history WHERE success=1 AND type='SQL' AND version IS NOT NULL ORDER BY installed_rank;" | Sort-Object -Unique)
    if (@(Compare-Object $directoryVersions $historyVersions).Count -ne 0) { throw 'SC9 Flyway directory/history mismatch.' }
    Write-Output "SC9_FLYWAY_DYNAMIC_HISTORY=PASS;COUNT=$($directoryVersions.Count);LATEST=$($directoryVersions | Select-Object -Last 1)"

    Invoke-Compose up -d object-init
    Invoke-Compose up -d --build --wait --wait-timeout 1200
    Assert-HealthyServices
    Assert-ExternalRuntime
    Write-Output 'SC9_PRODUCTION_RUNTIME=PASS'

    $proof = "SC9_OBJECT_STORAGE_PROOF_RUN_$Run"
    $objectScript = @"
set -eu
mc alias set face http://object-store:9000 "`$(cat /run/secrets/object_store_access_key)" "`$(cat /run/secrets/object_store_secret_key)" >/dev/null
printf '%s' '$proof' | mc pipe face/$($templateValues['OBJECT_STORAGE_BUCKET'])/acceptance/sc9-object-proof.txt >/dev/null
mc cat face/$($templateValues['OBJECT_STORAGE_BUCKET'])/acceptance/sc9-object-proof.txt
"@
    $objectResult = (Invoke-ObjectCli $ProjectName $objectScript | Select-Object -Last 1).Trim()
    if ($objectResult -ne $proof) { throw 'SC9 object storage write/read verification failed.' }
    Write-Output 'SC9_OBJECT_STORAGE=PASS'

    $fingerprint = Database-Fingerprint
    $backupSet = Join-Path $BackupDirectory "run-$Run"
    New-Item -ItemType Directory -Force -Path (Join-Path $backupSet 'objects') | Out-Null
    Invoke-Compose exec -T mysql sh -ec 'MYSQL_PWD=$(cat /run/secrets/db_root_password) mysqldump --single-transaction --routines --triggers --no-tablespaces -uroot face_salon | gzip -9 > /tmp/sc9-database.sql.gz'
    Invoke-Compose cp 'mysql:/tmp/sc9-database.sql.gz' (Join-Path $backupSet 'database.sql.gz')
    Write-NoNewline (Join-Path $backupSet 'database.fingerprint') $fingerprint
    $mirrorScript = @"
set -eu
mc alias set face http://object-store:9000 "`$(cat /run/secrets/object_store_access_key)" "`$(cat /run/secrets/object_store_secret_key)" >/dev/null
mc mirror --overwrite face/$($templateValues['OBJECT_STORAGE_BUCKET']) /backup/run-$Run/objects
"@
    Invoke-ObjectCli $ProjectName $mirrorScript | Out-Null
    $hashLines = Get-ChildItem -LiteralPath $backupSet -Recurse -File | Sort-Object FullName | ForEach-Object {
        $relative = $_.FullName.Substring($backupSet.Length + 1).Replace('\','/')
        "$(Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName | Select-Object -ExpandProperty Hash)  $relative"
    }
    $hashLines | Set-Content -LiteralPath (Join-Path $backupSet 'SHA256SUMS') -Encoding ascii
    foreach ($line in $hashLines) {
        if ($line -notmatch '^([A-F0-9]{64})\s{2}(.+)$') { throw 'SC9 backup checksum manifest is malformed.' }
        $actualHash = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $backupSet $Matches[2]) | Select-Object -ExpandProperty Hash
        if ($actualHash -ne $Matches[1]) { throw "SC9 backup checksum mismatch: $($Matches[2])" }
    }
    Write-Output "SC9_BACKUP=PASS;FINGERPRINT=$fingerprint"

    & docker @RestoreCompose up -d mysql object-store --wait --wait-timeout 600
    if ($LASTEXITCODE -ne 0) { throw 'SC9 isolated restore services failed.' }
    & docker @RestoreCompose cp (Join-Path $backupSet 'database.sql.gz') 'mysql:/tmp/sc9-database.sql.gz'
    if ($LASTEXITCODE -ne 0) { throw 'SC9 restore database copy failed.' }
    & docker @RestoreCompose exec -T mysql sh -ec 'gzip -dc /tmp/sc9-database.sql.gz | MYSQL_PWD=$(cat /run/secrets/db_root_password) mysql -uroot face_salon'
    if ($LASTEXITCODE -ne 0) { throw 'SC9 database restore failed.' }
    & docker @RestoreCompose up -d object-init
    if ($LASTEXITCODE -ne 0) { throw 'SC9 restore bucket initialization failed.' }
    & docker @RestoreCompose wait object-init | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'SC9 restore bucket initialization did not complete successfully.' }
    $restoreScript = @"
set -eu
mc alias set face http://object-store:9000 "`$(cat /run/secrets/object_store_access_key)" "`$(cat /run/secrets/object_store_secret_key)" >/dev/null
mc mirror --overwrite /backup/run-$Run/objects face/$($templateValues['OBJECT_STORAGE_BUCKET'])
mc cat face/$($templateValues['OBJECT_STORAGE_BUCKET'])/acceptance/sc9-object-proof.txt
"@
    $restoredObject = (Invoke-ObjectCli $restoreProject $restoreScript | Select-Object -Last 1).Trim()
    $savedCompose = $Compose
    $Compose = $RestoreCompose
    $restoredFingerprint = Database-Fingerprint
    $Compose = $savedCompose
    if ($restoredFingerprint -ne $fingerprint -or $restoredObject -ne $proof) { throw "SC9 restore mismatch: $fingerprint -> $restoredFingerprint" }
    Write-Output "SC9_RESTORE_DRILL=PASS;FINGERPRINT=$restoredFingerprint"
    & docker @RestoreCompose --profile tools down --volumes --remove-orphans | Out-Null

    Invoke-Compose restart
    Invoke-Compose up -d --wait --wait-timeout 600
    Assert-HealthyServices
    Assert-ExternalRuntime
    $restartFingerprint = Database-Fingerprint
    if ($restartFingerprint -ne $fingerprint) { throw 'SC9 restart changed the database fingerprint.' }
    Write-Output 'SC9_RESTART=PASS'

    $logs = & docker @Compose logs --no-color
    foreach ($secretFile in Get-ChildItem -LiteralPath $SecretDirectory -File | Where-Object { $_.Name -notmatch '^tls_certificate' }) {
        $secretValue = Get-Content -LiteralPath $secretFile.FullName -Raw
        if ($secretValue.Length -ge 8 -and $logs -match [regex]::Escape($secretValue)) { throw "SC9 container logs leaked Secret: $($secretFile.Name)" }
    }
    @(
        "run=$Run", 'result=PASS', "project=$ProjectName", "migration-count=$($directoryVersions.Count)",
        "migration-latest=$($directoryVersions | Select-Object -Last 1)", "database-fingerprint=$fingerprint",
        'payment=disabled', 'sms=disabled', 'logistics=disabled', 'demo-fallback=false'
    ) | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'runtime-result.properties') -Encoding UTF8
    Write-Output "SC9_REPEATABLE_RUN_$Run=PASS"
} finally {
    try { & docker @RestoreCompose --profile tools down --volumes --remove-orphans | Out-Null } catch { Write-Warning $_ }
    if ($started -and -not $KeepRunning) {
        try { & docker @Compose --profile tools down --volumes --remove-orphans | Out-Null; Write-Output 'SC9_DOCKER_ACCEPTANCE_CLEANUP=PASS' } catch { Write-Warning $_ }
    } elseif ($started) { Write-Output "SC9_DOCKER_ACCEPTANCE_KEEP_RUNNING=PASS;PROJECT=$ProjectName" }
}
