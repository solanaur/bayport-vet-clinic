# Bayport Veterinary Clinic — prints the project update guide to the console.
# Usage:  .\scripts\Show-ProjectUpdate.ps1
#         .\scripts\Show-ProjectUpdate.ps1 -Open   # also opens the markdown file

param(
    [switch]$Open
)

$RepoRoot = Split-Path $PSScriptRoot -Parent
$GuidePath = Join-Path $RepoRoot "PROJECT_UPDATE.md"

if (-not (Test-Path $GuidePath)) {
    Write-Host "PROJECT_UPDATE.md not found at: $GuidePath" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  BAYPORT VET CLINIC - PROJECT UPDATE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Get-Content $GuidePath -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^## ') {
        Write-Host $_ -ForegroundColor Yellow
    } elseif ($_ -match '^### ') {
        Write-Host $_ -ForegroundColor Green
    } elseif ($_ -match '^\|') {
        Write-Host $_ -ForegroundColor Gray
    } else {
        Write-Host $_
    }
}

Write-Host ""
Write-Host ("Full guide: " + $GuidePath) -ForegroundColor Gray

if ($Open) {
    Start-Process $GuidePath
}
