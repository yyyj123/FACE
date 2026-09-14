$ErrorActionPreference = 'Stop'

$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$source = Join-Path $PSScriptRoot '..\backend\db\legacy-car.sql'
$database = if ($env:DB_NAME) { $env:DB_NAME } else { 'face_salon' }
$hostName = if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }
$port = if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3308 }
$userName = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'face_app' }
$skipTables = @('chat', 'token')

if (-not (Test-Path -LiteralPath $mysql)) {
    throw "MySQL client not found: $mysql"
}
if (-not $env:DB_PASSWORD) {
    throw 'DB_PASSWORD is required. Compatibility installation never uses a hard-coded password.'
}

$sql = Get-Content -LiteralPath $source -Raw -Encoding UTF8
$matches = [regex]::Matches(
    $sql,
    '(?ms)^CREATE TABLE `(?<name>[^`]+)` \(.*?^\) ENGINE=.*?;$'
)

$statements = New-Object System.Collections.Generic.List[string]
$statements.Add("USE ``$database``;")
foreach ($match in $matches) {
    $name = $match.Groups['name'].Value
    if ($skipTables -contains $name) { continue }
    $statement = $match.Value -replace '^CREATE TABLE ', 'CREATE TABLE IF NOT EXISTS '
    $statements.Add($statement)
}

if ($statements.Count -lt 10) {
    throw "Compatibility schema extraction failed; only $($statements.Count - 1) tables were found."
}

$payload = ($statements -join "`r`n`r`n")
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:DB_PASSWORD
try {
    $payload | & $mysql `
        '--protocol=TCP' `
        "--host=$hostName" `
        "--port=$port" `
        "--user=$userName" `
        '--default-character-set=utf8mb4'
}
finally {
    $env:MYSQL_PWD = $previousPassword
}

if ($LASTEXITCODE -ne 0) {
    throw "Compatibility schema installation failed with exit code $LASTEXITCODE."
}

Write-Output "Installed $($statements.Count - 1) non-destructive compatibility tables in $database."
