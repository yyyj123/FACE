param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$runtimePath = Join-Path $Root 'scripts\verify-docker-runtime.ps1'
$contractPath = Join-Path $Root 'scripts\verify-docker-contract.ps1'
$staticSecurityPath = Join-Path $Root 'scripts\verify-m6-static-security.ps1'
$securityAcceptancePath = Join-Path $Root 'scripts\verify-m1-v3-security.ps1'
$composePath = Join-Path $Root 'compose.yaml'
$pomPath = Join-Path $Root 'backend-next\pom.xml'

$verificationScripts = @(
    $runtimePath,
    $contractPath,
    $staticSecurityPath,
    $securityAcceptancePath
)
foreach ($path in $verificationScripts + @($composePath, $pomPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing B0-R contract file: $path"
    }
}

$hardCodedVersionHits = @(Select-String -Path $verificationScripts -SimpleMatch '2026080103' -Encoding UTF8)
if ($hardCodedVersionHits.Count -gt 0) {
    throw "Verification scripts still hard-code Flyway 2026080103: $($hardCodedVersionHits.Path -join ', ')"
}

$runtime = Get-Content -LiteralPath $runtimePath -Raw -Encoding UTF8
foreach ($parameter in @('BackendPort', 'AdminPort', 'ClientPort', 'TechnicianPort')) {
    if ($runtime -notmatch "(?m)^\s*\[int\]\`$$parameter") {
        throw "Runtime verifier is missing the explicit $parameter parameter."
    }
}
foreach ($requiredMarker in @(
    'PORT_PREFLIGHT=PASS',
    'flyway:migrate',
    'flyway:validate',
    'flyway_schema_history',
    'MIGRATION_DIRECTORY_HISTORY_MATCH=PASS',
    'DOCKER_RESTART_HEALTH=PASS'
)) {
    if ($runtime -notmatch [regex]::Escape($requiredMarker)) {
        throw "Runtime verifier is missing required marker: $requiredMarker"
    }
}
if ($runtime -match '(?m)^\s*\$(?:backend|admin|client|technician)Port\s*=\s*82\d{2}\s*$') {
    throw 'Runtime verifier still assigns fixed 82xx acceptance ports.'
}

$compose = Get-Content -LiteralPath $composePath -Raw -Encoding UTF8
if ($compose -notmatch '(?m)^\s+migration:\s*$' -or
    $compose -notmatch 'target:\s*build' -or
    $compose -notmatch 'FLYWAY_LOCATIONS') {
    throw 'Compose is missing the isolated Flyway migration runner contract.'
}

$pom = Get-Content -LiteralPath $pomPath -Raw -Encoding UTF8
if ($pom -notmatch 'flyway-maven-plugin' -or $pom -notmatch '<artifactId>flyway-mysql</artifactId>') {
    throw 'Backend POM is missing the Flyway Maven runner and MySQL support.'
}

$migrationDirectory = Join-Path $Root 'backend-next\src\main\resources\db\migration'
$versionedMigrations = @(Get-ChildItem -LiteralPath $migrationDirectory -File | Where-Object {
    $_.Name -match '^V(?<Version>\d+(?:\.\d+)*)__.+\.sql$'
})
if ($versionedMigrations.Count -eq 0) {
    throw 'No versioned Flyway migrations were discovered dynamically.'
}
$duplicateVersions = @($versionedMigrations | ForEach-Object {
    if ($_.Name -match '^V(?<Version>\d+(?:\.\d+)*)__') { $Matches.Version }
} | Group-Object | Where-Object Count -gt 1)
if ($duplicateVersions.Count -gt 0) {
    throw "Duplicate Flyway migration versions: $($duplicateVersions.Name -join ', ')"
}

Write-Output "B0_VERSIONED_MIGRATIONS=$($versionedMigrations.Count)"
Write-Output 'B0_RUNTIME_CONTRACT=PASS'
