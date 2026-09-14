$ErrorActionPreference = 'Stop'
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0'
$config = 'E:\MySQL\face-my.ini'
$pidFile = 'E:\MySQL\face-mysql.pid'

if (Test-Path $pidFile) {
    $existingPid = Get-Content $pidFile -ErrorAction SilentlyContinue
    if ($existingPid -and (Get-Process -Id $existingPid -ErrorAction SilentlyContinue)) {
        Write-Output "FACE MySQL is already running (PID $existingPid)."
        exit 0
    }
}

Start-Process -FilePath "$mysqlHome\bin\mysqld.exe" `
    -ArgumentList "--defaults-file=$config" `
    -WorkingDirectory $mysqlHome `
    -WindowStyle Hidden

for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 250
    $listener = netstat -ano -p tcp | Select-String ':3308\s+.*LISTENING'
    if ($listener) {
        Write-Output 'FACE MySQL is listening on 127.0.0.1:3308.'
        exit 0
    }
}

throw 'FACE MySQL did not start. Check E:\MySQL\face-mysql.log.'
