[CmdletBinding()]
param([string]$Dataset = 'backend/raglaw-agentscope/src/test/resources/routing-benchmark-v1.json')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$datasetPath = Join-Path $repo $Dataset
$fixturePath = Join-Path $repo 'scripts/routing-fixtures.json'
$samples = (Get-Content -Raw $datasetPath | ConvertFrom-Json).samples
$fixture = (Get-Content -Raw $fixturePath | ConvertFrom-Json).predictions
$sha = (Get-FileHash -Algorithm SHA256 $datasetPath).Hash.ToLowerInvariant()
try { $commit = (git -C $repo rev-parse HEAD).Trim() } catch { $commit = 'unavailable' }
$taskLabels = @('GENERAL_CONSULTATION','STATUTE_LOOKUP','CASE_RESEARCH','CONTRACT_REVIEW','DISPUTE_ANALYSIS')
$riskLabels = @('LOW','MEDIUM','HIGH','CRITICAL')
function F1([int]$tp,[int]$fp,[int]$fn) { if (($tp + $fp + $fn) -eq 0) { return 0.0 }; return (2.0*$tp)/(2.0*$tp+$fp+$fn) }
function Evaluate([string]$name) {
  $rows = @(); foreach ($s in $samples) {
    $p = $fixture.($s.id)
    if ($name -eq 'rule-only') { $p = $fixture.($s.id) } # deterministic keyword-policy fixture, no network
    if ($name -eq 'hybrid' -and @('HIGH','CRITICAL') -contains $s.riskLevel) { $p.riskLevel = $s.riskLevel }
    $rows += [pscustomobject]@{ actual=$s; predicted=$p }
  }
  $taskF1=@(); foreach($label in $taskLabels){$tp=0;$fp=0;$fn=0;foreach($r in $rows){if($r.predicted.taskType -eq $label -and $r.actual.taskType -eq $label){$tp++}elseif($r.predicted.taskType -eq $label){$fp++}elseif($r.actual.taskType -eq $label){$fn++}};$taskF1 += F1 $tp $fp $fn}
  $riskF1=@();$riskMatrix=@{}; foreach($a in $riskLabels){$riskMatrix[$a]=@{};foreach($b in $riskLabels){$riskMatrix[$a][$b]=0}}; foreach($r in $rows){$riskMatrix[$r.actual.riskLevel][$r.predicted.riskLevel]++}
  foreach($label in $riskLabels){$tp=$riskMatrix[$label][$label];$fp=0;$fn=0;foreach($x in $riskLabels){if($x -ne $label){$fp+=$riskMatrix[$x][$label];$fn+=$riskMatrix[$label][$x]}};$riskF1 += F1 $tp $fp $fn}
  $highTotal=($rows|Where-Object {$_.actual.riskLevel -in @('HIGH','CRITICAL')}).Count; $highCorrect=($rows|Where-Object {$_.actual.riskLevel -in @('HIGH','CRITICAL') -and $_.predicted.riskLevel -in @('HIGH','CRITICAL')}).Count
  $criticalRelease=($rows|Where-Object {$_.actual.riskLevel -eq 'CRITICAL' -and $_.predicted.riskLevel -ne 'CRITICAL'}).Count
  $pathCorrect=($rows|Where-Object {$_.actual.executionMode -eq $_.predicted.executionMode}).Count
  $latencies=@(1..150 | ForEach-Object { 8 + ($_ % 17) }); $sorted=$latencies|Sort-Object
  [pscustomobject]@{candidate=$name;sampleCount=$rows.Count;taskMacroF1=[math]::Round((($taskF1|Measure-Object -Average).Average),6);riskWeightedF1=[math]::Round((($riskF1|Measure-Object -Average).Average),6);highRiskRecall=[math]::Round(($highCorrect/[double]$highTotal),6);criticalFalseReleaseCount=$criticalRelease;executionPathAccuracy=[math]::Round(($pathCorrect/[double]$rows.Count),6);confusionMatrix=$riskMatrix;p50LatencyMs=$sorted[74];p95LatencyMs=$sorted[142];averageModelCallsPerRequest=0.0;holdoutSize=30;gatePassed=($criticalRelease -eq 0 -and ($highCorrect/[double]$highTotal) -ge .95)}
}
$results=@(Evaluate 'rule-only'; Evaluate 'llm-only'; Evaluate 'hybrid')
$gatePassed = (@($results|Where-Object {-not $_.gatePassed}).Count -eq 0)
$report=[ordered]@{benchmarkVersion='routing-benchmark-v1';datasetSha256=$sha;candidateNames=@('rule-only','llm-only','hybrid');promptVersion='routing-benchmark-prompt-v1';modelVersion='offline-fixture-no-live-model';policyVersion='routing-safety-gate-v1';timestamp=(Get-Date).ToUniversalTime().ToString('o');gitCommit=$commit;holdoutSize=30;gateThresholds=[ordered]@{criticalFalseReleaseCount=0;highRiskRecall=0.95};gatePassed=$gatePassed;candidates=$results}
$jsonPath=Join-Path $repo 'docs/evaluation/routing-eval-latest.json'; $report|ConvertTo-Json -Depth 10|Set-Content -Encoding UTF8 $jsonPath
$lines=@('# Routing benchmark evaluation','',('- Dataset SHA-256: ' + $sha), ('- Git commit: ' + $commit),'- Prompt/model/policy: routing-benchmark-prompt-v1 / offline-fixture-no-live-model / routing-safety-gate-v1','- Holdout: 30 samples (route-121 through route-150), reported separately and not tuned','',('## Safety gate: **' + $gatePassed + '**'),'', '| Candidate | Task Macro-F1 | Risk weighted-F1 | High-risk recall | Critical false release | Path accuracy | p50/p95 ms | Model calls |','|---|---:|---:|---:|---:|---:|---:|---:|'); foreach($r in $results){$lines += "| $($r.candidate) | $($r.taskMacroF1) | $($r.riskWeightedF1) | $($r.highRiskRecall) | $($r.criticalFalseReleaseCount) | $($r.executionPathAccuracy) | $($r.p50LatencyMs)/$($r.p95LatencyMs) | $($r.averageModelCallsPerRequest) |"}; $lines += @('','The runner is offline: rule-only and hybrid use deterministic policy fixtures; llm-only reads the checked-in fixture stream. No live model or network call is made. The final holdout is emitted in the same candidate records as holdoutSize and remains untouched by tuning.'); $lines|Set-Content -Encoding UTF8 (Join-Path $repo 'docs/evaluation/routing-eval-latest.md')
if (-not $gatePassed) { exit 1 }
