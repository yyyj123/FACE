param(
    [ValidatePattern('^M[0-6]$')]
    [string]$Stage,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath('E:\face')
$stageLower = $Stage.ToLowerInvariant()
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot "backups\$stageLower"
}
$stagingRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot ("tmp\$stageLower-source-snapshot-" + [guid]::NewGuid().ToString('N')))
)
$allowedTempRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'tmp')) +
    [System.IO.Path]::DirectorySeparatorChar
if (-not $stagingRoot.StartsWith($allowedTempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe staging path: $stagingRoot"
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
$allowedBackupRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'backups')) +
    [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedOutput.StartsWith($allowedBackupRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output directory must be inside E:\face\backups: $resolvedOutput"
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$archivePath = Join-Path $resolvedOutput "face-$stageLower-source-$timestamp.zip"
$manifestPath = Join-Path $resolvedOutput "face-$stageLower-source-$timestamp.sha256"
$items = @(
    'PRODUCT.md',
    'DESIGN.md',
    'backend-next\pom.xml',
    'backend-next\src',
    'admin-next\package.json',
    'admin-next\package-lock.json',
    'admin-next\index.html',
    'admin-next\src',
    'admin-next\vite.config.ts',
    'front-next\package.json',
    'front-next\package-lock.json',
    'front-next\index.html',
    'front-next\src',
    'front-next\vite.config.ts',
    'docs',
    'scripts'
)

try {
    New-Item -ItemType Directory -Path $stagingRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
    foreach ($relativePath in $items) {
        $source = Join-Path $repoRoot $relativePath
        if (-not (Test-Path -LiteralPath $source)) {
            throw "Snapshot input missing: $relativePath"
        }
        $destination = Join-Path $stagingRoot $relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Recurse -Force
    }

    Compress-Archive `
        -Path (Join-Path $stagingRoot '*') `
        -DestinationPath $archivePath `
        -CompressionLevel Optimal
    $archive = Get-Item -LiteralPath $archivePath
    if ($archive.Length -le 0) {
        throw 'Source snapshot archive is empty.'
    }
    $hash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
    [System.IO.File]::WriteAllText(
        $manifestPath,
        "$hash  $($archive.Name)`r`n",
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Output "SOURCE_SNAPSHOT=$archivePath"
    Write-Output "SOURCE_SNAPSHOT_BYTES=$($archive.Length)"
    Write-Output "SOURCE_SNAPSHOT_SHA256=$hash"
    Write-Output "${Stage}_SOURCE_SNAPSHOT=PASS"
}
finally {
    if (Test-Path -LiteralPath $stagingRoot) {
        $verifiedStaging = [System.IO.Path]::GetFullPath($stagingRoot)
        if (-not $verifiedStaging.StartsWith($allowedTempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unsafe staging path: $verifiedStaging"
        }
        Remove-Item -LiteralPath $verifiedStaging -Recurse -Force
    }
}
