param(
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendDirectory = Join-Path $projectRoot 'backend-next'
$adminDirectory = Join-Path $projectRoot 'admin-next'
$clientDirectory = Join-Path $projectRoot 'front-next'
$runtimeDirectory = Join-Path $projectRoot '.runtime'

if (-not $env:DB_PASSWORD) {
    throw 'DB_PASSWORD is required. Set it before starting the V2 platform.'
}

& (Join-Path $PSScriptRoot 'start-face-mysql.ps1')
New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null

function Test-TcpPort {
    param([int]$Port)
    return [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().
        GetActiveTcpListeners().
        Port -contains $Port
}

function Start-HiddenService {
    param(
        [string]$Name,
        [int]$Port,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [string]$LogPrefix
    )

    if (Test-TcpPort -Port $Port) {
        Write-Host "[$Name] Port $Port is already listening." -ForegroundColor Yellow
        return
    }

    Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $runtimeDirectory "$LogPrefix.out.log") `
        -RedirectStandardError (Join-Path $runtimeDirectory "$LogPrefix.err.log")

    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Milliseconds 500
        if (Test-TcpPort -Port $Port) {
            Write-Host "[$Name] Ready on port $Port." -ForegroundColor Green
            return
        }
    }

    throw "$Name did not start on port $Port."
}

$maven = (Get-Command 'mvn.cmd' -ErrorAction Stop).Source
$npm = (Get-Command 'npm.cmd' -ErrorAction Stop).Source

Start-HiddenService `
    -Name 'Chain backend' `
    -Port 8090 `
    -FilePath $maven `
    -ArgumentList @('-q', 'spring-boot:run') `
    -WorkingDirectory $backendDirectory `
    -LogPrefix 'backend-next'

Start-HiddenService `
    -Name 'Chain admin' `
    -Port 8081 `
    -FilePath $npm `
    -ArgumentList @('run', 'dev') `
    -WorkingDirectory $adminDirectory `
    -LogPrefix 'admin-next'

Start-HiddenService `
    -Name 'Client V2' `
    -Port 8082 `
    -FilePath $npm `
    -ArgumentList @('run', 'dev') `
    -WorkingDirectory $clientDirectory `
    -LogPrefix 'front-next'

Write-Host ''
Write-Host 'FACE V2 platform is ready:' -ForegroundColor Green
Write-Host '  Admin:   http://127.0.0.1:8081'
Write-Host '  Client:  http://127.0.0.1:8082'
Write-Host '  API:     http://127.0.0.1:8090/face-next'
Write-Host '  Health:  http://127.0.0.1:8090/face-next/api/v2/health'

if (-not $NoBrowser) {
    Start-Process 'http://127.0.0.1:8081'
}
