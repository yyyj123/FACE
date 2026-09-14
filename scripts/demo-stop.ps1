param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.demo'),
    [string]$ProjectName = 'face-sc8-demo'
)
$ErrorActionPreference='Stop'; Set-StrictMode -Version Latest
$Root=[IO.Path]::GetFullPath($Root); $EnvFile=[IO.Path]::GetFullPath($EnvFile)
if ($ProjectName -notmatch '^face-sc8-[a-z0-9_-]+$') { throw 'ProjectName must start with face-sc8-.' }
$metadata=Join-Path $Root ".runtime\sc8-demo-$ProjectName.json"; $url=$null
if (Test-Path -LiteralPath $metadata) { $url=(Get-Content -LiteralPath $metadata -Raw | ConvertFrom-Json).publicUrl }
$args=@('compose','--project-name',$ProjectName,'--project-directory',$Root,'--env-file',$EnvFile,'-f',(Join-Path $Root 'compose.yaml'),'-f',(Join-Path $Root 'docker-compose.demo.yml'),'--profile','tunnel','down','--remove-orphans')
& docker @args; if ($LASTEXITCODE -ne 0) { throw 'Demo Compose stop failed.' }
if ($url) {
    $inactive=$false
    for ($i=0; $i -lt 6 -and -not $inactive; $i++) {
        try { $response=Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 10; $inactive=$response.StatusCode -ge 400 } catch { $inactive=$true }
        if (-not $inactive) { Start-Sleep -Seconds 2 }
    }
    if (-not $inactive) { throw 'Quick Tunnel URL remained publicly available after stop.' }
    Write-Output "DEMO_PUBLIC_STOP_INVALIDATION=PASS;URL=$url"
}
if (Test-Path -LiteralPath $metadata) { Remove-Item -LiteralPath $metadata -Force }
Write-Output 'DEMO_STOP=PASS'
