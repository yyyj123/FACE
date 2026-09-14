param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.docker.test')
)

$ErrorActionPreference = 'Stop'

$required = @(
    'compose.yaml',
    '.env.docker.example',
    'backend-next/Dockerfile',
    'backend-next/.dockerignore',
    'admin-next/Dockerfile',
    'admin-next/nginx.conf',
    'admin-next/.dockerignore',
    'front-next/Dockerfile',
    'front-next/nginx.conf',
    'front-next/.dockerignore'
)

foreach ($relativePath in $required) {
    $path = Join-Path $Root $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing Docker deployment contract file: $relativePath"
    }
}

$compose = Get-Content -LiteralPath (Join-Path $Root 'compose.yaml') -Raw
foreach ($service in @('mysql:', 'migration:', 'backend:', 'admin:', 'client:')) {
    if ($compose -notmatch [regex]::Escape($service)) {
        throw "Compose service is missing: $service"
    }
}
if ($compose -match '(?im)^\s*(?:MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|DB_PASSWORD):\s*(?!\$\{)[^\s]+') {
    throw 'Compose contains an embedded database password.'
}
if ($compose -notmatch 'readiness' -or $compose -notmatch 'depends_on') {
    throw 'Compose must enforce backend readiness and dependency health.'
}
if ($compose -notmatch '/docker-entrypoint-initdb.d/001_face_salon_base.sql' -or $compose -notmatch 'SELECT 1 FROM shop') {
    throw 'Compose must load and verify the controlled base schema before Flyway starts.'
}
foreach ($portVariable in @('FACE_BACKEND_PORT', 'FACE_ADMIN_PORT', 'FACE_CLIENT_PORT', 'FACE_TECHNICIAN_PORT')) {
    if ($compose -notmatch [regex]::Escape("`${${portVariable}")) {
        throw "Compose host port is not parameterized with $portVariable."
    }
}
if ($compose -notmatch '(?m)^\s+migration:\s*$' -or
    $compose -notmatch 'target:\s*build' -or
    $compose -notmatch 'FLYWAY_LOCATIONS:\s*filesystem:/workspace/src/main/resources/db/migration' -or
    $compose -notmatch 'FLYWAY_BASELINE_ON_MIGRATE:\s*"true"' -or
    $compose -notmatch 'FLYWAY_BASELINE_VERSION:\s*"0"') {
    throw 'Compose must expose the repository migration directory to the isolated Flyway runner.'
}

$runtimeVerifier = Get-Content -LiteralPath (Join-Path $Root 'scripts/verify-docker-runtime.ps1') -Raw -Encoding UTF8
foreach ($flywayCommand in @('flyway:migrate', 'flyway:validate', 'flyway_schema_history')) {
    if ($runtimeVerifier -notmatch [regex]::Escape($flywayCommand)) {
        throw "Docker runtime verifier is missing dynamic Flyway evidence: $flywayCommand"
    }
}

$migrationDirectory = Join-Path $Root 'backend-next/src/main/resources/db/migration'
$versionedMigrations = @(Get-ChildItem -LiteralPath $migrationDirectory -File | Where-Object {
    $_.Name -match '^V\d+(?:\.\d+)*__.+\.sql$'
})
if ($versionedMigrations.Count -eq 0) {
    throw 'No versioned Flyway migration was discovered from the configured directory.'
}

$applicationConfig = Get-Content -LiteralPath (Join-Path $Root 'backend-next/src/main/resources/application.yml') -Raw
if ($applicationConfig -notmatch 'forward-headers-strategy:\s*framework') {
    throw 'Backend must honor trusted reverse-proxy headers for same-origin HTTPS requests.'
}
foreach ($nginxRelativePath in @('admin-next/nginx.conf', 'front-next/nginx.conf')) {
    $nginx = Get-Content -LiteralPath (Join-Path $Root $nginxRelativePath) -Raw
    if ($nginx -notmatch 'X-Forwarded-Proto\s+\$proxy_forwarded_proto' -or
        $nginx -notmatch 'X-Forwarded-Host\s+\$host') {
        throw "$nginxRelativePath must preserve trusted forwarded origin information."
    }
}

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Docker test environment file is missing: $EnvFile"
}

docker compose --project-directory $Root --env-file $EnvFile -f (Join-Path $Root 'compose.yaml') config --quiet
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose config validation failed.'
}

Write-Output 'DOCKER_CONTRACT=PASS'
