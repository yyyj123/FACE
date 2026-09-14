param(
    [ValidateSet(1, 2)][int]$Run = 1,
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.demo.test'),
    [string]$ProjectName,
    [int]$DemoPort = 0,
    [string]$EvidenceDirectory,
    [string]$PreviewDirectory = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [IO.Path]::GetFullPath($Root)
$EnvFile = [IO.Path]::GetFullPath($EnvFile)
if (-not $ProjectName) { $ProjectName = "face-sc8-web-r-$Run" }
if (-not $EvidenceDirectory) { $EvidenceDirectory = Join-Path $Root "docs\verification\WEB-R\run-$Run" }
if (-not $PreviewDirectory) {
    $PreviewDirectory = Join-Path $Root '.artifacts\preview\FACE-WEB-R'
}
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
$PreviewDirectory = [IO.Path]::GetFullPath($PreviewDirectory)
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $PreviewDirectory -Force | Out-Null
$env:FACE_WEB_R_PREVIEWS = $PreviewDirectory
$transcribing = $false
try {
    Start-Transcript -LiteralPath (Join-Path $EvidenceDirectory 'verification.log') -Force | Out-Null
    $transcribing = $true
} catch {
    Write-Warning "WEB-R transcript could not start: $_"
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
    if ($line -match '^\s*([^#][^=]*)=(.*)$') { $values[$Matches[1].Trim()] = $Matches[2].Trim() }
}
if ($DemoPort -eq 0) { $DemoPort = [int]$values['FACE_DEMO_PORT'] }
if ($DemoPort -lt 1 -or $DemoPort -gt 65535) { throw "WEB-R requires an explicit valid DemoPort; received $DemoPort." }

$compose = @(
    'compose', '--project-name', $ProjectName, '--project-directory', $Root, '--env-file', $EnvFile,
    '-f', (Join-Path $Root 'compose.yaml'), '-f', (Join-Path $Root 'docker-compose.demo.yml')
)

function Invoke-Checked([string]$Failure, [scriptblock]$Command) {
    & $Command
    if ($LASTEXITCODE -ne 0) { throw $Failure }
}

function Sql([string]$Statement) {
    $result = $Statement | & docker @compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon'
    if ($LASTEXITCODE -ne 0) { throw 'WEB-R SQL assertion failed.' }
    return @($result)
}

function Assert-Healthy([string[]]$Services) {
    foreach ($service in $Services) {
        $containerId = (& docker @compose ps -q $service | Select-Object -Last 1).Trim()
        if (-not $containerId) { throw "WEB-R service has no container: $service" }
        $status = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId | Select-Object -Last 1).Trim()
        if ($LASTEXITCODE -ne 0 -or $status -ne 'healthy') { throw "WEB-R service is not healthy: $service=$status" }
    }
    Write-Output "WEB_R_CONTAINER_HEALTH=PASS;SERVICES=$($Services -join ',')"
}

$started = $false
try {
    Invoke-Checked 'WEB-R static UI contract failed.' {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'test-web-r-ui-contract.ps1') -Root $Root
    }
    Invoke-Checked 'WEB-R client tests failed.' { & npm.cmd --prefix (Join-Path $Root 'front-next') test }
    Write-Output 'WEB_R_CLIENT_TESTS=PASS'
    Invoke-Checked 'WEB-R admin tests failed.' { & npm.cmd --prefix (Join-Path $Root 'admin-next') test }
    Write-Output 'WEB_R_ADMIN_TESTS=PASS'
    Invoke-Checked 'WEB-R backend tests failed.' { & mvn.cmd -B -ntp -f (Join-Path $Root 'backend-next\pom.xml') test }
    Write-Output 'WEB_R_BACKEND_TESTS=PASS'
    Invoke-Checked 'WEB-R client production build failed.' { & npm.cmd --prefix (Join-Path $Root 'front-next') run build }
    Write-Output 'WEB_R_CLIENT_BUILD=PASS'
    Invoke-Checked 'WEB-R admin production build failed.' { & npm.cmd --prefix (Join-Path $Root 'admin-next') run build }
    Write-Output 'WEB_R_ADMIN_BUILD=PASS'

    & (Join-Path $PSScriptRoot 'demo-reset.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName -DemoPort $DemoPort -SkipBackup
    if ($LASTEXITCODE -ne 0) { throw 'WEB-R clean demo reset failed.' }
    $started = $true
    $metadataPath = Join-Path $Root ".runtime\sc8-demo-$ProjectName.json"
    $metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $publicUrl = [string]$metadata.publicUrl
    if (-not $publicUrl) { throw 'WEB-R Quick Tunnel URL is missing.' }
    $browserUrl = "http://127.0.0.1:$DemoPort"

    $directoryVersions = @(
        Get-ChildItem -LiteralPath (Join-Path $Root 'backend-next\src\main\resources\db\migration') -File |
            ForEach-Object { if ($_.Name -match '^V(?<v>\d+(?:\.\d+)*)__.+\.sql$') { $Matches.v } } |
            Sort-Object -Unique
    )
    $historyVersions = @(
        Sql "SELECT version FROM flyway_schema_history WHERE success=1 AND type='SQL' AND version IS NOT NULL ORDER BY installed_rank;" |
            ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ } | Sort-Object -Unique
    )
    if (@(Compare-Object $directoryVersions $historyVersions).Count -ne 0) { throw 'WEB-R Flyway migration directory/history mismatch.' }
    Write-Output "WEB_R_FLYWAY_DYNAMIC_HISTORY=PASS;COUNT=$($directoryVersions.Count);LATEST=$($directoryVersions | Select-Object -Last 1)"
    Assert-Healthy @('mysql', 'backend', 'admin', 'client', 'gateway')

    Invoke-Checked 'WEB-R SC8 business regression failed.' {
        & node (Join-Path $PSScriptRoot 'sc8-runtime-check.mjs') "--base=http://127.0.0.1:$DemoPort" "--project=$ProjectName" "--root=$($Root -replace '\\', '/')" "--env-file=$($EnvFile -replace '\\', '/')"
    }
    Write-Output 'WEB_R_SC8_BUSINESS_REGRESSION=PASS'
    Invoke-Checked 'WEB-R browser matrix failed.' {
        & node (Join-Path $PSScriptRoot 'verify-web-r-ui.mjs') "--url=$browserUrl" "--gate-password=$($values['FACE_DEMO_ACCESS_PASSWORD'])" "--account-password=$($values['FACE_DEMO_ADMIN_PASSWORD'])" "--output=$($EvidenceDirectory -replace '\\', '/')" "--run=$Run" "--edge-dir=$((Join-Path $Root ".runtime\web-r-edge-local-v2-$Run") -replace '\\', '/')"
    }

    & docker @compose restart backend admin client gateway
    if ($LASTEXITCODE -ne 0) { throw 'WEB-R container restart command failed.' }
    & docker @compose up -d --wait --wait-timeout 900 backend admin client gateway
    if ($LASTEXITCODE -ne 0) { throw 'WEB-R containers did not recover after restart.' }
    Assert-Healthy @('mysql', 'backend', 'admin', 'client', 'gateway')
    Invoke-Checked 'WEB-R browser matrix failed after restart.' {
        & node (Join-Path $PSScriptRoot 'verify-web-r-ui.mjs') "--url=$browserUrl" "--gate-password=$($values['FACE_DEMO_ACCESS_PASSWORD'])" "--account-password=$($values['FACE_DEMO_ADMIN_PASSWORD'])" "--output=$(($EvidenceDirectory + '\after-restart') -replace '\\', '/')" "--run=$($Run + 10)" '--smoke=true' "--edge-dir=$((Join-Path $Root ".runtime\web-r-edge-restart-local-v2-$Run") -replace '\\', '/')"
    }
    Write-Output 'WEB_R_RESTART_RECOVERY=PASS'

    & (Join-Path $PSScriptRoot 'demo-stop.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName
    if ($LASTEXITCODE -ne 0) { throw 'WEB-R demo stop failed.' }
    $started = $false
    Write-Output "WEB_R_REPEATABLE_RUN_$Run=PASS"
} finally {
    if ($started) {
        try { & (Join-Path $PSScriptRoot 'demo-stop.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName }
        catch { Write-Warning $_ }
    }
    if ($transcribing) { Stop-Transcript | Out-Null }
    Remove-Item Env:FACE_WEB_R_PREVIEWS -ErrorAction SilentlyContinue
}
