# Fails when the repo is not safe to push.
# Run from anywhere inside the git checkout:
#   powershell -File scripts/pre-push-check.ps1
#   powershell -File scripts/pre-push-check.ps1 -SkipTests

param([switch]$SkipTests)

$ErrorActionPreference = "Stop"
$root = (& git rev-parse --show-toplevel).Trim()
if (-not $root) { Write-Error "Not inside a git repository."; exit 1 }
Set-Location $root

$failures = New-Object System.Collections.Generic.List[string]
$tracked = @(git ls-files)

foreach ($rel in $tracked) {
    $norm = $rel -replace '\\', '/'
    if ($norm -match '(^|/)node_modules/' -or $norm -match '(^|/)target/') {
        $failures.Add("Tracked build output: $norm")
    }
    $name = Split-Path $norm -Leaf
    $isExample = $name -eq ".env.example" -or $name -like "*.env.example"
    $isEnv = $name -eq ".env" -or ($name -like "*.env" -and -not $isExample) -or $name -like ".env.*"
    if ($isEnv -and -not $isExample) {
        $failures.Add("Tracked env file: $norm")
    }
    $full = Join-Path $root ($norm -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (Test-Path -LiteralPath $full) {
        $len = (Get-Item -LiteralPath $full).Length
        if ($len -gt 5MB -and $norm -notmatch 'maven-wrapper\.jar$') {
            $failures.Add(("Tracked file larger than 5 MB ({0:N1} MB): {1}" -f ($len / 1MB), $norm))
        }
    }
}

$secretRules = @(
    @{ Name = "aws-access-key"; Pattern = 'AKIA[0-9A-Z]{16}' },
    @{ Name = "private-key"; Pattern = '-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----' },
    @{ Name = "github-token"; Pattern = 'ghp_[A-Za-z0-9]{20,}' },
    @{ Name = "slack-token"; Pattern = 'xox[baprs]-[A-Za-z0-9-]{10,}' },
    @{ Name = "stripe-live-key"; Pattern = 'sk_live_[A-Za-z0-9]{10,}' },
    @{ Name = "google-api-key"; Pattern = 'AIza[0-9A-Za-z\-_]{20,}' },
    @{ Name = "jwt-default"; Pattern = '\$\{JWT_SECRET:([^}]+)\}' }
)

foreach ($rel in $tracked) {
    $norm = $rel -replace '\\', '/'
    if ($norm -match '\.(png|jpg|jpeg|gif|pdf|docx|jar|zip|db)$') { continue }
    $full = Join-Path $root ($norm -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $full)) { continue }
    $lineNo = 0
    foreach ($line in [IO.File]::ReadLines($full)) {
        $lineNo++
        if ($line -match 'replace-with|YOUR_|your-clinic|placeholder|example\.com') { continue }
        foreach ($rule in $secretRules) {
            if ($line -match $rule.Pattern) {
                if ($rule.Name -eq "jwt-default" -and $Matches[1].Trim().Length -lt 8) { continue }
                $failures.Add("Secret-like $($rule.Name) at ${norm}:${lineNo}")
            }
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Output "Pre-push check failed:"
    $failures | ForEach-Object { Write-Output $_ }
    exit 1
}

if ($SkipTests) {
    Write-Output "Pre-push scan passed (tests skipped)."
    exit 0
}

$backend = Join-Path $root "bayport-vet-clinic\bayport-backend"
if (-not (Test-Path (Join-Path $backend "mvnw.cmd"))) {
    $failures.Add("mvnw.cmd not found")
    Write-Output "Pre-push check failed: mvnw.cmd not found"
    exit 1
}
Push-Location $backend
try {
    & .\mvnw.cmd test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

Write-Output "Pre-push check passed."
exit 0
