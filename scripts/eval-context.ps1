param(
    [string]$BackendDir = (Join-Path $PSScriptRoot "..\backend"),
    [string]$OutputDir = (Join-Path $PSScriptRoot "..\docs\evaluation\context"),
    [switch]$SkipTest
)
$ErrorActionPreference = "Stop"
$resource = Join-Path $BackendDir "raglaw-memory\src\test\resources\context-benchmark-v1.json"
$benchmark = Get-Content -Raw -Path $resource | ConvertFrom-Json
$hash = (Get-FileHash -Algorithm SHA256 -Path $resource).Hash.ToLowerInvariant()
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$commit = (git -C $repo rev-parse HEAD).Trim()

if (-not $SkipTest) {
    Push-Location $BackendDir
    try {
        mvn -q -pl raglaw-memory -am "-Dtest=ContextBenchmarkTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.compiler.fork=true" test
    } finally { Pop-Location }
}

function Percentile([double[]]$values, [double]$p) {
    $sorted = @($values | Sort-Object)
    if ($sorted.Count -eq 0) { return $null }
    $index = [math]::Ceiling($p * $sorted.Count) - 1
    return [math]::Round([double]$sorted[[math]::Max(0, $index)], 3)
}

$cases = @($benchmark.scenarios)
$thresholds = $benchmark.thresholds
if ($null -eq $thresholds -or $null -eq $thresholds.criticalFactRetention -or $null -eq $thresholds.evidencePointerRetention -or $null -eq $thresholds.p95InputBudgetUtilization) { throw "context benchmark thresholds are required" }
foreach ($case in $cases) {
    foreach ($field in @("id", "strategy", "criticalFactRetention", "evidencePointerRetention", "inputBudgetUtilization", "crossCaseLeakage", "unauthorizedHistoryExposure")) {
        if ($null -eq $case.$field) { throw "context benchmark case $($case.id) is missing $field" }
    }
}
$retentionFailures = @($cases | Where-Object { $_.criticalFactRetention -lt [double]$thresholds.criticalFactRetention -or $_.evidencePointerRetention -lt [double]$thresholds.evidencePointerRetention })
$budgetP95 = Percentile ([double[]]@($cases | ForEach-Object inputBudgetUtilization)) 0.95
$leakage = (@($cases | Where-Object { $_.crossCaseLeakage -ne 0 }).Count)
$unauthorized = (@($cases | Where-Object { $_.unauthorizedHistoryExposure -ne 0 }).Count)
$gatePassed = $retentionFailures.Count -eq 0 -and $budgetP95 -le [double]$thresholds.p95InputBudgetUtilization -and $leakage -eq 0 -and $unauthorized -eq 0
$strategies = [ordered]@{}
foreach ($group in ($cases | Group-Object strategy)) {
    $strategies[$group.Name] = [ordered]@{ count = $group.Count; p95InputBudgetUtilization = (Percentile ([double[]]@($group.Group | ForEach-Object inputBudgetUtilization)) 0.95); p50LatencyMs = (Percentile ([double[]]@($group.Group | ForEach-Object p50LatencyMs)) 0.50); p95LatencyMs = (Percentile ([double[]]@($group.Group | ForEach-Object p95LatencyMs)) 0.95); averageCostUsd = [math]::Round((@($group.Group | Measure-Object estimatedCostUsd -Average).Average), 4) }
}
$report = [ordered]@{ benchmarkVersion = $benchmark.benchmarkVersion; datasetSha256 = $hash; gitCommit = $commit; executionMode = "offline-fixture"; modelVersion = "offline-fixture"; thresholds = $thresholds; configuration = [ordered]@{ strategies = $benchmark.strategies; contextPolicyVersion = "context-retention-v1" }; scenarioCount = $cases.Count; strategies = $strategies; safetyGates = [ordered]@{ criticalFactRetention = if ($retentionFailures.Count -eq 0) { 1.0 } else { 0.0 }; evidencePointerRetention = if ($retentionFailures.Count -eq 0) { 1.0 } else { 0.0 }; crossCaseLeakage = $leakage; unauthorizedHistoryExposure = $unauthorized; p95InputBudgetUtilization = $budgetP95 }; failedCaseIds = @($retentionFailures | ForEach-Object id); gatePassed = $gatePassed }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$report | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 (Join-Path $OutputDir "latest.json")
@"
# Context evaluation ($($report.benchmarkVersion))

- Dataset SHA-256: $hash
- Git commit: $commit
- Execution mode: offline fixture (no live model or runtime replay)
- Model version: offline-fixture
- Frozen scenarios: $($report.scenarioCount)
- Configuration: four context assembly strategies / context-retention-v1

| Safety gate | Result |
| --- | ---: |
| Critical-fact retention | $($report.safetyGates.criticalFactRetention) (threshold $($thresholds.criticalFactRetention)) |
| Evidence-pointer retention | $($report.safetyGates.evidencePointerRetention) (threshold $($thresholds.evidencePointerRetention)) |
| Cross-case leakage | $($report.safetyGates.crossCaseLeakage) |
| Unauthorized history exposure | $($report.safetyGates.unauthorizedHistoryExposure) |
| p95 input-budget utilization | $($report.safetyGates.p95InputBudgetUtilization) (threshold $($thresholds.p95InputBudgetUtilization)) |

Gate passed: **$($report.gatePassed)**. Failed cases: $([string]::Join(', ', $report.failedCaseIds)). Strategy-level latency, budget, and cost comparisons are in `latest.json`.
"@ | Set-Content -Encoding UTF8 (Join-Path $OutputDir "latest.md")
if (-not $gatePassed) { exit 1 }
