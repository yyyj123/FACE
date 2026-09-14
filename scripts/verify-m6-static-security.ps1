$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath('E:\face')
$sourceRoots = @(
    (Join-Path $repoRoot 'backend-next\src\main'),
    (Join-Path $repoRoot 'admin-next\src'),
    (Join-Path $repoRoot 'front-next\src')
)
$extensions = @('.java', '.yml', '.yaml', '.properties', '.ts', '.vue', '.js', '.mjs')
$files = @(
    foreach ($root in $sourceRoots) {
        Get-ChildItem -LiteralPath $root -Recurse -File |
            Where-Object { $_.Extension -in $extensions }
    }
)
if ($files.Count -eq 0) {
    throw 'No production source files were found for security scanning.'
}

$credentialPatterns = @(
    'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY',
    'AKIA[0-9A-Z]{16}',
    '(?i)(db_password|api_secret|client_secret|access_token)\s*[:=]\s*["''][^$<{][^"'']{7,}["'']'
)
$credentialHits = @($files | Select-String -Pattern $credentialPatterns -Encoding UTF8)
if ($credentialHits.Count -gt 0) {
    $credentialHits | Select-Object Path, LineNumber, Line | Format-Table -AutoSize
    throw 'Production source contains a possible embedded credential.'
}

$javaFiles = @($files | Where-Object { $_.Extension -eq '.java' })
$sensitiveLogPatterns = @(
    '(?i)(logger|log)\.(trace|debug|info|warn|error)\([^\r\n]*(phone|mobile|idCard|health|diagnosis|paymentCredential|secretHash)',
    '(?i)System\.(out|err)\.print[^\r\n]*(phone|mobile|idCard|health|diagnosis|paymentCredential|secretHash)'
)
$sensitiveLogHits = @($javaFiles | Select-String -Pattern $sensitiveLogPatterns -Encoding UTF8)
if ($sensitiveLogHits.Count -gt 0) {
    $sensitiveLogHits | Select-Object Path, LineNumber, Line | Format-Table -AutoSize
    throw 'Production logging may expose sensitive fields.'
}

$migrationFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend-next\src\main\resources\db\migration') -File)
$latestMigration = $migrationFiles.Name |
    Where-Object { $_ -match '^V\d+__.+\.sql$' } |
    Sort-Object |
    Select-Object -Last 1
if (-not $latestMigration) {
    throw "Unexpected latest migration: $latestMigration"
}
$latestMigrationVersion = [regex]::Match($latestMigration, '^V(?<Version>\d+)__').Groups['Version'].Value
if (-not $latestMigrationVersion) {
    throw "Could not derive the latest migration version from: $latestMigration"
}

Write-Output "M6_STATIC_FILES_SCANNED=$($files.Count)"
Write-Output 'M6_EMBEDDED_CREDENTIALS=0'
Write-Output 'M6_SENSITIVE_LOGGING_HITS=0'
Write-Output "M6_LATEST_MIGRATION_SOURCE=V$latestMigrationVersion"
Write-Output 'M6_STATIC_SECURITY=PASS'
