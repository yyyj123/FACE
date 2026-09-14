$ErrorActionPreference = 'Stop'
$pidFile = 'E:\MySQL\face-mysql.pid'

if (-not (Test-Path $pidFile)) {
    Write-Output 'FACE MySQL is not running.'
    exit 0
}

$mysqlPid = Get-Content $pidFile
$process = Get-Process -Id $mysqlPid -ErrorAction SilentlyContinue
if ($process) {
    Stop-Process -Id $mysqlPid -Force
    $process.WaitForExit(5000)
}
Write-Output 'FACE MySQL stopped.'
