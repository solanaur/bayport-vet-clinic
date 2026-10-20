# Removes the clinic startup task and asks the running server to stop.
$ErrorActionPreference = "Stop"
& "$PSScriptRoot\stop-bayport.ps1"
Unregister-ScheduledTask -TaskName "Bayport Veterinary Clinic" -Confirm:$false -ErrorAction SilentlyContinue
Write-Output "Scheduled task removed."
