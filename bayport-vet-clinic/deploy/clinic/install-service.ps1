# Installs the clinic server so it starts when Windows starts and restarts after a crash.
# Run this in an elevated PowerShell window.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\ClinicCommon.ps1"

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run install-service.ps1 from an elevated PowerShell window."
}

$root = Get-BayportRoot
$app = Join-Path $root "app"
$web = Join-Path $app "web"
$config = Join-Path $root "config"
$dirs = @(
    $app,
    $web,
    $config,
    (Join-Path $root "data\uploads"),
    (Join-Path $root "logs"),
    (Join-Path $root "run")
)
New-Item -ItemType Directory -Force -Path $dirs | Out-Null

$envFile = Join-Path $config "bayport.env"
if (-not (Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "bayport.env.example") -Destination $envFile
    Write-Output "Created $envFile. Fill in the secrets before the first start. This script did not write real secrets."
}

$repoUi = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Copy-Item -Path (Join-Path $repoUi "*.html") -Destination $web -Force
$sourceAssets = Join-Path $repoUi "assets"
$destAssets = Join-Path $web "assets"
if (Test-Path -LiteralPath $sourceAssets) {
    if (Test-Path -LiteralPath $destAssets) {
        Remove-Item -LiteralPath $destAssets -Recurse -Force
    }
    Copy-Item -LiteralPath $sourceAssets -Destination $destAssets -Recurse -Force
}

$built = Join-Path $repoUi "bayport-backend\target\bayport-backend-0.0.1-SNAPSHOT.jar"
$destJar = Join-Path $app "bayport-backend.jar"
if (Test-Path -LiteralPath $built) {
    Copy-Item -LiteralPath $built -Destination $destJar -Force
    Write-Output "Copied the backend jar."
} elseif (-not (Test-Path -LiteralPath $destJar)) {
    Write-Output "Backend jar was not found. Build it with mvnw -DskipTests package, then run this script again."
}

$taskName = "Bayport Veterinary Clinic"
$serviceScript = Join-Path $PSScriptRoot "bayport-service.ps1"
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$serviceScript`""
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit (New-TimeSpan -Days 3650)
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -User "SYSTEM" -RunLevel Highest -Force | Out-Null
Write-Output "Scheduled task '$taskName' is installed. It starts at boot and the service script restarts Java if it exits."
Write-Output "Edit $envFile, create the MySQL database, then start the task or run start-bayport.ps1."
