param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [IO.Path]::GetFullPath($Root)
$failures = [Collections.Generic.List[string]]::new()

function Check-Pattern {
    param([string]$RelativePath, [string]$Pattern, [string]$Message)
    $path = Join-Path $Root $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $failures.Add("missing file: $RelativePath")
        return
    }
    $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    if ($content -notmatch $Pattern) { $failures.Add($Message) }
}

function Reject-Pattern {
    param([string]$RelativePath, [string]$Pattern, [string]$Message)
    $path = Join-Path $Root $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return }
    $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    if ($content -match $Pattern) { $failures.Add($Message) }
}

$frontShell = 'front-next/src/components/ClientShell.vue'
$frontRouter = 'front-next/src/router/index.ts'
$frontCss = 'front-next/src/styles/app.css'
$adminShell = 'admin-next/src/components/AppShell.vue'
$adminCss = 'admin-next/src/styles/app.css'

Check-Pattern $frontShell 'class="skip-link"' 'Customer shell must provide a keyboard skip link.'
Check-Pattern $frontShell 'id="main-content"' 'Customer main content must expose a stable skip target.'
Check-Pattern $frontShell 'tabindex="-1"' 'Customer main content must accept programmatic route focus.'
Check-Pattern $frontShell 'afterEach|watch\(' 'Customer shell must restore focus after route navigation.'
Reject-Pattern $frontShell 'to="/workbench"|auth\.isTechnician' 'Customer shell must not expose frozen technician navigation.'
Check-Pattern $frontRouter "component:\s*\(\)\s*=>\s*import\('../views/HomeView\.vue'\)" 'Customer routes must use route-level lazy loading.'
Reject-Pattern $frontRouter "import HomeView from|import BookingView from|import PointsStoreView from" 'Customer routes must not eagerly import page views.'
Check-Pattern $frontCss 'safe-area-inset-bottom' 'Customer mobile navigation must reserve the device safe area.'
Check-Pattern $frontCss 'prefers-reduced-motion:\s*reduce' 'Customer styles must provide reduced-motion behavior.'

Check-Pattern $adminShell 'class="skip-link"' 'Admin shell must provide a keyboard skip link.'
Check-Pattern $adminShell 'aria-expanded' 'Admin mobile menu button must expose expanded state.'
Check-Pattern $adminShell 'aria-controls="admin-sidebar"' 'Admin mobile menu button must identify its controlled navigation.'
Check-Pattern $adminShell 'keydown\.esc|Escape' 'Admin mobile drawer must close with Escape.'
Check-Pattern $adminShell 'watch\(' 'Admin mobile drawer must close on route navigation.'
Check-Pattern $adminShell 'id="main-content"' 'Admin main content must expose a stable skip target.'
Check-Pattern $adminShell 'tabindex="-1"' 'Admin main content must accept programmatic route focus.'
Reject-Pattern $adminShell 'SC7' 'Admin shell must not display a stale delivery-stage label.'
Check-Pattern $adminCss 'body\.nav-open|body\.mobile-nav-open' 'Admin mobile drawer must lock background scrolling.'
Check-Pattern $adminCss 'min-height:\s*44px' 'Admin mobile controls must meet the minimum touch target.'
Check-Pattern $adminCss 'prefers-reduced-motion:\s*reduce' 'Admin styles must provide reduced-motion behavior.'

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Output "WEB-R contract failure: $_" }
    throw "WEB-R UI contract failed with $($failures.Count) issue(s)."
}

Write-Output 'WEB_R_UI_STATIC_CONTRACT=PASS'
