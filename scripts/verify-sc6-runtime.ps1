param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.docker.test'),
    [string]$ProjectName = 'face-sc6-acceptance',
    [int]$BackendPort = 8990,
    [int]$AdminPort = 8981,
    [int]$ClientPort = 8982,
    [int]$TechnicianPort = 8983,
    [string]$DemoPaymentSecret = 'sc6-synthetic-payment-secret',
    [int]$FulfillmentScanMs = 1000,
    [switch]$KeepRunning
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [System.IO.Path]::GetFullPath($Root)
$EnvFile = [System.IO.Path]::GetFullPath($EnvFile)
$baseVerifier = Join-Path $PSScriptRoot 'verify-docker-runtime.ps1'
$runtimeCheck = Join-Path $PSScriptRoot 'sc6-runtime-check.mjs'
$uiCheck = Join-Path $PSScriptRoot 'sc6-ui-check.mjs'
$stageEnvironment = [ordered]@{
    FACE_BACKEND_PORT = $BackendPort
    FACE_ADMIN_PORT = $AdminPort
    FACE_CLIENT_PORT = $ClientPort
    FACE_TECHNICIAN_PORT = $TechnicianPort
    FACE_PAYMENT_DEMO_MOCK_ENABLED = 'true'
    FACE_PAYMENT_DEMO_MOCK_SECRET = $DemoPaymentSecret
    FACE_SC6_FULFILLMENT_SCAN_MS = $FulfillmentScanMs
}
$originalEnvironment = @{}
$started = $false
try {
    foreach ($entry in $stageEnvironment.GetEnumerator()) {
        $originalEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
    }
    & node $uiCheck $Root
    if ($LASTEXITCODE -ne 0) { throw 'SC6 UI/domain contract verification failed.' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $baseVerifier `
        -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName `
        -BackendPort $BackendPort -AdminPort $AdminPort -ClientPort $ClientPort `
        -TechnicianPort $TechnicianPort -KeepRunning
    if ($LASTEXITCODE -ne 0) { throw 'SC6 base Docker runtime verification failed.' }
    $started = $true
    & node $runtimeCheck `
        "--client=http://127.0.0.1:$ClientPort/face-next" `
        "--admin=http://127.0.0.1:$AdminPort/face-next" `
        "--project=$ProjectName" "--root=$($Root -replace '\\','/')" `
        "--env-file=$($EnvFile -replace '\\','/')"
    if ($LASTEXITCODE -ne 0) { throw 'SC6 business runtime verification failed.' }
}
finally {
    if ($started -and -not $KeepRunning) {
        & docker compose --project-name $ProjectName --project-directory $Root `
            --env-file $EnvFile -f (Join-Path $Root 'compose.yaml') down --volumes --remove-orphans
        if ($LASTEXITCODE -ne 0) { Write-Warning 'SC6 Docker acceptance cleanup failed.' }
        else { Write-Output 'SC6_DOCKER_ACCEPTANCE_CLEANUP=PASS' }
    } elseif ($started) {
        Write-Output "SC6_DOCKER_ACCEPTANCE_KEEP_RUNNING=PASS;PROJECT=$ProjectName"
    }
    foreach ($entry in $originalEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}
