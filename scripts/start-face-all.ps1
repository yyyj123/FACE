$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendNextDirectory = Join-Path $projectRoot 'backend-next'
$adminDirectory = Join-Path $projectRoot 'admin-next'
$frontDirectory = Join-Path $projectRoot 'front-next'
$databaseScript = Join-Path $PSScriptRoot 'start-face-mysql.ps1'

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    return [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().
        GetActiveTcpListeners().
        Port -contains $Port
}

function Get-RequiredCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Command not found: $Name. Install it and add it to PATH."
    }

    return $command.Source
}

function Start-FaceService {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [int]$Port,

        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList,

        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory
    )

    if (Test-TcpPort -Port $Port) {
        Write-Host "[$Name] Port $Port is already listening; skipping duplicate startup." -ForegroundColor Yellow
        return $null
    }

    Write-Host "[$Name] Starting on port $Port..." -ForegroundColor Cyan
    return Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -NoNewWindow `
        -PassThru
}

foreach ($requiredPath in @(
    $backendNextDirectory,
    $adminDirectory,
    $frontDirectory,
    $databaseScript
)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required startup path does not exist: $requiredPath"
    }
}

$mavenCommand = Get-RequiredCommand -Name 'mvn.cmd'
$npmCommand = Get-RequiredCommand -Name 'npm.cmd'

Write-Host '[Database] Checking and starting FACE MySQL...' -ForegroundColor Cyan
& $databaseScript

$processes = @()

$backendNextProcess = Start-FaceService `
    -Name 'Chain Backend' `
    -Port 8090 `
    -FilePath $mavenCommand `
    -ArgumentList @('-q', 'spring-boot:run') `
    -WorkingDirectory $backendNextDirectory
if ($backendNextProcess) {
    $processes += $backendNextProcess
}

$adminProcess = Start-FaceService `
    -Name 'Chain Admin' `
    -Port 8081 `
    -FilePath $npmCommand `
    -ArgumentList @('run', 'dev') `
    -WorkingDirectory $adminDirectory
if ($adminProcess) {
    $processes += $adminProcess
}

$frontProcess = Start-FaceService `
    -Name 'Client V2' `
    -Port 8082 `
    -FilePath $npmCommand `
    -ArgumentList @('run', 'dev') `
    -WorkingDirectory $frontDirectory
if ($frontProcess) {
    $processes += $frontProcess
}

Write-Host ''
Write-Host 'FACE startup commands have been launched:' -ForegroundColor Green
Write-Host '  Admin V2:http://127.0.0.1:8081'
Write-Host '  Client:  http://127.0.0.1:8082'
Write-Host '  Chain API:http://127.0.0.1:8090/face-next'

if ($processes.Count -eq 0) {
    Write-Host 'All application ports are already listening.' -ForegroundColor Green
    exit 0
}

Write-Host ''
Write-Host 'The launcher is running. Use the IDEA Stop button to end this run.' -ForegroundColor DarkGray
Wait-Process -Id $processes.Id
