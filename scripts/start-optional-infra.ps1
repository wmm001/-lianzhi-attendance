$ErrorActionPreference = 'Stop'

# Local demonstration only: both services are bound to loopback, never the LAN.
& docker container inspect lianzhi-redis *> $null
if ($LASTEXITCODE -eq 0) {
    & docker start lianzhi-redis | Out-Null
} else {
    & docker run -d --name lianzhi-redis --restart unless-stopped `
        -p '127.0.0.1:6379:6379' -v 'lianzhi-redis-local:/data' `
        'redis:7-alpine' redis-server --appendonly yes | Out-Null
}
if ($LASTEXITCODE -ne 0) { throw 'Redis container failed to start' }

& docker container inspect lianzhi-nacos *> $null
if ($LASTEXITCODE -eq 0) {
    & docker start lianzhi-nacos | Out-Null
} else {
    & docker run -d --name lianzhi-nacos --restart unless-stopped `
        -p '127.0.0.1:8848:8848' -p '127.0.0.1:9848:9848' `
        -v 'lianzhi-nacos-local:/home/nacos/data' `
        -e MODE=standalone -e NACOS_AUTH_ENABLE=false `
        -e JVM_XMS=256m -e JVM_XMX=512m -e JVM_XMN=128m `
        'nacos/nacos-server:v2.5.4' | Out-Null
}
if ($LASTEXITCODE -ne 0) { throw 'Nacos container failed to start' }

for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try {
        $health = Invoke-WebRequest -Uri 'http://127.0.0.1:8848/nacos/v1/console/health/readiness' -TimeoutSec 2
        if ($health.StatusCode -eq 200) { break }
    } catch { Start-Sleep -Seconds 2 }
}
if ($attempt -eq 60) { throw 'Nacos did not become ready within 2 minutes' }
if ((& docker exec lianzhi-redis redis-cli ping).Trim() -ne 'PONG') { throw 'Redis PING failed' }
Write-Host 'Redis ready on 127.0.0.1:6379'
Write-Host 'Nacos ready on http://127.0.0.1:8848/nacos/'
