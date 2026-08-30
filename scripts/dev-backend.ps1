# Load root .env and start raglaw-server (Spring Boot does not read .env automatically).
# Usage: pwsh scripts/dev-backend.ps1 [-SkipBuild]

param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $RepoRoot ".env"
$EnvExample = Join-Path $RepoRoot ".env.example"

if (-not (Test-Path $EnvFile)) {
    if (-not (Test-Path $EnvExample)) {
        throw ".env.example not found at $EnvExample"
    }
    Copy-Item $EnvExample $EnvFile
    Write-Host "Created .env from .env.example" -ForegroundColor Yellow
}

function Import-DotEnvFile {
    param([string]$Path)
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) {
            return
        }
        $eq = $line.IndexOf("=")
        if ($eq -le 0) {
            return
        }
        $name = $line.Substring(0, $eq).Trim()
        $value = $line.Substring($eq + 1).Trim()
        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

Import-DotEnvFile -Path $EnvFile
Write-Host "Loaded environment from $EnvFile" -ForegroundColor Cyan

Set-Location (Join-Path $RepoRoot "backend")

if (-not $SkipBuild) {
    mvn -pl raglaw-server -am install -DskipTests
}

mvn -pl raglaw-server spring-boot:run
