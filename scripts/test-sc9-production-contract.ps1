param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [IO.Path]::GetFullPath($Root)

function Require-File {
    param([string]$RelativePath)
    $path = Join-Path $Root $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "SC9 required file is missing: $RelativePath"
    }
    return $path
}

function Require-Pattern {
    param([string]$Path, [string]$Pattern, [string]$Message)
    $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ($content -notmatch $Pattern) { throw $Message }
}

$compose = Require-File 'docker-compose.prod.yml'
$envExample = Require-File '.env.prod.example'
$gateway = Require-File 'prod/gateway/default.conf.template'
$prometheus = Require-File 'prod/monitoring/prometheus.yml'
$alerts = Require-File 'prod/monitoring/face-alerts.yml'
$alertmanager = Require-File 'prod/monitoring/alertmanager.yml'
$adapterReadme = Require-File 'prod/adapters/README.md'
$applicationProd = Require-File 'backend-next/src/main/resources/application-prod.yml'
$guard = Require-File 'backend-next/src/main/java/com/face/platform/production/ProductionSafetyGuard.java'
$policy = Require-File 'backend-next/src/main/java/com/face/platform/production/ProductionSafetyPolicy.java'

foreach ($script in @('prod-start.sh','prod-stop.sh','prod-backup.sh','prod-restore-drill.sh','prod-go-no-go.sh')) {
    $path = Require-File "scripts/$script"
    Require-Pattern $path '^#!/usr/bin/env sh' "SC9 Linux script must use a portable sh entrypoint: $script"
}

foreach ($service in @('gateway:','object-store:','object-init:','prometheus:','alertmanager:','pushgateway:')) {
    Require-Pattern $compose ([regex]::Escape($service)) "SC9 production Compose service is missing: $service"
}
Require-Pattern $compose 'SPRING_PROFILES_ACTIVE:\s*prod' 'Production backend must explicitly activate only the prod profile.'
Require-Pattern $compose 'SPRING_CONFIG_IMPORT:\s*configtree:/run/secrets/' 'Production backend must read Docker Secrets through Config Tree.'
Require-Pattern $compose 'MYSQL_PASSWORD_FILE' 'MySQL application password must use the official _FILE contract.'
Require-Pattern $compose 'MYSQL_ROOT_PASSWORD_FILE' 'MySQL root password must use the official _FILE contract.'
Require-Pattern $compose 'FACE_PAYMENT_DEMO_MOCK_ENABLED:\s*"false"' 'Production must explicitly disable DEMO_MOCK payment.'
Require-Pattern $compose 'profiles:\s*\r?\n\s*- frozen' 'The technician compatibility service must be frozen in production.'
Require-Pattern $compose '127\.0\.0\.1.*FACE_PROMETHEUS_PORT' 'Prometheus must be optional loopback-only and parameterized.'
Require-Pattern $compose 'target:\s*/run/secrets/tls_private_key' 'TLS private key must be mounted as a Docker Secret.'
Require-Pattern $compose '/app/adapters:ro' 'External adapter packages must be mounted read-only.'

foreach ($secret in @('db_password:','db_root_password:','object_store_access_key:','object_store_secret_key:','alert_webhook_url:','tls_certificate:','tls_private_key:')) {
    Require-Pattern $compose "(?m)^\s{2}$([regex]::Escape($secret))" "SC9 Docker Secret is missing: $secret"
}
if ((Get-Content -LiteralPath $compose -Raw -Encoding UTF8) -cmatch '(?m)^\s*(?:MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|DB_PASSWORD|MINIO_ROOT_PASSWORD):\s*(?!\$\{)[^\s]+') {
    throw 'SC9 production Compose embeds a secret value.'
}

Require-Pattern $gateway 'listen 8443 ssl' 'Production gateway must terminate TLS on a non-privileged container port.'
Require-Pattern $gateway 'return 308 https://' 'Production gateway must redirect HTTP to HTTPS.'
Require-Pattern $gateway 'Strict-Transport-Security' 'Production gateway must emit HSTS.'
Require-Pattern $gateway 'location /client/' 'Production gateway must expose /client/.'
Require-Pattern $gateway 'location /admin/' 'Production gateway must expose /admin/.'
Require-Pattern $gateway 'location /api/' 'Production gateway must expose /api/.'
Require-Pattern $gateway 'location /technician/' 'Production gateway must explicitly reject /technician/.'

Require-Pattern $prometheus 'face-backend' 'Prometheus must scrape the FACE backend.'
Require-Pattern $prometheus 'alertmanagers:' 'Prometheus must send alerts to Alertmanager.'
foreach ($alert in @('FaceBackendDown','FaceJvmHeapHigh','FaceBackupStale')) {
    Require-Pattern $alerts $alert "SC9 alert rule is missing: $alert"
}
Require-Pattern $alertmanager 'receivers:' 'Alertmanager receiver configuration is missing.'
Require-Pattern $alertmanager 'url_file:\s*/run/secrets/alert_webhook_url' 'Alertmanager must route alerts through a Secret-backed operator webhook.'

Require-Pattern $applicationProd 'demo-mock:\s*\r?\n\s+enabled:\s*false' 'Prod profile must disable DEMO_MOCK in backend configuration.'
Require-Pattern $applicationProd 'mode:\s*disabled' 'Prod profile must default external channels to disabled.'
Require-Pattern $guard '@Profile\("prod"\)' 'Production guard must only run in the prod profile.'
Require-Pattern $policy 'DEMO_MOCK' 'Production guard must reject DEMO_MOCK.'
Require-Pattern $policy '888888|demo SMS|SMS demo' 'Production guard must reject demo SMS behavior.'
Require-Pattern $policy 'SANDBOX' 'Production guard must reject payment Sandbox configuration.'
Require-Pattern $policy 'channel-evidence' 'Production guard must require external-channel evidence.'
Require-Pattern $adapterReadme 'small-payment' 'Payment adapter installation must require small-payment evidence.'
Require-Pattern $adapterReadme 'reconciliation' 'Payment adapter installation must require reconciliation evidence.'

foreach ($name in @('FACE_DOMAIN','FACE_PUBLIC_ORIGIN','FACE_HTTP_PORT','FACE_HTTPS_PORT','FACE_PROMETHEUS_PORT','FACE_SECRET_DIR','FACE_ADAPTER_DIR','FACE_CHANNEL_EVIDENCE_DIR')) {
    Require-Pattern $envExample "(?m)^$name=" "SC9 environment template is missing $name."
}

Write-Output 'SC9_PRODUCTION_STATIC_CONTRACT=PASS'
