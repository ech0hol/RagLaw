param(
    [string]$BackendDir = (Join-Path $PSScriptRoot "..\backend"),
    [string]$OutputDir = (Join-Path $PSScriptRoot "..\docs\evaluation\workflow-state"),
    [switch]$SkipTest
)
$ErrorActionPreference = "Stop"
$resource = Join-Path $BackendDir "raglaw-agentscope\src\test\resources\workflow-state-benchmark-v1.json"
$benchmark = Get-Content -Raw -Path $resource | ConvertFrom-Json
$hash = (Get-FileHash -Algorithm SHA256 -Path $resource).Hash.ToLowerInvariant()
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$commit = (git -C $repo rev-parse HEAD).Trim()

if (-not $SkipTest) {
    Push-Location $BackendDir
    try {
        mvn -q -pl raglaw-agentscope -am "-Dtest=WorkflowStateBenchmarkTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.compiler.fork=true" test
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
if ($null -eq $thresholds -or $null -eq $thresholds.completionRate -or $null -eq $thresholds.expertTop1Accuracy -or $null -eq $thresholds.noCandidateAccuracy -or $null -eq $thresholds.p95ContextBudgetUtilization) { throw "workflow benchmark thresholds are required" }
$safetyFields = @("crossCaseLeakage", "unauthorizedTools", "duplicateAcceptedResults", "snapshotInconsistency", "factStatusPromotion", "criticalFactLoss", "evidencePointerLoss")
$safetyViolations = 0
$failed = @()
foreach ($case in $cases) {
    foreach ($field in @("id", "expectedCompletion", "expertTop1Correct", "noCandidateCorrect", "contextBudgetUtilization", "safety")) {
        if ($null -eq $case.$field) { throw "workflow benchmark case $($case.id) is missing $field" }
    }
    foreach ($field in $safetyFields) { $safetyViolations += [int]$case.safety.$field }
    if (-not $case.expectedCompletion -or -not $case.expertTop1Correct -or -not $case.noCandidateCorrect -or $case.contextBudgetUtilization -gt [double]$thresholds.p95ContextBudgetUtilization) { $failed += $case.id }
}
$completionRate = (@($cases | Where-Object expectedCompletion).Count / $cases.Count)
$expertTop1 = (@($cases | Where-Object expertTop1Correct).Count / $cases.Count)
$noCandidate = (@($cases | Where-Object noCandidateCorrect).Count / $cases.Count)
$budgetP95 = Percentile ([double[]]@($cases | ForEach-Object contextBudgetUtilization)) 0.95
$gatePassed = $safetyViolations -eq 0 -and $completionRate -ge [double]$thresholds.completionRate -and $expertTop1 -ge [double]$thresholds.expertTop1Accuracy -and $noCandidate -ge [double]$thresholds.noCandidateAccuracy -and $budgetP95 -le [double]$thresholds.p95ContextBudgetUtilization
$report = [ordered]@{
    benchmarkVersion = $benchmark.benchmarkVersion
    datasetSha256 = $hash
    gitCommit = $commit
    executionMode = "offline-fixture"
    modelVersion = "offline-fixture"
    thresholds = $thresholds
    configuration = [ordered]@{ workflowPolicyVersion = "workflow-state-safety-v1"; candidates = @("durable-coordinator") }
    scenarioCount = $cases.Count
    metrics = [ordered]@{ completionRate = [math]::Round($completionRate, 3); expertTop1Accuracy = [math]::Round($expertTop1, 3); noCandidateAccuracy = [math]::Round($noCandidate, 3); p95ContextBudgetUtilization = $budgetP95; p50LatencyMs = (Percentile ([double[]]@($cases | ForEach-Object p50LatencyMs)) 0.50); p95LatencyMs = (Percentile ([double[]]@($cases | ForEach-Object p95LatencyMs)) 0.95); averageCostUsd = [math]::Round((@($cases | Measure-Object estimatedCostUsd -Average).Average), 4) }
    safetyViolations = $safetyViolations
    failedCaseIds = $failed
    gatePassed = $gatePassed
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$report | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 (Join-Path $OutputDir "latest.json")
@"
# Workflow state evaluation ($($report.benchmarkVersion))

- Dataset SHA-256: $hash
- Git commit: $commit
- Frozen scenarios: $($report.scenarioCount)
- Configuration: offline fixture / workflow-state-safety-v1

| Metric | Result | Gate |
| --- | ---: | ---: |
| Completion rate | $($report.metrics.completionRate) | >= 0.95 |
| Expert top-1 accuracy | $($report.metrics.expertTop1Accuracy) | >= 0.90 |
| No-candidate accuracy | $($report.metrics.noCandidateAccuracy) | >= 0.98 |
| p95 context budget utilization | $($report.metrics.p95ContextBudgetUtilization) | <= 1.0 |
| Safety violations | $($report.safetyViolations) | 0 |
| p50 / p95 latency (ms) | $($report.metrics.p50LatencyMs) / $($report.metrics.p95LatencyMs) | observe |
| Average cost (USD) | $($report.metrics.averageCostUsd) | observe |

Gate passed: **$($report.gatePassed)**. Failed cases: $([string]::Join(', ', $report.failedCaseIds)).
"@ | Set-Content -Encoding UTF8 (Join-Path $OutputDir "latest.md")
if (-not $gatePassed) { exit 1 }
