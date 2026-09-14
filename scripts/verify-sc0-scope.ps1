$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Split-Path -Parent $PSScriptRoot

function Read-Utf8 {
    param([string] $RelativePath)

    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required SC0 artifact is missing: $RelativePath"
    }
    return Get-Content -LiteralPath $path -Raw -Encoding utf8
}

function Assert-ContainsLiteral {
    param(
        [string] $RelativePath,
        [string] $Text,
        [string] $Message
    )

    $content = Read-Utf8 -RelativePath $RelativePath
    if (-not $content.Contains($Text)) {
        throw "$Message ($RelativePath)"
    }
}

foreach ($document in @('README.md', 'PRODUCT.md', 'DESIGN.md', 'CONTRIBUTING.md')) {
    Assert-ContainsLiteral -RelativePath $document -Text 'V3.0' -Message 'Core scope document must identify the active V3.0 baseline'
}

Assert-ContainsLiteral -RelativePath 'PRODUCT.md' -Text '/client/' -Message 'PRODUCT must name the customer-facing route'
Assert-ContainsLiteral -RelativePath 'PRODUCT.md' -Text '/admin/' -Message 'PRODUCT must name the operations route'
Assert-ContainsLiteral -RelativePath 'PRODUCT.md' -Text '/api/' -Message 'PRODUCT must name the unified backend route'
Assert-ContainsLiteral -RelativePath 'PRODUCT.md' -Text '## Frozen scope' -Message 'PRODUCT must state the frozen legacy scope'
Assert-ContainsLiteral -RelativePath 'DESIGN.md' -Text '## Active scope' -Message 'DESIGN must identify the active delivery boundary'
Assert-ContainsLiteral -RelativePath 'DESIGN.md' -Text '/client/' -Message 'DESIGN must constrain the customer navigation'
Assert-ContainsLiteral -RelativePath 'DESIGN.md' -Text '/admin/' -Message 'DESIGN must constrain the operations navigation'
Assert-ContainsLiteral -RelativePath 'CONTRIBUTING.md' -Text 'SC0' -Message 'Repository instructions must identify the active gate'
Assert-ContainsLiteral -RelativePath 'CONTRIBUTING.md' -Text 'SC1' -Message 'Repository instructions must preserve the next-stage gate'

$legacyPrdFiles = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'docs/specs') -File -Filter '*V2.1.md')
if ($legacyPrdFiles.Count -ne 1) {
    throw "Expected exactly one V2.1 PRD, found $($legacyPrdFiles.Count)."
}
$legacyPrd = Get-Content -LiteralPath $legacyPrdFiles[0].FullName -Raw -Encoding utf8
$legacyHeaderLength = [Math]::Min(600, $legacyPrd.Length)
if (-not $legacyPrd.Substring(0, $legacyHeaderLength).Contains('V3.0')) {
    throw 'The V2.1 PRD must be visibly superseded near its title.'
}

Assert-ContainsLiteral -RelativePath 'docs/architecture/v3-scope-module-matrix.md' -Text 'RETAIN' -Message 'The V3.0 module matrix must classify retained assets'
Assert-ContainsLiteral -RelativePath 'docs/architecture/v3-scope-module-matrix.md' -Text 'REUSE' -Message 'The V3.0 module matrix must classify reused assets'
Assert-ContainsLiteral -RelativePath 'docs/architecture/v3-scope-module-matrix.md' -Text 'FREEZE' -Message 'The V3.0 module matrix must classify frozen assets'
Assert-ContainsLiteral -RelativePath 'docs/architecture/v3-scope-module-matrix.md' -Text 'ADD' -Message 'The V3.0 module matrix must classify new work'

$loginView = Read-Utf8 -RelativePath 'front-next/src/views/LoginView.vue'
if ($loginView -match '8283') {
    throw 'The customer DEMO navigation still exposes the technician portal on port 8283.'
}

foreach ($preservedAsset in @(
    'front-next/src/components/TechnicianShell.vue',
    'backend-next/src/main/resources/db/migration/V2026072602__seed_beautician_demo_account.sql'
)) {
    $path = Join-Path $RepoRoot $preservedAsset
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Frozen compatibility asset was removed: $preservedAsset"
    }
}

Assert-ContainsLiteral -RelativePath 'compose.yaml' -Text '  technician:' -Message 'The frozen technician compatibility service must remain defined'

Write-Host 'SC0 scope contract: PASS'
