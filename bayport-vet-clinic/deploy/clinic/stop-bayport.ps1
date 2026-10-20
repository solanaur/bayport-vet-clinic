# Ask the watchdog to stop, then stop the Java process.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\ClinicCommon.ps1"

$root = Get-BayportRoot
$run = Join-Path $root "run"
New-Item -ItemType Directory -Force -Path $run | Out-Null
$stopFile = Join-Path $run "stop.requested"
Set-Content -LiteralPath $stopFile -Value "stop" -Encoding ascii

$pidFile = Join-Path $run "bayport.pid"
if (Test-Path -LiteralPath $pidFile) {
    $processId = Get-Content -LiteralPath $pidFile | Select-Object -First 1
    if ($processId -match "^\d+$") {
        Stop-Process -Id ([int]$processId) -Force -ErrorAction SilentlyContinue
    }
}
Write-Output "Bayport stop requested."
