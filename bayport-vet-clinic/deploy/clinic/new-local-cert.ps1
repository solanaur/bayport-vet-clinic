# Creates a clinic HTTPS certificate and records its location in bayport.env.
# The keystore password is written to the env file and is not printed.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\ClinicCommon.ps1"

$root = Get-BayportRoot
$config = Join-Path $root "config"
New-Item -ItemType Directory -Force -Path $config | Out-Null
$envFile = Join-Path $config "bayport.env"
if (-not (Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "bayport.env.example") -Destination $envFile
}
Import-BayportEnv $envFile

$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    throw "Set JAVA_HOME in $envFile before creating the certificate."
}
$keytool = Join-Path $javaHome "bin\keytool.exe"
if (-not (Test-Path -LiteralPath $keytool)) {
    throw "keytool was not found under JAVA_HOME."
}

$password = $env:SERVER_SSL_KEY_STORE_PASSWORD
if (-not $password) {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $password = [Convert]::ToBase64String($bytes)
}

$store = Join-Path $config "bayport.p12"
$cert = Join-Path $config "bayport.crt"
if (Test-Path -LiteralPath $store) {
    Remove-Item -LiteralPath $store -Force
}

$hostName = $env:COMPUTERNAME
$lanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
    Select-Object -ExpandProperty IPAddress -First 1
$san = "dns:$hostName,dns:localhost,ip:127.0.0.1"
if ($lanIp) {
    $san = "$san,ip:$lanIp"
}

& $keytool -genkeypair -alias bayport -keyalg RSA -keysize 2048 -validity 825 `
    -storetype PKCS12 -keystore $store -storepass $password -keypass $password `
    -dname "CN=$hostName, OU=Bayport Veterinary Clinic, O=Bayport, L=Paranaque, C=PH" `
    -ext "SAN=$san"
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed."
}
& $keytool -exportcert -alias bayport -keystore $store -storetype PKCS12 -storepass $password -rfc -file $cert | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Certificate export failed."
}

$storeUrl = "file:" + ($store -replace "\\", "/")
Set-BayportEnvValue -Path $envFile -Name "BAYPORT_SSL_ENABLED" -Value "true"
Set-BayportEnvValue -Path $envFile -Name "BAYPORT_SERVER_PORT" -Value "8443"
Set-BayportEnvValue -Path $envFile -Name "BAYPORT_ALLOW_LAN_HTTP" -Value "false"
Set-BayportEnvValue -Path $envFile -Name "SERVER_SSL_KEY_STORE" -Value $storeUrl
Set-BayportEnvValue -Path $envFile -Name "SERVER_SSL_KEY_STORE_PASSWORD" -Value $password
Set-BayportEnvValue -Path $envFile -Name "SERVER_SSL_KEY_STORE_TYPE" -Value "PKCS12"
Set-BayportEnvValue -Path $envFile -Name "SERVER_SSL_KEY_ALIAS" -Value "bayport"

Write-Output "Certificate created for $hostName."
Write-Output "Public certificate: $cert"
Write-Output "Keystore password was saved in bayport.env and was not printed."
Write-Output "On each staff computer, install bayport.crt into Trusted Root Certification Authorities."
Write-Output "Set SPRING_WEB_CORS_ALLOWED_ORIGINS to https://${hostName}:8443"
if ($lanIp) {
    Write-Output "If staff use the IP address, also allow https://${lanIp}:8443"
}
