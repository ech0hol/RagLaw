# RagLaw RAG pipeline evaluation script
# Usage: .\scripts\eval-rag-quality.ps1 -AdminPassword admin12345

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminPassword = $(if ($env:RAGLAW_ADMIN_PASSWORD) { $env:RAGLAW_ADMIN_PASSWORD } elseif ($env:RAGLAW_SEED_ADMIN_PASSWORD) { $env:RAGLAW_SEED_ADMIN_PASSWORD } else { "admin12345" }),
    [string]$AdminEmail = "admin@raglaw.local",
    [ValidateSet("fulltext", "hybrid", "both")]
    [string]$RetrievalMode = "fulltext",
    [switch]$UseRealEmbedding,
    [switch]$AllowIncompleteCorpus
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Write-Section($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = $null,
        [object]$Body = $null,
        [hashtable]$Form = $null
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $uri = "$BaseUrl$Path"
    if ($Form) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Form $Form
    }
    if ($Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 5)
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
}

function Login-Admin {
    if (-not $AdminPassword) {
        $AdminPassword = if ($env:RAGLAW_SEED_ADMIN_PASSWORD) { $env:RAGLAW_SEED_ADMIN_PASSWORD } else { "admin12345" }
    }
    $resp = Invoke-Api -Method POST -Path "/api/v1/auth/login" -Body @{
        email = $AdminEmail
        password = $AdminPassword
    }
    return $resp.data.accessToken
}

function Upload-And-Ingest {
    param(
        [string]$Token,
        [string]$FilePath,
        [string]$CategoryId,
        [switch]$Approve
    )
    $uploadJson = curl.exe -s -X POST "$BaseUrl/api/v1/admin/documents/upload" `
        -H "Authorization: Bearer $Token" `
        -F "file=@$FilePath" `
        -F "categoryId=$CategoryId"
    $upload = $uploadJson | ConvertFrom-Json
    if (-not $upload.success) {
        throw "Upload failed: $uploadJson"
    }
    $docId = $upload.data.id
    $ingest = Invoke-Api -Method POST -Path "/api/v1/admin/documents/$docId/ingest" -Token $Token
    $status = [string]$ingest.data.status
    if ($Approve -and $status -eq "AWAITING_APPROVAL") {
        Invoke-Api -Method POST -Path "/api/v1/admin/approvals/$docId/approve" -Token $Token | Out-Null
        $status = "INDEXED"
    }
    return [PSCustomObject]@{
        Id = $docId
        Title = $upload.data.title
        Status = $status
        CategoryId = $CategoryId
    }
}

function Invoke-AguiQuery {
    param(
        [string]$Token,
        [string]$Message,
        [string]$AgentCode = "STATUTE"
    )
    $headers = @{
        Authorization = "Bearer $Token"
        Accept = "text/event-stream"
    }
    $body = @{
        message = $Message
        agentCode = $AgentCode
    } | ConvertTo-Json
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $bodyBytes = $utf8.GetBytes($body)
    $resp = Invoke-WebRequest -Method POST -Uri "$BaseUrl/api/v1/agui/run" -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bodyBytes -UseBasicParsing
    $text = $resp.Content
    $refCount = ([regex]::Matches($text, "event:reference")).Count
    $doneMatch = [regex]::Match($text, "event:done\s+data:(\{.*\})")
    $done = if ($doneMatch.Success) { $doneMatch.Groups[1].Value | ConvertFrom-Json } else { $null }
    return [PSCustomObject]@{
        ReferenceCount = $refCount
        Answer = $done.content
        LatencyMs = $done.latencyMs
        RawSse = $text
    }
}

function Invoke-Mysql {
    param([string]$Sql)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $out = docker exec raglaw-mysql mysql -uraglaw -praglaw --default-character-set=utf8mb4 raglaw -N -e $Sql 2>&1 | Where-Object { $_ -is [string] -and $_ -notmatch 'Warning' }
    $ErrorActionPreference = $prev
    return $out
}

function Invoke-EsCount {
    param([string]$IndexName = "raglaw_chunks")
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $out = curl.exe -s "http://localhost:9200/$IndexName/_count" 2>&1
    $ErrorActionPreference = $prev
    if ($out -match '"count"\s*:\s*(\d+)') {
        return $Matches[1]
    }
    return "n/a"
}

function Test-MysqlRetrieval {
    param([string]$Query)
    $escaped = $Query.Replace("'", "''")
    $sql = "SELECT c.id, LEFT(c.content, 80), c.l2_path, c.l3_path, MATCH(c.content) AGAINST('$escaped' IN NATURAL LANGUAGE MODE) AS score FROM raglaw_document_chunk c INNER JOIN raglaw_document d ON d.id = c.document_id WHERE d.status = 'INDEXED' AND MATCH(c.content) AGAINST('$escaped' IN NATURAL LANGUAGE MODE) ORDER BY score DESC LIMIT 5;"
    return Invoke-Mysql -Sql $sql
}

Write-Section "Health check"
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -TimeoutSec 5
    Write-Host "Backend: $($health.data.status)"
    if ($health.data.rag) {
        $rag = $health.data.rag
        Write-Host "  elasticsearch: $($rag.elasticsearchEnabled), embedding configured: $($rag.embeddingConfigured), hybrid ready: $($rag.hybridRetrievalReady)"
    }
} catch {
    throw "Backend not running. Start with: cd backend; mvn -pl raglaw-server spring-boot:run"
}

$hybridReady = $false
$embeddingMock = $true
if ($health.data.rag) {
    $hybridReady = [bool]$health.data.rag.hybridRetrievalReady
    if ($null -ne $health.data.rag.embeddingMock) {
        $embeddingMock = [bool]$health.data.rag.embeddingMock
    }
}
if ($UseRealEmbedding) {
    $embeddingMock = $false
}
if ($RetrievalMode -eq "hybrid" -and -not $hybridReady) {
    Write-Host "Hybrid mode requested but ELASTICSEARCH_ENABLED + EMBEDDING_ENABLED are not both active." -ForegroundColor Yellow
    Write-Host "Set ELASTICSEARCH_ENABLED=true, EMBEDDING_ENABLED=true; with RAGLAW_LLM_MOCK=true mock vectors work without DASHSCOPE_API_KEY." -ForegroundColor Yellow
    Write-Host "Re-ingest fixtures after enabling, then re-run." -ForegroundColor Yellow
}

Write-Section "Login"
$token = Login-Admin
Write-Host "OK"

Write-Section "Apply L3 category seed"
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
Get-Content "$RepoRoot\docs\sql\mysql\004_seed_l3_categories.sql" -Raw | docker exec -i raglaw-mysql mysql -uraglaw -praglaw raglaw *> $null
Get-Content "$RepoRoot\docs\sql\mysql\005_seed_social_l3_category.sql" -Raw | docker exec -i raglaw-mysql mysql -uraglaw -praglaw raglaw *> $null
$ErrorActionPreference = $prevEap
Write-Host "L3 categories synced"

Write-Section "Upload and ingest fixtures"

$fixtures = @(
    @{ Path = "$RepoRoot\docs\fixtures\statutes\labor-contract-law-excerpt.md"; Category = "cat_l3_statute_civil_labor"; Approve = $false },
    @{ Path = "$RepoRoot\docs\fixtures\statutes\civil-code-contract-excerpt.md"; Category = "cat_l3_statute_civil_contract"; Approve = $false },
    @{ Path = "$RepoRoot\docs\fixtures\cases\labor-overtime-case.md"; Category = "cat_l3_case_civil_labor"; Approve = $true },
    @{ Path = "$RepoRoot\docs\fixtures\statutes\social-insurance-excerpt.md"; Category = "cat_l3_statute_social_general"; Approve = $false },
    @{ Path = "$RepoRoot\docs\fixtures\statutes\medical-insurance-remote-settlement.md"; Category = "cat_l3_statute_social_general"; Approve = $false }
)
$docs = @()
foreach ($fx in $fixtures) {
    try {
        $doc = Upload-And-Ingest -Token $token -FilePath $fx.Path -CategoryId $fx.Category -Approve:($fx.Approve)
        $docs += $doc
        Write-Host "  $($doc.Title) -> $($doc.Status)"
    } catch {
        Write-Host "  SKIP $($fx.Path): $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Section "recall-benchmark.json (knowledge search API)"
$benchmarkPath = "$RepoRoot\docs\evaluation\recall-benchmark.json"
$benchmarkJson = [System.IO.File]::ReadAllText($benchmarkPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$benchmarkQueries = @()
foreach ($item in $benchmarkJson.queries) {
    $encoded = [uri]::EscapeDataString($item.text)
    $search = Invoke-Api -Method GET -Path "/api/v1/knowledge/search?q=$encoded&pageSize=10" -Token $token
    $hits = @($search.data.items)
    $hitCount = $hits.Count
    $top1Path = if ($hitCount -gt 0) { [string]$hits[0].path } else { "" }
    $distinctDocs = @($hits | ForEach-Object { $_.documentId } | Select-Object -Unique).Count
    $minHits = [int]$item.minHitCount
    $prefixOk = $true
    if ($item.PSObject.Properties.Name -contains "top1PathPrefix" -and $item.top1PathPrefix) {
        $prefixOk = $top1Path.StartsWith([string]$item.top1PathPrefix)
    }
    $distinctOk = $true
    if ($item.PSObject.Properties.Name -contains "minDistinctDocuments" -and $item.minDistinctDocuments) {
        $distinctOk = $distinctDocs -ge [int]$item.minDistinctDocuments
    }
    $pass = ($hitCount -ge $minHits) -and $prefixOk -and $distinctOk
    $status = if ($pass) { "PASS" } else { "FAIL" }
    Write-Host "  [$status] $($item.text) hits=$hitCount top1=$top1Path docs=$distinctDocs"
    $benchmarkQueries += [PSCustomObject]@{
        text = $item.text
        minHitCount = $minHits
        top1PathPrefix = $item.top1PathPrefix
        hitCount = $hitCount
        top1Path = $top1Path
        distinctDocuments = $distinctDocs
        pass = $pass
    }
}
$benchmarkPassed = @($benchmarkQueries | Where-Object { $_.pass }).Count
$benchmarkTotal = $benchmarkQueries.Count
$benchmarkRate = if ($benchmarkTotal -gt 0) { [math]::Round($benchmarkPassed / $benchmarkTotal, 4) } else { 0 }
$benchmarkGatePass = $benchmarkRate -ge 0.9
Write-Host "Benchmark pass rate: $benchmarkPassed/$benchmarkTotal ($benchmarkRate) gate>=0.9: $benchmarkGatePass"

Write-Section "MySQL fulltext diagnostic (sample)"
$queriesZh = @(
    [char]0x62D6 + [char]0x6B20 + [char]0x5DE5 + [char]0x8D44,
    [char]0x8FDD + [char]0x7EA6 + [char]0x91D1 + [char]0x8FC7 + [char]0x9AD8,
    [char]0x52A0 + [char]0x73ED + [char]0x8D39
)
$retrievalReport = @()
foreach ($q in $queriesZh) {
    $rows = Test-MysqlRetrieval -Query $q
    $count = if ($rows) { ($rows | Measure-Object).Count } else { 0 }
    $retrievalReport += [PSCustomObject]@{ Query = $q; HitCount = $count }
}

Write-Section "AG-UI E2E (STATUTE)"
$q1 = [char]0x516C + [char]0x53F8 + [char]0x62D6 + [char]0x6B20 + [char]0x5DE5 + [char]0x8D44 + [char]0x52B3 + [char]0x52A8 + [char]0x8005 + [char]0x5982 + [char]0x4F55 + [char]0x7EF4 + [char]0x6743 + "?"
$q2 = [char]0x5408 + [char]0x540C + [char]0x7EA6 + [char]0x5B9A + [char]0x7684 + [char]0x8FDD + [char]0x7EA6 + [char]0x91D1 + [char]0x8FC7 + [char]0x9AD8 + [char]0x600E + [char]0x4E48 + [char]0x529E + "?"
$kwLaborPay = -join @([char]0x52B3,[char]0x52A8,[char]0x62A5,[char]0x916C)
$kwEconomic = -join @([char]0x7ECF,[char]0x6D4E,[char]0x8865,[char]0x507F)
$kwPenalty = -join @([char]0x8FDD,[char]0x7EA6,[char]0x91D1)
$aguiQueries = @(
    @{ Q = $q1; Keywords = @($kwLaborPay, $kwEconomic) },
    @{ Q = $q2; Keywords = @($kwPenalty) }
)
$answerReport = @()
foreach ($item in $aguiQueries) {
    Write-Host ""
    Write-Host "Question: $($item.Q)" -ForegroundColor Yellow
    $result = Invoke-AguiQuery -Token $token -Message $item.Q -AgentCode "STATUTE"
    Write-Host "  refs: $($result.ReferenceCount), latency: $($result.LatencyMs)ms"
    $matched = @($item.Keywords | Where-Object { $result.Answer -match [regex]::Escape($_) })
    Write-Host "  keyword hits: $($matched.Count)/$($item.Keywords.Count)"
    $answerReport += [PSCustomObject]@{
        Query = $item.Q
        References = $result.ReferenceCount
        LatencyMs = $result.LatencyMs
        KeywordHits = $matched.Count
        ExpectedKeywords = $item.Keywords.Count
        AnswerPreview = if ($result.Answer -and $result.Answer.Length -gt 200) { $result.Answer.Substring(0, 200) + "..." } else { $result.Answer }
    }
}

Write-Section "Summary"
$chunkCount = (Invoke-Mysql -Sql "SELECT COUNT(*) FROM raglaw_document_chunk;") | Select-Object -Last 1
$indexedCount = (Invoke-Mysql -Sql "SELECT COUNT(*) FROM raglaw_document WHERE status='INDEXED';") | Select-Object -Last 1
$vectorCount = $null
try {
    $vectorCount = Invoke-EsCount
} catch {
    $vectorCount = "n/a"
}
$aguiRefPass = @($answerReport | Where-Object { $_.References -gt 0 }).Count
Write-Host "Indexed docs: $indexedCount, chunks: $chunkCount, ES chunk docs: $vectorCount"
Write-Host "Benchmark: $benchmarkPassed/$benchmarkTotal (gate $($benchmarkGatePass))"
Write-Host "MySQL sample hit rate: $(($retrievalReport | Where-Object { $_.HitCount -gt 0 }).Count)/$($retrievalReport.Count)"
Write-Host "Answer with refs: $aguiRefPass/$($answerReport.Count)"
Write-Host "Eval mode: $RetrievalMode (hybrid ready: $hybridReady)"

$suffix = if ($RetrievalMode -eq "hybrid" -and $hybridReady) { "-hybrid" } elseif ($RetrievalMode -eq "both") { "-compare" } else { "" }
$reportPath = "$RepoRoot\docs\evaluation\rag-pipeline-eval-$(Get-Date -Format 'yyyy-MM-dd')$suffix.json"
New-Item -ItemType Directory -Force -Path (Split-Path $reportPath) | Out-Null
@{
    evaluatedAt = (Get-Date).ToString("o")
    retrievalMode = $RetrievalMode
    hybridReady = $hybridReady
    embeddingMock = $embeddingMock
    useRealEmbedding = [bool]$UseRealEmbedding
    documents = $docs
    benchmark = @{
        source = "docs/evaluation/recall-benchmark.json"
        passed = $benchmarkPassed
        total = $benchmarkTotal
        passRate = $benchmarkRate
        gatePass = $benchmarkGatePass
        queries = $benchmarkQueries
    }
    retrieval = @{
        engine = if ($hybridReady -and $RetrievalMode -ne "fulltext") { "Elasticsearch BM25 + vector RRF" } else { "MySQL FULLTEXT (ngram)" }
        queries = $retrievalReport
    }
    answers = $answerReport
    stats = @{
        indexedDocuments = [int]$indexedCount
        chunkCount = [int]$chunkCount
        vectorCount = $vectorCount
        aguiReferencePass = $aguiRefPass
        aguiReferenceTotal = $answerReport.Count
    }
} | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $reportPath
Write-Host "Report saved: $reportPath" -ForegroundColor Green

if (-not $benchmarkGatePass) {
    Write-Host "GATE B3 FAIL: recall pass rate $benchmarkRate < 0.9" -ForegroundColor Red
    if (-not $AllowIncompleteCorpus) {
        exit 1
    }
    Write-Host "AllowIncompleteCorpus set; not failing process." -ForegroundColor Yellow
}
