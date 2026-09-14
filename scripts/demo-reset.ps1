param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.demo'),
    [string]$ProjectName = 'face-sc8-demo',
    [int]$DemoPort = 0,
    [switch]$NoTunnel,
    [switch]$SkipBackup
)
$ErrorActionPreference='Stop'; Set-StrictMode -Version Latest
$Root=[IO.Path]::GetFullPath($Root); $EnvFile=[IO.Path]::GetFullPath($EnvFile)
if ($ProjectName -notmatch '^face-sc8-[a-z0-9_-]+$') { throw 'ProjectName must start with face-sc8-; reset refused.' }
$compose=@('compose','--project-name',$ProjectName,'--project-directory',$Root,'--env-file',$EnvFile,'-f',(Join-Path $Root 'compose.yaml'),'-f',(Join-Path $Root 'docker-compose.demo.yml'))
$running=& docker @compose ps -q mysql
if ($running -and -not $SkipBackup) { & (Join-Path $PSScriptRoot 'demo-backup.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName }
& docker @compose --profile tunnel down --volumes --remove-orphans
if ($LASTEXITCODE -ne 0) { throw 'Demo reset could not remove the explicitly named disposable project.' }
# demo-start performs flyway:migrate and flyway:validate before restoring the fixed seed.
& (Join-Path $PSScriptRoot 'demo-start.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName -DemoPort $DemoPort -NoTunnel:$NoTunnel
if ($LASTEXITCODE -ne 0) { throw 'Demo reset restart failed.' }
Write-Output 'DEMO_RESET=PASS;FLYWAY=flyway:migrate,flyway:validate'
