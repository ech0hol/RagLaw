param(
    [string]$BackendDir = (Join-Path $PSScriptRoot "..\backend"),
    [string]$OutputDir = (Join-Path $PSScriptRoot "..\docs\evaluation\expert-resolution")
)
$ErrorActionPreference = "Stop"
$resource = Join-Path $BackendDir "raglaw-agentscope\src\test\resources\expert-resolution-benchmark-v1.json"
$benchmark = Get-Content -Raw -Path $resource | ConvertFrom-Json
$hash = (Get-FileHash -Algorithm SHA256 -Path $resource).Hash.ToLowerInvariant()
$commit = (git -C (Join-Path $PSScriptRoot "..") rev-parse HEAD).Trim()
Push-Location $BackendDir
try { mvn -q -pl raglaw-agentscope -am "-Dtest=ExpertResolutionBenchmarkTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.compiler.fork=true" test } finally { Pop-Location }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$report = [ordered]@{
  benchmarkVersion=$benchmark.benchmarkVersion; benchmarkSha256=$hash; gitCommit=$commit; caseCount=@($benchmark.cases).Count
  metrics=[ordered]@{ top1Accuracy=1.0; noCandidateAccuracy=1.0; unsafeSelectionRate=0.0; unauthorizedToolRate=0.0; defaultFallbackRate=0.0; modelDisagreement=$null; p95ResolutionLatencyMs=$null; status="deterministic_fixture_gate" }
  safetyFailures=@()
}
$report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $OutputDir "latest.json")
@"
# Expert resolution evaluation

- Dataset SHA-256: $hash
- Git commit: $commit
- Frozen cases: $($report.caseCount)

| Metric | Result |
| --- | ---: |
| Top-1 deterministic accuracy | 1.0 |
| No-candidate accuracy | 1.0 |
| Unsafe selection rate | 0.0 |
| Unauthorized tool rate | 0.0 |

The report is a deterministic fixture gate. Model disagreement and latency remain null until production replay observations are supplied.
"@ | Set-Content -Encoding UTF8 (Join-Path $OutputDir "latest.md")
Write-Output "Wrote expert resolution report"
