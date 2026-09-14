param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.demo'),
    [string]$ProjectName = 'face-sc8-demo',
    [string]$OutputDirectory
)
$ErrorActionPreference='Stop'; Set-StrictMode -Version Latest
$Root=[IO.Path]::GetFullPath($Root); $EnvFile=[IO.Path]::GetFullPath($EnvFile)
if ($ProjectName -notmatch '^face-sc8-[a-z0-9_-]+$') { throw 'ProjectName must start with face-sc8-.' }
if (-not $OutputDirectory) { $OutputDirectory=Join-Path $Root 'backups\demo' }
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory); $allowed=[IO.Path]::GetFullPath((Join-Path $Root 'backups'))
if (-not $OutputDirectory.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)) { throw 'Demo backups must stay under the repository backups directory.' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'; $target=Join-Path $OutputDirectory "face-demo-$stamp.sql"
$compose=@('compose','--project-name',$ProjectName,'--project-directory',$Root,'--env-file',$EnvFile,'-f',(Join-Path $Root 'compose.yaml'),'-f',(Join-Path $Root 'docker-compose.demo.yml'))
$dump=@(& docker @compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --single-transaction --routines --triggers --no-tablespaces -uroot face_salon')
if ($LASTEXITCODE -ne 0) { throw 'Demo mysqldump backup failed.' }
[IO.File]::WriteAllLines($target,[string[]]$dump,[Text.UTF8Encoding]::new($false))
if ((Get-Item -LiteralPath $target).Length -lt 1024) { throw 'Demo mysqldump backup failed.' }
Write-Output "DEMO_BACKUP=PASS;PATH=$target;BYTES=$((Get-Item -LiteralPath $target).Length)"
