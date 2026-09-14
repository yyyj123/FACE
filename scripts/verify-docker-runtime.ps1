param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.docker.test'),
    [string]$ProjectName = 'face-b0r-acceptance',
    [int]$BackendPort = 0,
    [int]$AdminPort = 0,
    [int]$ClientPort = 0,
    [int]$TechnicianPort = 0,
    [switch]$KeepRunning
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Root = [System.IO.Path]::GetFullPath($Root)
$EnvFile = [System.IO.Path]::GetFullPath($EnvFile)
$composeFile = Join-Path $Root 'compose.yaml'
$migrationDirectory = Join-Path $Root 'backend-next\src\main\resources\db\migration'

if ($ProjectName -notmatch '^[a-z0-9][a-z0-9_-]+$') {
    throw 'ProjectName must contain only lowercase letters, digits, underscores, or hyphens.'
}
foreach ($requiredFile in @($composeFile, $EnvFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required B0-R file is missing: $requiredFile"
    }
}
if (-not (Test-Path -LiteralPath $migrationDirectory -PathType Container)) {
    throw "Flyway migration directory is missing: $migrationDirectory"
}

function Read-DotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*([^#][^=]*)=(.*)$') {
            $values[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
    return $values
}

function Resolve-Port {
    param(
        [int]$ExplicitPort,
        [string]$EnvironmentName,
        [int]$DefaultPort,
        [hashtable]$EnvironmentValues
    )

    $candidate = if ($ExplicitPort -gt 0) {
        $ExplicitPort
    } elseif ($EnvironmentValues.ContainsKey($EnvironmentName)) {
        $parsed = 0
        if (-not [int]::TryParse($EnvironmentValues[$EnvironmentName], [ref]$parsed)) {
            throw "Invalid port in ${EnvironmentName}: $($EnvironmentValues[$EnvironmentName])"
        }
        $parsed
    } else {
        $DefaultPort
    }
    if ($candidate -lt 1 -or $candidate -gt 65535) {
        throw "Port for $EnvironmentName is outside 1-65535: $candidate"
    }
    return $candidate
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & docker @script:composeArgs @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed: $($Arguments -join ' ')"
    }
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed ($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
        }
    }
    finally {
        Pop-Location
    }
}

function Require-HttpStatus {
    param([string]$Uri, [int]$Expected = 200)

    $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 30
    if ($response.StatusCode -ne $Expected) {
        throw "Unexpected HTTP status for ${Uri}: $($response.StatusCode)"
    }
    return $response
}

function Get-ResponseText {
    param($Response)

    if ($Response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string]$Response.Content
}

function Invoke-MySqlQuery {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $result = $Sql | & docker @script:composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon'
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL evidence query failed: $Sql"
    }
    return @($result)
}

function Assert-ComposeHealthy {
    $rawServices = & docker @script:composeArgs ps --format json
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not read Docker Compose service state.'
    }
    $services = @($rawServices | ForEach-Object { $_ | ConvertFrom-Json })
    $expectedServices = @('mysql', 'backend', 'admin', 'client', 'technician')
    foreach ($serviceName in $expectedServices) {
        $service = @($services | Where-Object Service -eq $serviceName)
        if ($service.Count -ne 1 -or $service[0].State -ne 'running' -or $service[0].Health -ne 'healthy') {
            throw "Compose service is not healthy: $serviceName; state=$($service | ConvertTo-Json -Compress)"
        }
    }
}

function Assert-ApplicationEndpoints {
    $readiness = Require-HttpStatus "http://127.0.0.1:$BackendPort/face-next/actuator/health/readiness"
    $readinessText = Get-ResponseText $readiness
    $readinessBody = $readinessText | ConvertFrom-Json
    if ($readinessBody.status -ne 'UP') {
        throw "Backend readiness is not UP: $readinessText"
    }

    foreach ($port in @($AdminPort, $ClientPort, $TechnicianPort)) {
        $spa = Require-HttpStatus "http://127.0.0.1:$port/"
        if ((Get-ResponseText $spa) -notmatch '<div id="app"') {
            throw "SPA shell is incomplete on port $port."
        }
    }

    $loginBody = @{ username = 'admin'; password = 'Face@123' } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post `
        -Uri "http://127.0.0.1:$AdminPort/face-next/api/v3/auth/login" `
        -ContentType 'application/json' `
        -Body $loginBody `
        -TimeoutSec 30
    if (-not $login.data.access_token) {
        throw 'Admin login through the container reverse proxy did not return an access token.'
    }
}

$envValues = Read-DotEnv -Path $EnvFile
$BackendPort = Resolve-Port $BackendPort 'FACE_BACKEND_PORT' 8090 $envValues
$AdminPort = Resolve-Port $AdminPort 'FACE_ADMIN_PORT' 8081 $envValues
$ClientPort = Resolve-Port $ClientPort 'FACE_CLIENT_PORT' 8082 $envValues
$TechnicianPort = Resolve-Port $TechnicianPort 'FACE_TECHNICIAN_PORT' 8083 $envValues
$ports = @($BackendPort, $AdminPort, $ClientPort, $TechnicianPort)
$duplicatePorts = @($ports | Group-Object | Where-Object Count -gt 1)
if ($duplicatePorts.Count -gt 0) {
    throw "Host ports must be unique: $($duplicatePorts.Name -join ', ')"
}

$portEnvironment = [ordered]@{
    FACE_BACKEND_PORT = $BackendPort
    FACE_ADMIN_PORT = $AdminPort
    FACE_CLIENT_PORT = $ClientPort
    FACE_TECHNICIAN_PORT = $TechnicianPort
}
$originalEnvironment = @{}
foreach ($entry in $portEnvironment.GetEnumerator()) {
    $originalEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
    [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
}

$script:composeArgs = @(
    'compose',
    '--project-name', $ProjectName,
    '--project-directory', $Root,
    '--env-file', $EnvFile,
    '-f', $composeFile
)

$started = $false
try {
    # The project name is validated above; this removes only its disposable B0-R containers/volume.
    Invoke-Compose 'down' '--volumes' '--remove-orphans'

    $activePorts = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port
    $occupiedPorts = @($ports | Where-Object { $_ -in $activePorts })
    if ($occupiedPorts.Count -gt 0) {
        throw "Requested host port is already occupied: $($occupiedPorts -join ', '). Choose explicit free ports; automatic random selection is disabled."
    }
    Write-Output "PORT_PREFLIGHT=PASS;BACKEND=$BackendPort;ADMIN=$AdminPort;CLIENT=$ClientPort;TECHNICIAN=$TechnicianPort"

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-docker-contract.ps1') -Root $Root -EnvFile $EnvFile
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker contract verification failed before runtime acceptance.'
    }
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'test-b0-runtime-contract.ps1') -Root $Root
    if ($LASTEXITCODE -ne 0) {
        throw 'B0-R runtime contract verification failed.'
    }

    $maven = (Get-Command mvn.cmd -ErrorAction SilentlyContinue)
    if (-not $maven) { $maven = Get-Command mvn -ErrorAction Stop }
    $npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue)
    if (-not $npm) { $npm = Get-Command npm -ErrorAction Stop }

    Invoke-CheckedCommand $maven.Source @('-B', '-ntp', '-f', (Join-Path $Root 'backend-next\pom.xml'), 'test') $Root
    Write-Output 'BACKEND_TESTS=PASS'
    Invoke-CheckedCommand $npm.Source @('--prefix', (Join-Path $Root 'admin-next'), 'run', 'build') $Root
    Write-Output 'ADMIN_FRONTEND_BUILD=PASS'
    Invoke-CheckedCommand $npm.Source @('--prefix', (Join-Path $Root 'front-next'), 'run', 'build') $Root
    Write-Output 'CLIENT_FRONTEND_BUILD=PASS'

    $started = $true
    Invoke-Compose 'up' '-d' 'mysql' '--wait' '--wait-timeout' '300'
    Invoke-Compose '--profile' 'tools' 'build' 'migration'
    Invoke-Compose '--profile' 'tools' 'run' '--rm' 'migration' 'flyway:migrate'
    Write-Output 'DOCKER_FLYWAY_MIGRATE=PASS'
    Invoke-Compose '--profile' 'tools' 'run' '--rm' 'migration' 'flyway:validate'
    Write-Output 'DOCKER_FLYWAY_VALIDATE=PASS'

    $versionedMigrations = @(Get-ChildItem -LiteralPath $migrationDirectory -File | ForEach-Object {
        if ($_.Name -match '^V(?<Version>\d+(?:\.\d+)*)__.+\.sql$') {
            $Matches.Version
        }
    } | Sort-Object -Unique)
    if ($versionedMigrations.Count -eq 0) {
        throw 'The migration directory contains no versioned Flyway migrations.'
    }
    $historyVersions = @(Invoke-MySqlQuery "SELECT version FROM flyway_schema_history WHERE success = 1 AND type = 'SQL' AND version IS NOT NULL ORDER BY installed_rank;" |
        ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ } | Sort-Object -Unique)
    $failedMigrationCount = [int]((Invoke-MySqlQuery 'SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0;' | Select-Object -Last 1).Trim())
    $migrationDifference = @(Compare-Object -ReferenceObject $versionedMigrations -DifferenceObject $historyVersions)
    if ($failedMigrationCount -ne 0 -or $migrationDifference.Count -gt 0) {
        throw "Migration directory/history mismatch. Failed=$failedMigrationCount Difference=$($migrationDifference | ConvertTo-Json -Compress)"
    }
    $latestMigration = $versionedMigrations | Select-Object -Last 1
    Write-Output "MIGRATION_DIRECTORY_HISTORY_MATCH=PASS;COUNT=$($versionedMigrations.Count);LATEST=$latestMigration"

    Invoke-Compose 'up' '-d' '--build' '--wait' '--wait-timeout' '900'
    Assert-ComposeHealthy
    Assert-ApplicationEndpoints
    Write-Output 'DOCKER_INITIAL_HEALTH=PASS'

    Invoke-Compose 'restart'
    Invoke-Compose 'up' '-d' '--wait' '--wait-timeout' '900'
    Assert-ComposeHealthy
    Assert-ApplicationEndpoints
    Write-Output 'DOCKER_RESTART_HEALTH=PASS'

    Write-Output 'DOCKER_IMAGE_BUILD=PASS'
    Write-Output 'DOCKER_MYSQL_HEALTH=PASS'
    Write-Output "DOCKER_FLYWAY_VERSION=$latestMigration"
    Write-Output 'DOCKER_BACKEND_READINESS=UP'
    Write-Output 'DOCKER_ADMIN_PROXY_LOGIN=PASS'
    Write-Output 'DOCKER_ADMIN_SPA=PASS'
    Write-Output 'DOCKER_CLIENT_SPA=PASS'
    Write-Output 'DOCKER_TECHNICIAN_SPA=PASS'
    Write-Output 'DOCKER_COMPOSE_HEALTH=PASS'
    Write-Output 'DOCKER_RUNTIME_ACCEPTANCE=PASS'
}
finally {
    if ($started -and -not $KeepRunning) {
        & docker @script:composeArgs down --volumes --remove-orphans
        if ($LASTEXITCODE -ne 0) {
            Write-Warning 'Docker acceptance cleanup failed; inspect the explicitly named test project manually.'
        } else {
            Write-Output 'DOCKER_ACCEPTANCE_CLEANUP=PASS'
        }
    }
    foreach ($entry in $originalEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}
