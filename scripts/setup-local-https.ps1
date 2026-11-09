param([string]$LanIp = '10.105.16.122')
$ErrorActionPreference = 'Stop'

$project = Split-Path -Parent $PSScriptRoot
$output = Join-Path $project 'frontend\config\local-https'
$publicRoot = Join-Path $project 'frontend\public\local-ca.crt'
$publicRootDer = Join-Path $project 'frontend\public\local-ca.cer'
New-Item -ItemType Directory -Force -Path $output | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $publicRoot) | Out-Null

$root = New-SelfSignedCertificate -Type Custom -Subject 'CN=Lianzhi Local Demo CA' `
  -CertStoreLocation 'Cert:\CurrentUser\My' -KeyAlgorithm RSA -KeyLength 2048 `
  -KeyExportPolicy Exportable -HashAlgorithm SHA256 -KeyUsage CertSign,CRLSign `
  -TextExtension '2.5.29.19={critical}{text}CA=true&pathlength=0' `
  -NotAfter (Get-Date).AddMonths(3)
$server = New-SelfSignedCertificate -Type SSLServerAuthentication -Subject "CN=$LanIp" `
  -Signer $root -CertStoreLocation 'Cert:\CurrentUser\My' -KeyAlgorithm RSA `
  -KeyLength 2048 -KeyExportPolicy Exportable -HashAlgorithm SHA256 `
  -TextExtension "2.5.29.17={text}DNS=localhost&IPAddress=127.0.0.1&IPAddress=$LanIp" `
  -NotAfter (Get-Date).AddMonths(2)

$rsa = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey($server)
if (-not $rsa) { throw 'Could not export HTTPS private key' }
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText((Join-Path $output 'server.key'), $rsa.ExportPkcs8PrivateKeyPem(), $utf8)
[System.IO.File]::WriteAllText((Join-Path $output 'server.crt'), $server.ExportCertificatePem(), $utf8)
[System.IO.File]::WriteAllText($publicRoot, $root.ExportCertificatePem(), $utf8)
Export-Certificate -Cert $root -FilePath $publicRootDer -Type CERT -Force | Out-Null
Write-Host "Local HTTPS certificate generated for $LanIp."
Write-Host "CA public certificate: $publicRoot"
Write-Host "iPhone certificate download: $publicRootDer"
Write-Host 'The CA private key remains in the current Windows user certificate store.'
