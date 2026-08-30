# Audit MySQL chunks vs Elasticsearch index after pgvector -> ES migration.
# Usage:
#   .\scripts\audit-es-migration.ps1
#   .\scripts\audit-es-migration.ps1 -OutputJson docs/evaluation/es-migration-audit.json
#   .\scripts\audit-es-migration.ps1 -MysqlViaDocker:$false -MysqlHost localhost -MysqlPort 3307

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ElasticsearchUri = $(if ($env:ELASTICSEARCH_URI) { $env:ELASTICSEARCH_URI } else { "http://localhost:9200" }),
    [string]$EsIndexName = "raglaw_chunks",
    [string]$MysqlHost = $(if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "localhost" }),
    [int]$MysqlPort = $(if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3307 }),
    [string]$MysqlDatabase = $(if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "raglaw" }),
    [string]$MysqlUser = $(if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "raglaw" }),
    [string]$MysqlPassword = $(if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "raglaw" }),
    [string]$MysqlDockerContainer = "raglaw-mysql",
    [switch]$MysqlViaDocker = $true,
    [string]$OutputJson = "",
    [switch]$IncludeChunkIds = $false,
    [int]$MaxIssueSamples = 20,
    [string]$AdminPassword = $(if ($env:RAGLAW_ADMIN_PASSWORD) { $env:RAGLAW_ADMIN_PASSWORD } elseif ($env:RAGLAW_SEED_ADMIN_PASSWORD) { $env:RAGLAW_SEED_ADMIN_PASSWORD } else { "admin12345" }),
    [string]$AdminEmail = "admin@raglaw.local"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Invoke-AdminApi {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$Body = $null
    )
    $headers = @{ Authorization = "Bearer $Token" }
    $uri = "$BaseUrl$Path"
    if ($Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 5)
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
}

function Get-AdminToken {
    if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
        return $null
    }
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" `
        -Body (@{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json)
    return $login.data.accessToken
}

function Get-EmbeddableChunkCountFromApi {
    param([string]$DocumentId, [string]$Token)
    try {
        $resp = Invoke-AdminApi -Method Get -Path "/api/v1/admin/documents/$DocumentId/embeddable-chunk-count" -Token $Token
        return [int]$resp.data
    } catch {
        return $null
    }
}

function Write-Section($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

function Invoke-MySqlQuery {
    param([string]$Sql)
    $oneLine = ($Sql -replace "`r?`n", " " -replace "\s+", " ").Trim()
    if ($MysqlViaDocker) {
        $containerExists = docker ps --format "{{.Names}}" 2>$null | Select-String -SimpleMatch $MysqlDockerContainer
        if (-not $containerExists) {
            throw "Docker container '$MysqlDockerContainer' not running. Start docker compose or pass -MysqlViaDocker:`$false."
        }
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $raw = docker exec -e "MYSQL_PWD=$MysqlPassword" $MysqlDockerContainer mysql `
                --default-character-set=utf8mb4 `
                -N -B "-u$MysqlUser" $MysqlDatabase `
                -e $oneLine 2>&1
        } finally {
            $ErrorActionPreference = $prevEap
        }
        $lines = @($raw | Where-Object { $_ -is [string] })
        if ($lines.Count -eq 0 -and $LASTEXITCODE -ne 0) {
            $errText = ($raw | Out-String).Trim()
            throw "MySQL query failed: $errText"
        }
        return (($lines -join "`n").Trim())
    }

    $mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysqlCmd) {
        throw "mysql CLI not found. Install MySQL client or use -MysqlViaDocker."
    }
    $env:MYSQL_PWD = $MysqlPassword
    $raw = & mysql -N -B -h $MysqlHost -P $MysqlPort -u$MysqlUser $MysqlDatabase -e $oneLine 2>&1
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed: $raw"
    }
    return ($raw | Out-String).Trim()
}

function Convert-TsvRows {
    param([string]$Tsv)
    if ([string]::IsNullOrWhiteSpace($Tsv)) {
        return @()
    }
    $result = New-Object System.Collections.Generic.List[object]
    foreach ($line in ($Tsv -split "`r?`n")) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $result.Add([PSCustomObject]@{
            Columns = [object[]]($line -split "`t", -1)
        })
    }
    return $result.ToArray()
}

function Get-Cjk {
    param([int[]]$CodePoints)
    return -join ($CodePoints | ForEach-Object { [char]$_ })
}

# CJK literals via code points (avoids UTF-8 BOM issues in PowerShell)
$script:CjkDi = Get-Cjk 0x7B2C      # 第
$script:CjkTiao = Get-Cjk 0x6761    # 条
$script:CjkZhang = Get-Cjk 0x7AE0   # 章
$script:CjkMuLu = Get-Cjk 0x76EE, 0x5F55  # 目录
$script:CjkJuHao = Get-Cjk 0x3002   # 。
$script:CjkFenHao = Get-Cjk 0xFF1B  # ；
$script:CjkMaoHao = Get-Cjk 0xFF1A  # ：

function Test-LooksLikeShortHeading {
    param([string]$Text)
    $trimmed = if ($Text) { $Text.Trim() } else { "" }
    if ($trimmed.Length -eq 0) { return $false }
    if ($trimmed.Length -gt 30) { return $false }
    if ($trimmed.Contains($script:CjkJuHao) -or $trimmed.Contains($script:CjkFenHao) -or $trimmed.Contains($script:CjkMaoHao)) { return $false }
    if ($trimmed.StartsWith($script:CjkDi) -and $trimmed.Contains($script:CjkZhang)) { return $true }
    if ($trimmed.StartsWith($script:CjkDi) -and $trimmed.Contains($script:CjkTiao)) {
        $articleEnd = $trimmed.IndexOf($script:CjkTiao)
        if ($articleEnd -ge 0 -and ($articleEnd + 1) -lt $trimmed.Length) {
            $afterArticle = $trimmed.Substring($articleEnd + 1).Trim()
            if ($afterArticle.Length -gt 4) { return $false }
        }
        return $true
    }
    return $false
}

function Test-LooksLikeChapterHeading {
    param([string]$Text)
    $trimmed = if ($Text) { $Text.Trim() } else { "" }
    return ($trimmed.Length -gt 0 -and $trimmed.Length -le 30 -and $trimmed.StartsWith($script:CjkDi) -and $trimmed.Contains($script:CjkZhang))
}

function Test-ContainsSubstantiveArticleBody {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $false }
    $trimmed = $Text.Trim()
    if (-not $trimmed.Contains($script:CjkTiao)) { return $false }
    return -not (Test-LooksLikeShortHeading $trimmed)
}

function Test-LooksLikeTableOfContents {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $false }
    if (Test-ContainsSubstantiveArticleBody $Text) { return $false }
    $trimmed = $Text.Trim()
    $tocHeading = "## " + $script:CjkMuLu
    if ($trimmed.StartsWith($script:CjkMuLu) -or $trimmed.StartsWith($tocHeading)) { return $true }
    $chapterSectionLines = 0
    foreach ($line in ($trimmed -split "`r?`n")) {
        $normalized = $line.Trim()
        if ($normalized.Length -eq 0) { continue }
        $looksChapterLine = $false
        if ($normalized.StartsWith($script:CjkDi) -and $normalized.Length -le 40) {
            if ($normalized.Contains($script:CjkZhang) -or $normalized.Contains([char]0x8282) -or $normalized.Contains([char]0x7F16)) {
                $looksChapterLine = $true
            }
        }
        if ($looksChapterLine -or (Test-LooksLikeChapterHeading $normalized)) {
            $chapterSectionLines++
        }
    }
    return ($chapterSectionLines -ge 2)
}

function Get-EmbeddableChunkIds {
    param([array]$ChunksForDocument)
    $hasMicro = @($ChunksForDocument | Where-Object { $_.ChunkLevel -eq "MICRO" }).Count -gt 0
    $hasChildren = @($ChunksForDocument | Where-Object {
        $_.ChunkLevel -eq "CHILD" -or ($null -eq $_.ChunkLevel -and $null -ne $_.ParentId)
    }).Count -gt 0

    $selected = New-Object System.Collections.Generic.List[string]
    foreach ($chunk in $ChunksForDocument) {
        if ($chunk.ChunkLevel -eq "PARENT" -or ($null -eq $chunk.ChunkLevel -and $null -eq $chunk.ParentId -and $hasChildren)) {
            continue
        }
        if ($hasMicro -and $chunk.ChunkLevel -ne "MICRO") {
            continue
        }
        if ($chunk.ChunkLevel -eq "MICRO" -and (Test-LooksLikeShortHeading $chunk.Content)) {
            continue
        }
        if (Test-LooksLikeTableOfContents $chunk.Content) {
            continue
        }
        $selected.Add($chunk.ChunkId)
    }
    return $selected
}

function Invoke-Elasticsearch {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [object]$Body = $null
    )
    $uri = "$ElasticsearchUri$Path"
    if ($Body) {
        $json = $Body | ConvertTo-Json -Depth 20 -Compress
        return Invoke-RestMethod -Method $Method -Uri $uri -ContentType "application/json" -Body $json
    }
    return Invoke-RestMethod -Method $Method -Uri $uri
}

function Get-AllEsChunks {
    param([string]$IndexName)
    $chunks = New-Object System.Collections.Generic.List[object]
    $first = Invoke-Elasticsearch -Method POST -Path "/$IndexName/_search?scroll=2m" -Body @{
        size = 1000
        _source = @("chunk_id", "document_id", "index_version")
        query = @{ match_all = @{} }
    }
    $scrollId = $first._scroll_id
    foreach ($hit in $first.hits.hits) {
        $src = $hit._source
        $chunks.Add([PSCustomObject]@{
            ChunkId = [string]$src.chunk_id
            DocumentId = [string]$src.document_id
            IndexVersion = if ($null -ne $src.index_version) { [long]$src.index_version } else { 0 }
        })
    }
    while ($first.hits.hits.Count -gt 0) {
        $first = Invoke-Elasticsearch -Method POST -Path "/_search/scroll" -Body @{
            scroll = "2m"
            scroll_id = $scrollId
        }
        $scrollId = $first._scroll_id
        if ($first.hits.hits.Count -eq 0) { break }
        foreach ($hit in $first.hits.hits) {
            $src = $hit._source
            $chunks.Add([PSCustomObject]@{
                ChunkId = [string]$src.chunk_id
                DocumentId = [string]$src.document_id
                IndexVersion = if ($null -ne $src.index_version) { [long]$src.index_version } else { 0 }
            })
        }
    }
    if ($scrollId) {
        try {
            Invoke-Elasticsearch -Method DELETE -Path "/_search/scroll" -Body @{ scroll_id = $scrollId } | Out-Null
        } catch {
            # ignore scroll cleanup errors
        }
    }
    return $chunks
}

function Get-EsDocumentStatsMap {
    param($EsAggResponse)
    $map = @{}
    if (-not $EsAggResponse -or -not $EsAggResponse.aggregations.by_document.buckets) {
        return $map
    }
    foreach ($bucket in $EsAggResponse.aggregations.by_document.buckets) {
        $map[$bucket.key] = [PSCustomObject]@{
            ChunkCount = [int]$bucket.doc_count
            IndexVersion = [long]$bucket.max_index_version.value
            WithEmbedding = [int]$bucket.with_embedding.doc_count
        }
    }
    return $map
}

function Get-EsEmbeddingStats {
    param([string]$IndexName)
    try {
        $resp = Invoke-Elasticsearch -Method POST -Path "/$IndexName/_search" -Body @{
            size = 0
            aggs = @{
                total = @{ value_count = @{ field = "chunk_id" } }
                with_embedding = @{
                    filter = @{ exists = @{ field = "embedding" } }
                    aggs = @{
                        count = @{ value_count = @{ field = "chunk_id" } }
                    }
                }
                by_document = @{
                    terms = @{ field = "document_id"; size = 10000 }
                    aggs = @{
                        max_index_version = @{ max = @{ field = "index_version" } }
                        with_embedding = @{
                            filter = @{ exists = @{ field = "embedding" } }
                        }
                    }
                }
            }
        }
        return $resp
    } catch {
        return $null
    }
}

Write-Section "RagLaw ES migration audit"
Write-Host "Repo: $RepoRoot"
Write-Host "MySQL: $MysqlHost`:$MysqlPort/$MysqlDatabase (viaDocker=$MysqlViaDocker)"
Write-Host "Elasticsearch: $ElasticsearchUri/$EsIndexName"

$report = [ordered]@{
    auditedAt = (Get-Date).ToString("o")
    config = [ordered]@{
        baseUrl = $BaseUrl
        elasticsearchUri = $ElasticsearchUri
        esIndexName = $EsIndexName
        mysql = "$MysqlHost`:$MysqlPort/$MysqlDatabase"
    }
    health = $null
    mysql = [ordered]@{}
    elasticsearch = [ordered]@{}
    outbox = [ordered]@{}
    documents = @()
    summary = [ordered]@{}
    issues = @()
}

Write-Section "Backend health"
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health"
    $report.health = $health.data
    $rag = $health.data.rag
    Write-Host "status=$($health.data.status) elasticsearchEnabled=$($rag.elasticsearchEnabled) embeddingReady=$($rag.embeddingReady) hybridRetrievalReady=$($rag.hybridRetrievalReady)"
} catch {
    $report.health = @{ error = $_.Exception.Message }
    Write-Warning "Health check failed: $($_.Exception.Message)"
}

Write-Section "MySQL document / chunk totals"
$docStatusRows = Convert-TsvRows (Invoke-MySqlQuery @"
SELECT status, COUNT(*) FROM raglaw_document GROUP BY status ORDER BY status
"@)
$report.mysql.documentStatus = @($docStatusRows | ForEach-Object {
    [ordered]@{ status = $_.Columns[0]; count = [int]$_.Columns[1] }
})
foreach ($row in $docStatusRows) {
    $c = $row.Columns
    Write-Host ("  {0,-20} {1}" -f $c[0], $c[1])
}

$chunkLevelRows = Convert-TsvRows (Invoke-MySqlQuery @"
SELECT IFNULL(c.chunk_level, 'NULL') AS lvl, COUNT(*)
FROM raglaw_document_chunk c
INNER JOIN raglaw_document d ON d.id = c.document_id
WHERE d.status = 'INDEXED'
GROUP BY IFNULL(c.chunk_level, 'NULL')
ORDER BY lvl
"@)
$report.mysql.chunkLevelsIndexed = @($chunkLevelRows | ForEach-Object {
    [ordered]@{ level = $_.Columns[0]; count = [int]$_.Columns[1] }
})

Write-Section "MySQL index outbox"
$outboxStatusRows = Convert-TsvRows (Invoke-MySqlQuery @"
SELECT status, COUNT(*) FROM raglaw_index_outbox GROUP BY status ORDER BY status
"@)
$report.outbox.statusCounts = @($outboxStatusRows | ForEach-Object {
    [ordered]@{ status = $_.Columns[0]; count = [int]$_.Columns[1] }
})
foreach ($row in $outboxStatusRows) {
    $c = $row.Columns
    Write-Host ("  {0,-12} {1}" -f $c[0], $c[1])
}

$outboxProblemRows = Convert-TsvRows (Invoke-MySqlQuery @"
SELECT document_id, status, attempt_count,
       REPLACE(REPLACE(LEFT(IFNULL(error_message,''), 200), CHAR(10), ' '), CHAR(9), ' '),
       index_version, created_at
FROM raglaw_index_outbox
WHERE status IN ('PENDING', 'FAILED')
ORDER BY created_at DESC
LIMIT 200
"@)
$report.outbox.pendingOrFailed = @($outboxProblemRows | ForEach-Object {
    $c = $_.Columns
    [ordered]@{
        documentId = $c[0]
        status = $c[1]
        attemptCount = [int]$c[2]
        errorMessage = $c[3]
        indexVersion = [long]$c[4]
        createdAt = $c[5]
    }
})
if ($outboxProblemRows.Count -gt 0) {
    Write-Host "Pending/Failed outbox entries: $($outboxProblemRows.Count)" -ForegroundColor Yellow
}

Write-Section "Load MySQL indexed documents + chunks"
$docRows = Convert-TsvRows (Invoke-MySqlQuery @"
SELECT d.id, d.title, d.doc_type, d.status, d.ingest_stage, d.index_version,
       (SELECT COUNT(*) FROM raglaw_document_chunk c WHERE c.document_id = d.id) AS total_chunks
FROM raglaw_document d
WHERE d.status = 'INDEXED'
ORDER BY d.title
"@)

$chunksByDocument = @{}
$mysqlEmbeddableTotal = 0
$mysqlDocs = @()
$adminToken = Get-AdminToken
if ($adminToken) {
    Write-Host "Using backend embeddable-chunk-count API for MySQL counts"
}
foreach ($row in $docRows) {
    $c = $row.Columns
    $docId = $c[0]
    $docChunkRows = Convert-TsvRows (Invoke-MySqlQuery @"
SELECT c.id, c.document_id, IFNULL(c.chunk_level, 'NULL'), IFNULL(c.parent_id, ''),
       CHAR_LENGTH(c.content),
       REPLACE(REPLACE(REPLACE(LEFT(c.content, 800), CHAR(13), ''), CHAR(10), '\\n'), CHAR(9), ' ')
FROM raglaw_document_chunk c
WHERE c.document_id = '$docId'
"@)
    $docChunks = New-Object System.Collections.Generic.List[object]
    foreach ($chunkRow in $docChunkRows) {
        $cc = $chunkRow.Columns
        $docChunks.Add([PSCustomObject]@{
            ChunkId = $cc[0]
            DocumentId = $docId
            ChunkLevel = if ($cc[2] -eq "NULL") { $null } else { $cc[2] }
            ParentId = if ([string]::IsNullOrEmpty($cc[3])) { $null } else { $cc[3] }
            ContentLength = [int]$cc[4]
            Content = ($cc[5] -replace '\\n', "`n")
        })
    }
    $chunksByDocument[$docId] = $docChunks
    $apiEmbeddableCount = if ($adminToken) { Get-EmbeddableChunkCountFromApi -DocumentId $docId -Token $adminToken } else { $null }
    $embeddableIds = if ($null -ne $apiEmbeddableCount) {
        @()  # IDs not needed when API provides authoritative count
    } else {
        Get-EmbeddableChunkIds -ChunksForDocument ([object[]]$docChunks.ToArray())
    }
    $embeddableCount = if ($null -ne $apiEmbeddableCount) { $apiEmbeddableCount } else { $embeddableIds.Count }
    $mysqlEmbeddableTotal += $embeddableCount
    $mysqlDocs += [PSCustomObject]@{
        DocumentId = $docId
        Title = $c[1]
        DocType = $c[2]
        Status = $c[3]
        IngestStage = $c[4]
        IndexVersion = [long]$c[5]
        TotalChunks = [int]$c[6]
        EmbeddableChunks = $embeddableCount
        EmbeddableChunkIds = $embeddableIds
    }
}
Write-Host "Indexed documents: $($mysqlDocs.Count)"
$chunkRowCount = ($chunksByDocument.Values | ForEach-Object { $_.Count } | Measure-Object -Sum).Sum
Write-Host "MySQL total chunks (indexed docs): $chunkRowCount"
Write-Host "MySQL embeddable chunks (IngestPipeline rules): $mysqlEmbeddableTotal"

Write-Section "Elasticsearch index"
$esAvailable = $false
$esChunks = @()
$esAgg = $null
try {
    $indexInfo = Invoke-Elasticsearch -Path "/$EsIndexName"
    $esAvailable = $true
    $report.elasticsearch.indexExists = $true
    $report.elasticsearch.indexHealth = $indexInfo
} catch {
    $report.elasticsearch.indexExists = $false
    $report.elasticsearch.error = $_.Exception.Message
    Write-Warning "Elasticsearch index '$EsIndexName' not reachable: $($_.Exception.Message)"
}

$esDocStats = @{}
if ($esAvailable) {
    $esAgg = Get-EsEmbeddingStats -IndexName $EsIndexName
    if ($esAgg) {
        $report.elasticsearch.totalChunks = [int]$esAgg.aggregations.total.value
        $report.elasticsearch.chunksWithEmbedding = [int]$esAgg.aggregations.with_embedding.count.value
        $esDocStats = Get-EsDocumentStatsMap -EsAggResponse $esAgg
    }
    $esChunks = Get-AllEsChunks -IndexName $EsIndexName
    Write-Host "ES chunks (scroll): $($esChunks.Count)"
    if ($esAgg) {
        Write-Host "ES chunks (agg): $($report.elasticsearch.totalChunks), with embedding: $($report.elasticsearch.chunksWithEmbedding)"
    }
}

$esByDocument = @{}
foreach ($chunk in $esChunks) {
    if (-not $esByDocument.ContainsKey($chunk.DocumentId)) {
        $esByDocument[$chunk.DocumentId] = New-Object System.Collections.Generic.List[object]
    }
    $esByDocument[$chunk.DocumentId].Add($chunk)
}

Write-Section "Per-document diff"
$issueCount = 0
$docsMissingEs = 0
$docsCountMismatch = 0
$docsVersionMismatch = 0
$docsNoEmbedding = 0

foreach ($doc in $mysqlDocs) {
    $docId = $doc.DocumentId
    if ($doc.DocType -eq "CONTRACT") {
        continue
    }
    $expectedIds = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($id in $doc.EmbeddableChunkIds) { [void]$expectedIds.Add($id) }
    $esDocChunks = if ($esByDocument.ContainsKey($docId)) {
        [System.Collections.Generic.List[object]]$esByDocument[$docId]
    } else {
        [System.Collections.Generic.List[object]]::new()
    }
    $esIds = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($c in $esDocChunks) { [void]$esIds.Add($c.ChunkId) }
    $esStats = if ($esDocStats.ContainsKey($docId)) { $esDocStats[$docId] } else { $null }

    $missingInEs = @($expectedIds | Where-Object { -not $esIds.Contains($_) })
    $extraInEs = @($esIds | Where-Object { -not $expectedIds.Contains($_) })
    $esChunkCount = if ($esStats) { $esStats.ChunkCount } else { $esDocChunks.Count }
    $esIndexVersion = if ($esStats) { $esStats.IndexVersion } elseif ($esDocChunks.Count -gt 0) {
        ($esDocChunks | Measure-Object -Property IndexVersion -Maximum).Maximum
    } else { 0 }
    $esWithoutEmbedding = if ($esStats) { $esStats.ChunkCount - $esStats.WithEmbedding } else { 0 }

    $issues = @()
    if ($esChunkCount -eq 0 -and $doc.EmbeddableChunks -gt 0) {
        $issues += "NO_ES_CHUNKS"
        $docsMissingEs++
    }
    if ($missingInEs.Count -gt 0) {
        $issues += "MISSING_IN_ES"
    }
    if ($extraInEs.Count -gt 0) {
        $issues += "EXTRA_IN_ES"
    }
    if ($doc.EmbeddableChunks -ne $esChunkCount) {
        $issues += "COUNT_MISMATCH"
        $docsCountMismatch++
    }
    if ($esChunkCount -gt 0 -and $esIndexVersion -ne $doc.IndexVersion) {
        $issues += "INDEX_VERSION_MISMATCH"
        $docsVersionMismatch++
    }
    if ($esWithoutEmbedding -gt 0) {
        $issues += "MISSING_EMBEDDING"
        $docsNoEmbedding++
    }

    $docReport = [ordered]@{
        documentId = $docId
        title = $doc.Title
        docType = $doc.DocType
        ingestStage = $doc.IngestStage
        mysqlIndexVersion = $doc.IndexVersion
        mysqlTotalChunks = $doc.TotalChunks
        mysqlEmbeddableChunks = $doc.EmbeddableChunks
        esChunkCount = $esChunkCount
        esChunksWithEmbedding = if ($esStats) { $esStats.WithEmbedding } else { 0 }
        esIndexVersion = $esIndexVersion
        esMissingEmbeddingCount = $esWithoutEmbedding
        missingInEsCount = $missingInEs.Count
        extraInEsCount = $extraInEs.Count
        issues = $issues
    }
    if ($IncludeChunkIds) {
        $docReport.missingInEsChunkIds = $missingInEs
        $docReport.extraInEsChunkIds = $extraInEs
    } elseif ($missingInEs.Count -gt 0 -or $extraInEs.Count -gt 0) {
        $docReport.missingInEsSample = @($missingInEs | Select-Object -First 5)
        $docReport.extraInEsSample = @($extraInEs | Select-Object -First 5)
    }

    if ($issues.Count -gt 0) {
        $issueCount++
        if ($report.issues.Count -lt $MaxIssueSamples) {
            $report.issues += $docReport
        }
    }
    $report.documents += $docReport
}

$esOrphanDocIds = @($esByDocument.Keys | Where-Object { -not ($mysqlDocs.DocumentId -contains $_) })
$esOrphanChunkCount = ($esOrphanDocIds | ForEach-Object { $esByDocument[$_].Count } | Measure-Object -Sum).Sum
if ($null -eq $esOrphanChunkCount) { $esOrphanChunkCount = 0 }

$report.summary = [ordered]@{
    indexedDocuments = $mysqlDocs.Count
    mysqlTotalChunks = $chunkRowCount
    mysqlEmbeddableChunks = $mysqlEmbeddableTotal
    esTotalChunks = $esChunks.Count
    esChunksWithEmbedding = if ($report.elasticsearch.chunksWithEmbedding) { $report.elasticsearch.chunksWithEmbedding } else { 0 }
    documentsWithIssues = $issueCount
    documentsMissingEsEntirely = $docsMissingEs
    documentsCountMismatch = $docsCountMismatch
    documentsIndexVersionMismatch = $docsVersionMismatch
    documentsMissingEmbedding = $docsNoEmbedding
    esOrphanDocuments = $esOrphanDocIds.Count
    esOrphanChunks = $esOrphanChunkCount
    outboxPendingOrFailed = $report.outbox.pendingOrFailed.Count
}
$report.elasticsearch.orphanDocumentIds = @($esOrphanDocIds | Select-Object -First $MaxIssueSamples)

Write-Host ""
Write-Host "Summary" -ForegroundColor Green
Write-Host ("  Indexed documents:              {0}" -f $report.summary.indexedDocuments)
Write-Host ("  MySQL embeddable chunks:        {0}" -f $report.summary.mysqlEmbeddableChunks)
Write-Host ("  ES chunks:                      {0}" -f $report.summary.esTotalChunks)
Write-Host ("  ES chunks w/ embedding:         {0}" -f $report.summary.esChunksWithEmbedding)
Write-Host ("  Documents with issues:          {0}" -f $report.summary.documentsWithIssues) -ForegroundColor $(if ($issueCount -gt 0) { "Yellow" } else { "Green" })
Write-Host ("  Documents missing ES entirely:  {0}" -f $report.summary.documentsMissingEsEntirely) -ForegroundColor $(if ($docsMissingEs -gt 0) { "Red" } else { "Green" })
Write-Host ("  Outbox pending/failed:          {0}" -f $report.summary.outboxPendingOrFailed) -ForegroundColor $(if ($report.summary.outboxPendingOrFailed -gt 0) { "Yellow" } else { "Green" })
Write-Host ("  ES orphan chunks (stale docs):  {0}" -f $report.summary.esOrphanChunks) -ForegroundColor $(if ($esOrphanChunkCount -gt 0) { "Yellow" } else { "Green" })

if ($report.issues.Count -gt 0) {
    Write-Host ""
    Write-Host "Sample problematic documents (max $MaxIssueSamples):" -ForegroundColor Yellow
    foreach ($issue in $report.issues) {
        Write-Host ("  - {0} [{1}] issues={2} mysqlEmb={3} es={4} missing={5} extra={6}" -f `
            $issue.title, $issue.documentId.Substring(0, 8), ($issue.issues -join ","), `
            $issue.mysqlEmbeddableChunks, $issue.esChunkCount, $issue.missingInEsCount, $issue.extraInEsCount)
    }
}

if ($OutputJson) {
    $outDir = Split-Path -Parent $OutputJson
    if ($outDir -and -not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    }
    if (-not [System.IO.Path]::IsPathRooted($OutputJson)) {
        $OutputJson = Join-Path $RepoRoot $OutputJson
    }
    $report | ConvertTo-Json -Depth 12 | Set-Content -Path $OutputJson -Encoding UTF8
    Write-Host ""
    Write-Host "Full report written to: $OutputJson" -ForegroundColor Cyan
} else {
    $defaultOut = Join-Path $RepoRoot ("docs/evaluation/es-migration-audit-{0}.json" -f (Get-Date -Format "yyyy-MM-dd-HHmmss"))
    $outDir = Split-Path -Parent $defaultOut
    if (-not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 12 | Set-Content -Path $defaultOut -Encoding UTF8
    Write-Host ""
    Write-Host "Full report written to: $defaultOut" -ForegroundColor Cyan
}

if ($issueCount -gt 0 -or $report.summary.outboxPendingOrFailed -gt 0) {
    Write-Host ""
    Write-Host "Suggested fixes:" -ForegroundColor Cyan
    Write-Host "  1. Ensure ELASTICSEARCH_ENABLED=true and backend is running with ES reachable"
    Write-Host "  2. Reindex affected docs: POST /api/v1/admin/documents/{id}/reindex"
    Write-Host "  3. Or batch: POST /api/v1/admin/documents/reindex-batch?docType=STATUTE"
    Write-Host "  4. Check outbox errors above; failed entries block eventual consistency"
    exit 1
}

exit 0
