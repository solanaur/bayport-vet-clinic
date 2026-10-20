# Start the clinic server in this window. Stop it with Ctrl+C or stop-bayport.ps1.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\ClinicCommon.ps1"

$root = Get-BayportRoot
$envFile = Join-Path $root "config\bayport.env"
Import-BayportEnv $envFile
$java = Get-BayportJava
$app = Join-Path $root "app"
$jar = Join-Path $app "bayport-backend.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Missing $jar. Run install-service.ps1 after building the backend."
}
New-Item -ItemType Directory -Force -Path (Join-Path $root "logs") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $root "data\uploads") | Out-Null
Set-Location $app
& $java -jar $jar --spring.profiles.active=production
exit $LASTEXITCODE
