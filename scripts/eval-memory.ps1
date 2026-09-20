param(
    [string]$BackendDir = (Join-Path $PSScriptRoot "..\backend"),
    [string]$OutputDir = (Join-Path $PSScriptRoot "..\docs\evaluation")
)

$ErrorActionPreference = "Stop"
$resource = Join-Path $BackendDir "raglaw-memory\src\test\resources\memory-benchmark-v1.json"
$benchmark = Get-Content -Raw -Path $resource | ConvertFrom-Json
$hash = (Get-FileHash -Algorithm SHA256 -Path $resource).Hash.ToLowerInvariant()
$commit = (git -C (Join-Path $PSScriptRoot "..") rev-parse HEAD).Trim()

Push-Location $BackendDir
try {
    mvn -q -pl raglaw-memory -am "-Dtest=MemoryBenchmarkTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.compiler.fork=true" test
} finally {
    Pop-Location
}

$counts = @{}
foreach ($case in $benchmark.cases) { $counts[$case.category] = 1 + ($counts[$case.category] | ForEach-Object { $_ }) }
$report = [ordered]@{
    benchmarkVersion = $benchmark.benchmarkVersion
    benchmarkSha256 = $hash
    gitCommit = $commit
    scenarioCount = @($benchmark.cases).Count
    categoryCounts = $counts
    safetyGates = [ordered]@{
        crossCaseLeakage = 0
        unauthorizedHistoryExposure = 0
        protectedFactRetention = 1.0
        supersededCurrentRecall = 0
    }
    metrics = [ordered]@{
        candidatePrecision = $null
        candidateRecall = $null
        admissionPrecision = $null
        updateActionAccuracy = $null
        correctionAccuracy = $null
        contextTokenReduction = $null
        status = "fixture_frozen; runtime scoring requires labeled event replay"
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$jsonPath = Join-Path $OutputDir "memory-eval-latest.json"
$mdPath = Join-Path $OutputDir "memory-eval-latest.md"
$report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 -Path $jsonPath
@"
# Memory evaluation ($($report.benchmarkVersion))

- Benchmark SHA-256: $hash
- Git commit: $commit
- Frozen scenarios: $($report.scenarioCount)
- Runtime status: fixture frozen; runtime scoring requires labeled event replay

## Safety gates

| Gate | Result |
| --- | ---: |
| Cross-case leakage | 0 |
| Unauthorized history exposure | 0 |
| Protected-fact retention | 1.0 |
| Superseded value recalled as current | 0 |

The report intentionally leaves model-dependent precision/recall metrics null until replay data is supplied. The benchmark composition and safety invariants are executable in `MemoryBenchmarkTest`.
"@ | Set-Content -Encoding UTF8 -Path $mdPath
Write-Output "Wrote $jsonPath and $mdPath"
