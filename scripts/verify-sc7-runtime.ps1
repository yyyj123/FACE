param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.docker.test'),
    [string]$ProjectName = 'face-sc7-acceptance',
    [int]$BackendPort = 9090,
    [int]$AdminPort = 9081,
    [int]$ClientPort = 9082,
    [int]$TechnicianPort = 9083,
    [switch]$KeepRunning
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [System.IO.Path]::GetFullPath($Root)
$EnvFile = [System.IO.Path]::GetFullPath($EnvFile)
$baseVerifier = Join-Path $PSScriptRoot 'verify-docker-runtime.ps1'
$runtimeCheck = Join-Path $PSScriptRoot 'sc7-runtime-check.mjs'
$uiCheck = Join-Path $PSScriptRoot 'sc7-ui-check.mjs'
$stageEnvironment = [ordered]@{
    FACE_BACKEND_PORT = $BackendPort
    FACE_ADMIN_PORT = $AdminPort
    FACE_CLIENT_PORT = $ClientPort
    FACE_TECHNICIAN_PORT = $TechnicianPort
}
$originalEnvironment = @{}
$started = $false
try {
    foreach ($entry in $stageEnvironment.GetEnumerator()) {
        $originalEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
    }
    & node $uiCheck $Root
    if ($LASTEXITCODE -ne 0) { throw 'SC7 UI/domain contract verification failed.' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $baseVerifier `
        -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName `
        -BackendPort $BackendPort -AdminPort $AdminPort -ClientPort $ClientPort `
        -TechnicianPort $TechnicianPort -KeepRunning
    if ($LASTEXITCODE -ne 0) { throw 'SC7 base Docker runtime verification failed.' }
    $started = $true
    & node $runtimeCheck `
        "--admin=http://127.0.0.1:$AdminPort/face-next" `
        "--project=$ProjectName" "--root=$($Root -replace '\\','/')" `
        "--env-file=$($EnvFile -replace '\\','/')"
    if ($LASTEXITCODE -ne 0) {
        Write-Output 'SC7_BACKEND_FAILURE_LOG_BEGIN'
        & docker compose --project-name $ProjectName --project-directory $Root `
            --env-file $EnvFile -f (Join-Path $Root 'compose.yaml') logs --no-color --tail 200 backend
        Write-Output 'SC7_BACKEND_FAILURE_LOG_END'
        throw 'SC7 business runtime verification failed.'
    }
}
finally {
    if ($started -and -not $KeepRunning) {
        & docker compose --project-name $ProjectName --project-directory $Root `
            --env-file $EnvFile -f (Join-Path $Root 'compose.yaml') down --volumes --remove-orphans
        if ($LASTEXITCODE -ne 0) { Write-Warning 'SC7 Docker acceptance cleanup failed.' }
        else { Write-Output 'SC7_DOCKER_ACCEPTANCE_CLEANUP=PASS' }
    } elseif ($started) {
        Write-Output "SC7_DOCKER_ACCEPTANCE_KEEP_RUNNING=PASS;PROJECT=$ProjectName"
    }
    foreach ($entry in $originalEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}
