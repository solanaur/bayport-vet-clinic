# Keeps the clinic server running. If Java exits, start it again after 5 seconds.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\ClinicCommon.ps1"

$root = Get-BayportRoot
$run = Join-Path $root "run"
$app = Join-Path $root "app"
$logs = Join-Path $root "logs"
New-Item -ItemType Directory -Force -Path $run, $logs, (Join-Path $root "data\uploads") | Out-Null

$stopFile = Join-Path $run "stop.requested"
$pidFile = Join-Path $run "bayport.pid"
if (Test-Path -LiteralPath $stopFile) {
    Remove-Item -LiteralPath $stopFile -Force
}

Import-BayportEnv (Join-Path $root "config\bayport.env")
$java = Get-BayportJava
$jar = Join-Path $app "bayport-backend.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Missing $jar"
}

$outLog = Join-Path $logs "service-out.log"
$errLog = Join-Path $logs "service-err.log"

while (-not (Test-Path -LiteralPath $stopFile)) {
    $process = Start-Process -FilePath $java `
        -ArgumentList @("-jar", $jar, "--spring.profiles.active=production") `
        -WorkingDirectory $app `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru `
        -WindowStyle Hidden
    Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ascii
    Wait-Process -Id $process.Id
    if (Test-Path -LiteralPath $stopFile) {
        break
    }
    Start-Sleep -Seconds 5
}

if (Test-Path -LiteralPath $pidFile) {
    Remove-Item -LiteralPath $pidFile -Force
}
