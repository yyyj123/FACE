param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.demo'),
    [string]$ProjectName = 'face-sc8-demo',
    [int]$DemoPort = 0,
    [switch]$NoTunnel
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [IO.Path]::GetFullPath($Root); $EnvFile = [IO.Path]::GetFullPath($EnvFile)
if ($ProjectName -notmatch '^face-sc8-[a-z0-9_-]+$') { throw 'ProjectName must start with face-sc8- and contain only lowercase safe characters.' }
if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) { throw "Demo env file is missing: $EnvFile" }

$values = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
    if ($line -match '^\s*([^#][^=]*)=(.*)$') { $values[$Matches[1].Trim()] = $Matches[2].Trim() }
}
if ($DemoPort -eq 0) {
    $parsed = 0
    if (-not $values.ContainsKey('FACE_DEMO_PORT') -or -not [int]::TryParse($values['FACE_DEMO_PORT'], [ref]$parsed)) {
        throw 'FACE_DEMO_PORT must be explicitly set in the env file or -DemoPort; automatic random selection is disabled.'
    }
    $DemoPort = $parsed
}
if ($DemoPort -lt 1 -or $DemoPort -gt 65535) { throw "FACE_DEMO_PORT is invalid: $DemoPort" }
$active = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port
if ($DemoPort -in $active) { throw "Requested FACE_DEMO_PORT $DemoPort is already occupied; choose another explicit port." }
$env:FACE_DEMO_PORT = [string]$DemoPort

$compose = @('compose','--project-name',$ProjectName,'--project-directory',$Root,'--env-file',$EnvFile,'-f',(Join-Path $Root 'compose.yaml'),'-f',(Join-Path $Root 'docker-compose.demo.yml'))
function Invoke-Compose([string[]]$Items) { & docker @compose @Items; if ($LASTEXITCODE -ne 0) { throw "docker compose failed: $($Items -join ' ')" } }

Invoke-Compose @('up','-d','mysql','--wait','--wait-timeout','300')
Invoke-Compose @('--profile','tools','build','migration')
Invoke-Compose @('--profile','tools','run','--rm','migration','flyway:migrate')
Invoke-Compose @('--profile','tools','run','--rm','migration','flyway:validate')
Invoke-Compose @('up','-d','--build','backend','admin','client','gateway','--wait','--wait-timeout','900')

$url = $null
if (-not $NoTunnel) {
    Invoke-Compose @('--profile','tunnel','up','-d','cloudflared')
    for ($attempt = 0; $attempt -lt 60 -and -not $url; $attempt++) {
        Start-Sleep -Milliseconds 500
        $logs = & docker @compose --profile tunnel logs --no-color cloudflared 2>&1 | Out-String
        $match = [regex]::Match($logs, 'https://[a-z0-9-]+\.trycloudflare\.com')
        if ($match.Success) { $url = $match.Value }
    }
    if (-not $url) { throw 'Quick Tunnel did not publish a trycloudflare.com URL.' }
}

$runtime = Join-Path $Root '.runtime'; New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$metadata = [ordered]@{ projectName=$ProjectName; demoPort=$DemoPort; publicUrl=$url; startedAt=(Get-Date).ToString('o') }
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime "sc8-demo-$ProjectName.json") -Encoding UTF8
Write-Output "DEMO_PORT_PREFLIGHT=PASS;PORT=$DemoPort"
Write-Output 'DEMO_FLYWAY_MIGRATE_VALIDATE=PASS'
Write-Output 'DEMO_GATEWAY_HEALTH=PASS'
if ($url) { Write-Output "DEMO_QUICK_TUNNEL_URL=$url" }
Write-Output 'DEMO_START=PASS'
