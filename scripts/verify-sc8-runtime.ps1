param(
    [ValidateSet(1,2)][int]$Run = 1,
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = (Join-Path (Split-Path -Parent $PSScriptRoot) '.env.demo.test'),
    [string]$ProjectName,
    [int]$DemoPort = 0,
    [string]$EvidenceDirectory
)
$ErrorActionPreference='Stop'; Set-StrictMode -Version Latest
$Root=[IO.Path]::GetFullPath($Root); $EnvFile=[IO.Path]::GetFullPath($EnvFile)
if(-not $ProjectName){$ProjectName="face-sc8-acceptance-$Run"}
if(-not $EvidenceDirectory){$EvidenceDirectory=Join-Path $Root "docs\verification\SC8\run-$Run"}
$EvidenceDirectory=[IO.Path]::GetFullPath($EvidenceDirectory);New-Item -ItemType Directory -Path $EvidenceDirectory -Force|Out-Null
$values=@{};foreach($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8){if($line -match '^\s*([^#][^=]*)=(.*)$'){$values[$Matches[1].Trim()]=$Matches[2].Trim()}}
if($DemoPort -eq 0){$DemoPort=[int]$values['FACE_DEMO_PORT']}
$compose=@('compose','--project-name',$ProjectName,'--project-directory',$Root,'--env-file',$EnvFile,'-f',(Join-Path $Root 'compose.yaml'),'-f',(Join-Path $Root 'docker-compose.demo.yml'))
function Sql([string]$Statement){$result=$Statement|& docker @compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon';if($LASTEXITCODE -ne 0){throw 'SC8 SQL assertion failed.'};return @($result)}
function Fingerprint(){return ((Sql "SELECT CONCAT((SELECT COUNT(*) FROM account),(SELECT COUNT(*) FROM appointment),(SELECT COUNT(*) FROM package_product),(SELECT COUNT(*) FROM points_ledger),(SELECT COUNT(*) FROM mall_order),(SELECT COUNT(*) FROM service_review),(SELECT COUNT(*) FROM after_sale_case),(SELECT COUNT(*) FROM mall_return_request));")|Select-Object -Last 1).Trim()}
function Assert-UrlInactive([string]$Url){for($i=0;$i-lt 6;$i++){try{$r=Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 10;if($r.StatusCode-ge 400){return}}catch{return};Start-Sleep -Seconds 2};throw "Old Quick Tunnel URL remained active: $Url"}

$started=$false
try{
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'test-sc8-demo-contract.ps1') -Root $Root
    if($LASTEXITCODE-ne 0){throw 'SC8 static contract failed.'}
    & mvn.cmd -B -ntp -f (Join-Path $Root 'backend-next\pom.xml') test
    if($LASTEXITCODE-ne 0){throw 'SC8 backend tests failed.'};Write-Output 'SC8_BACKEND_TESTS=PASS'
    & npm.cmd --prefix (Join-Path $Root 'admin-next') run build
    if($LASTEXITCODE-ne 0){throw 'SC8 admin build failed.'};Write-Output 'SC8_ADMIN_BUILD=PASS'
    & npm.cmd --prefix (Join-Path $Root 'front-next') run build
    if($LASTEXITCODE-ne 0){throw 'SC8 client build failed.'};Write-Output 'SC8_CLIENT_BUILD=PASS'

    & (Join-Path $PSScriptRoot 'demo-reset.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName -DemoPort $DemoPort -SkipBackup
    $started=$true
    $metadata=Get-Content -LiteralPath (Join-Path $Root ".runtime\sc8-demo-$ProjectName.json") -Raw|ConvertFrom-Json
    $firstUrl=[string]$metadata.publicUrl; if(-not $firstUrl){throw 'Initial Quick Tunnel URL is missing.'}

    $directoryVersions=@(Get-ChildItem -LiteralPath (Join-Path $Root 'backend-next\src\main\resources\db\migration') -File|ForEach-Object{if($_.Name-match '^V(?<v>\d+(?:\.\d+)*)__.+\.sql$'){$Matches.v}}|Sort-Object -Unique)
    $historyVersions=@(Sql "SELECT version FROM flyway_schema_history WHERE success=1 AND type='SQL' AND version IS NOT NULL ORDER BY installed_rank;"|ForEach-Object{([string]$_).Trim()}|Where-Object{$_}|Sort-Object -Unique)
    $migrationDifferences=@(Compare-Object $directoryVersions $historyVersions)
    if($migrationDifferences.Count-ne 0){throw 'SC8 Flyway directory/history mismatch.'}
    Write-Output "SC8_FLYWAY_DYNAMIC_HISTORY=PASS;COUNT=$($directoryVersions.Count);LATEST=$($directoryVersions|Select-Object -Last 1)"
    $baseline=Fingerprint;Write-Output "SC8_FIXED_SEED_FINGERPRINT=$baseline"

    & node (Join-Path $PSScriptRoot 'sc8-runtime-check.mjs') "--base=http://127.0.0.1:$DemoPort" "--project=$ProjectName" "--root=$($Root-replace '\\','/')" "--env-file=$($EnvFile-replace '\\','/')"
    if($LASTEXITCODE-ne 0){throw 'SC8 gate and business runtime failed.'}
    & node (Join-Path $PSScriptRoot 'sc8-browser-check.mjs') "--url=$firstUrl" "--password=$($values['FACE_DEMO_ACCESS_PASSWORD'])" "--output=$($EvidenceDirectory-replace '\\','/')" "--edge-dir=$((Join-Path $Root ".runtime\sc8-edge-$Run")-replace '\\','/')"
    if($LASTEXITCODE-ne 0){throw 'SC8 public desktop/mobile browser check failed.'}
    & (Join-Path $PSScriptRoot 'demo-backup.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName

    & (Join-Path $PSScriptRoot 'demo-reset.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName -DemoPort $DemoPort -SkipBackup
    Assert-UrlInactive $firstUrl;Write-Output 'SC8_RESET_OLD_PUBLIC_URL_INVALID=PASS'
    $after=Fingerprint;if($after-ne $baseline){throw "Fixed seed fingerprint changed after reset: $baseline -> $after"};Write-Output 'SC8_RESET_FIXED_SEED_RECOVERY=PASS'
    $current=(Get-Content -LiteralPath (Join-Path $Root ".runtime\sc8-demo-$ProjectName.json") -Raw|ConvertFrom-Json).publicUrl
    & node (Join-Path $PSScriptRoot 'sc8-browser-check.mjs') "--url=$current" "--password=$($values['FACE_DEMO_ACCESS_PASSWORD'])" "--output=$($EvidenceDirectory-replace '\\','/')/after-reset" "--edge-dir=$((Join-Path $Root ".runtime\sc8-edge-reset-$Run")-replace '\\','/')"
    if($LASTEXITCODE-ne 0){throw 'SC8 post-reset public browser check failed.'}
    & (Join-Path $PSScriptRoot 'demo-stop.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName
    $started=$false
    Write-Output "SC8_REPEATABLE_RUN_$Run=PASS"
}finally{
    if($started){try{& (Join-Path $PSScriptRoot 'demo-stop.ps1') -Root $Root -EnvFile $EnvFile -ProjectName $ProjectName}catch{Write-Warning $_}}
}
