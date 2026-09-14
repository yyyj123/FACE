$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Split-Path -Parent $PSScriptRoot

function Read-RequiredFile {
    param([string] $RelativePath)

    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required SC1 artifact is missing: $RelativePath"
    }
    return Get-Content -LiteralPath $path -Raw -Encoding utf8
}

function Assert-Token {
    param(
        [string] $RelativePath,
        [string] $Token
    )

    $content = Read-RequiredFile -RelativePath $RelativePath
    if (-not $content.Contains($Token)) {
        throw "SC1 artifact '$RelativePath' is missing token '$Token'."
    }
}

$Audit = 'docs/architecture/sc1-current-state-gap-audit.md'
$Migration = 'docs/architecture/sc1-data-reuse-migration-plan.md'
$State = 'docs/architecture/sc1-state-machine-transaction-boundaries.md'
$Adr = 'docs/adr/ADR-001-v3-incremental-domain-reuse.md'
$StagePlan = 'docs/architecture/sc1-follow-up-stage-file-plan.md'

foreach ($domain in @(
    'MEMBER',
    'APPOINTMENT',
    'ORDER',
    'PACKAGE',
    'AFTERSALE',
    'MARKETING',
    'NOTIFICATION',
    'INVENTORY'
)) {
    Assert-Token -RelativePath $Audit -Token $domain
}

foreach ($token in @('TABLES', 'SERVICES', 'APIS', 'PERMISSIONS', 'PAGES', 'EVIDENCE')) {
    Assert-Token -RelativePath $Audit -Token $token
}

foreach ($token in @('REUSE', 'ADD_MIGRATION', 'NO_DUPLICATE_MODEL', 'FLYWAY_APPEND_ONLY')) {
    Assert-Token -RelativePath $Migration -Token $token
}

foreach ($token in @('STATE_OWNER', 'TRANSACTION_OWNER', 'IDEMPOTENCY', 'LOCKING', 'OUTBOX')) {
    Assert-Token -RelativePath $State -Token $token
}

foreach ($token in @('Status: Accepted', 'Context', 'Decision', 'Consequences')) {
    Assert-Token -RelativePath $Adr -Token $token
}

foreach ($stage in @('SC2', 'SC3', 'SC4', 'SC5', 'SC6', 'SC7', 'SC8', 'SC9', 'SC10')) {
    Assert-Token -RelativePath $StagePlan -Token $stage
}

$migrationChanges = @(@(
        git -C $RepoRoot diff --name-only 'sc0/v1.0.0..HEAD' -- 'backend-next/src/main/resources/db/migration'
        git -C $RepoRoot diff --name-only -- 'backend-next/src/main/resources/db/migration'
        git -C $RepoRoot diff --cached --name-only -- 'backend-next/src/main/resources/db/migration'
        git -C $RepoRoot ls-files --others --exclude-standard -- 'backend-next/src/main/resources/db/migration'
    ) | Sort-Object -Unique)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect migration changes.'
}
if ($migrationChanges.Count -gt 0) {
    throw "SC1 must not modify Flyway migrations: $($migrationChanges -join ', ')"
}

$changedPaths = @(@(
        git -C $RepoRoot diff --name-only 'sc0/v1.0.0..HEAD'
        git -C $RepoRoot diff --name-only
        git -C $RepoRoot diff --cached --name-only
        git -C $RepoRoot ls-files --others --exclude-standard
    ) | Sort-Object -Unique)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect SC1 changed paths.'
}

$disallowedPaths = @($changedPaths | Where-Object {
        $_ -notmatch '^(AGENTS\.md|PRODUCT\.md|README\.md)$' -and
        $_ -notmatch '^docs/architecture/sc1-[a-z0-9-]+\.md$' -and
        $_ -notmatch '^docs/adr/ADR-001-v3-incremental-domain-reuse\.md$' -and
        $_ -notmatch '^docs/plans/2026-08-03-sc1\.md$' -and
        $_ -notmatch '^docs/verification/SC1/' -and
        $_ -notmatch '^scripts/verify-sc1-design-contract\.ps1$'
    })
if ($disallowedPaths.Count -gt 0) {
    throw "SC1 changed files outside its audit/design boundary: $($disallowedPaths -join ', ')"
}

Write-Host 'SC1 design contract: PASS'
