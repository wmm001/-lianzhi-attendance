$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $project 'tmp'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Test-LocalPort([int]$port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(500)) { return $false }
        $client.EndConnect($pending)
        return $true
    } catch { return $false }
    finally { $client.Close() }
}

function Wait-LocalPort([int]$port, [string]$name) {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (Test-LocalPort $port) { Write-Host "$name ready on 127.0.0.1:$port"; return }
        Start-Sleep -Seconds 1
    }
    throw "$name did not start. Check logs in $logs"
}

$mysqlDir = 'D:\phpstudy_pro\Extensions\MySQL5.7.26'
$mysqlExe = Join-Path $mysqlDir 'bin\mysqld.exe'
$mysqlIni = Join-Path $mysqlDir 'my.ini'
if (-not (Test-Path $mysqlExe) -or -not (Test-Path $mysqlIni)) {
    throw 'phpStudy MySQL 5.7 was not found at D:\phpstudy_pro\Extensions\MySQL5.7.26'
}
if (-not (Test-LocalPort 3306)) {
    Start-Process -FilePath $mysqlExe -ArgumentList "--defaults-file=$mysqlIni", '--console' `
        -WorkingDirectory $mysqlDir -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'mysql.out.log') `
        -RedirectStandardError (Join-Path $logs 'mysql.err.log')
}
Wait-LocalPort 3306 'MySQL'

$pythonDir = Join-Path $project 'face-recognition'
$pythonExe = Join-Path $pythonDir '.venv\Scripts\python.exe'
if (-not (Test-LocalPort 5000)) {
    $env:MPLCONFIGDIR = Join-Path $logs 'matplotlib-cache'
    Start-Process -FilePath $pythonExe -ArgumentList 'app_opencv.py' `
        -WorkingDirectory $pythonDir -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'face.out.log') `
        -RedirectStandardError (Join-Path $logs 'face.err.log')
}
Wait-LocalPort 5000 'Face service'

$backendDir = Join-Path $project 'backend'
$javaExe = 'C:\Program Files\Java\jdk-17\bin\java.exe'
if (-not (Test-LocalPort 8080)) {
    Start-Process -FilePath $javaExe `
        -ArgumentList '-jar', 'target\attendance-system-1.0.0.jar', '--spring.profiles.active=dev' `
        -WorkingDirectory $backendDir -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'backend.out.log') `
        -RedirectStandardError (Join-Path $logs 'backend.err.log')
}
Wait-LocalPort 8080 'Java API'

$frontendDir = Join-Path $project 'frontend'
if (-not (Test-LocalPort 5173)) {
    Start-Process -FilePath 'C:\Program Files\nodejs\npm.cmd' `
        -ArgumentList 'run', 'dev', '--', '--host', '0.0.0.0' `
        -WorkingDirectory $frontendDir -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'frontend.out.log') `
        -RedirectStandardError (Join-Path $logs 'frontend.err.log')
}
Wait-LocalPort 5173 'Web UI'
Write-Host 'Open http://localhost:5173'
Write-Host 'For phone access, use http://<computer-WLAN-IP>:5173 on the same reachable Wi-Fi.'
