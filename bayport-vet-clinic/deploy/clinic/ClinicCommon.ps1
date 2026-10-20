# Shared helpers for the clinic PC scripts. Do not print environment values.

function Get-BayportRoot {
    if ($env:BAYPORT_HOME -and $env:BAYPORT_HOME.Trim() -ne "") {
        return $env:BAYPORT_HOME.Trim()
    }
    return "C:\Bayport"
}

function Import-BayportEnv {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing $Path. Copy deploy\clinic\bayport.env.example there and fill in the values."
    }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) {
            return
        }
        $split = $line.IndexOf("=")
        if ($split -lt 1) {
            return
        }
        $name = $line.Substring(0, $split).Trim()
        $value = $line.Substring($split + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Get-BayportJava {
    $home = $env:JAVA_HOME
    if ($home) {
        $candidate = Join-Path $home "bin\java.exe"
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    $fromPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($fromPath) {
        return $fromPath.Source
    }
    throw "Java 17 was not found. Set JAVA_HOME in bayport.env."
}

function Set-BayportEnvValue {
    param(
        [string]$Path,
        [string]$Name,
        [string]$Value
    )
    $lines = @()
    if (Test-Path -LiteralPath $Path) {
        $lines = @(Get-Content -LiteralPath $Path)
    }
    $found = $false
    $updated = foreach ($line in $lines) {
        if ($line -match "^\s*$([regex]::Escape($Name))\s*=") {
            $found = $true
            "$Name=$Value"
        } else {
            $line
        }
    }
    if (-not $found) {
        $updated += "$Name=$Value"
    }
    Set-Content -LiteralPath $Path -Value $updated -Encoding utf8
}
