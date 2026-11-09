$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$config = Join-Path $project 'backend\config\mysql-local.properties'
$mysql = 'D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe'

if (-not (Test-Path $config)) { throw "Missing local database credentials: $config" }
if (-not (Test-Path $mysql)) { throw "phpStudy MySQL client was not found: $mysql" }

$properties = @{}
Get-Content $config | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2] }
}
if (-not $properties['DB_USERNAME'] -or -not $properties['DB_PASSWORD']) {
    throw "DB_USERNAME or DB_PASSWORD is missing in $config"
}

$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $properties['DB_PASSWORD']
    $result = & $mysql -h 127.0.0.1 -P 3306 -u $properties['DB_USERNAME'] `
        -D lianzhi_attendance --batch --skip-column-names `
        -e 'SELECT DATABASE(), CURRENT_USER(); SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE(); SELECT COUNT(*) FROM user; SELECT COUNT(*) FROM work_attendance;'
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not connect to lianzhi_attendance. Start MySQL in phpStudy, then retry.'
    }
    Write-Host "Database and account: $($result[0])"
    Write-Host "Tables: $($result[1])"
    Write-Host "Users: $($result[2])"
    Write-Host "Work attendance records: $($result[3])"
    Write-Host 'Table names:'
    & $mysql -h 127.0.0.1 -P 3306 -u $properties['DB_USERNAME'] `
        -D lianzhi_attendance --batch --skip-column-names -e 'SHOW TABLES;'
    if ($LASTEXITCODE -ne 0) { throw 'Could not list lianzhi_attendance tables.' }
} finally {
    $env:MYSQL_PWD = $previousPassword
}
