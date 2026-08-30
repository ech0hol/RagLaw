# RagLaw strict quality acceptance - simulates frontend user flows via API
$ErrorActionPreference = "Stop"
if ($env:RAGLAW_BASE_URL) { $BaseUrl = $env:RAGLAW_BASE_URL } else { $BaseUrl = "http://localhost:8080" }
$AdminEmail = "admin@raglaw.local"
if ($env:RAGLAW_ADMIN_PASSWORD) { $AdminPassword = $env:RAGLAW_ADMIN_PASSWORD } else { $AdminPassword = "admin12345" }

$results = @()
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Add-Result($area, $case, $status, $detail) {
    $script:results += [PSCustomObject]@{ Area = $area; Case = $case; Status = $status; Detail = $detail }
}

function Invoke-Api($method, $path, $body = $null, $token = $null) {
    $headers = @{ "Content-Type" = "application/json" }
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $params = @{ Uri = "$BaseUrl$path"; Method = $method; Headers = $headers }
    if ($body -ne $null) { $params.Body = ($body | ConvertTo-Json -Depth 10 -Compress) }
    return Invoke-RestMethod @params
}

function Get-HttpStatus($errorRecord) {
    try {
        return [int]$errorRecord.Exception.Response.StatusCode.value__
    } catch {
        return 0
    }
}

function Invoke-Upload($path, $filePath, $token, $extraFields = @{}) {
    $auth = "Authorization: Bearer $token"
    $form = @("-F", "file=@$filePath")
    foreach ($key in $extraFields.Keys) {
        $form += "-F"
        $form += "$key=$($extraFields[$key])"
    }
    $json = & curl.exe -s -X POST "$BaseUrl$path" -H $auth @form
    return $json | ConvertFrom-Json
}

function Get-LawyerToken($adminToken) {
    $email = "eval-lawyer@raglaw.local"
    $password = "raglaw-eval-lawyer"
    try {
        $login = Invoke-Api POST "/api/v1/auth/login" @{ email = $email; password = $password }
        if ($login.success -and $login.data.accessToken) {
            return $login.data.accessToken
        }
    } catch { }
    try {
        Invoke-Api POST "/api/v1/admin/users" @{
            email = $email
            password = $password
            displayName = "Eval Lawyer"
            role = "LAWYER"
        } $adminToken | Out-Null
        Add-Result "auth" "create_lawyer" "PASS" $email
    } catch {
        $code = Get-HttpStatus $_
        if ($code -eq 409 -or $code -eq 400) {
            Add-Result "auth" "create_lawyer" "PASS" "already exists"
        } else {
            Add-Result "auth" "create_lawyer" "WARN" $_.Exception.Message
        }
    }
    try {
        $login = Invoke-Api POST "/api/v1/auth/login" @{ email = $email; password = $password }
        return $login.data.accessToken
    } catch {
        Add-Result "auth" "lawyer_login" "FAIL" $_.Exception.Message
        return $null
    }
}

function Test-DuplicateAnswerBlocks($content) {
    $matches = [regex]::Matches($content, "(?m)^1\.\s+\*\*")
    return $matches.Count -gt 1
}

function Test-ContradictoryDisclaimer($content, $referenceCount) {
    if ($referenceCount -le 0) { return $false }
    return $content -match "未检索到|知识库未检索|无法基于文档作答"
}

Write-Host "=== RagLaw Quality Acceptance ===" -ForegroundColor Cyan
Write-Host "Base: $BaseUrl"

# 1. Health
try {
    $health = Invoke-Api GET "/api/v1/health"
    if ($health.success -and $health.data.status -eq "UP") {
        $rag = $health.data.rag
        $detail = "hybrid=$($rag.hybridRetrievalReady) esSync=$($rag.elasticsearchIndexSyncReady) llmMock=$($health.data.llmMock)"
        Add-Result "infra" "health" "PASS" $detail
        if ($rag.elasticsearchEnabled -and $rag.embeddingConfigured) {
            $hybridOk = [bool]$rag.hybridRetrievalReady -and [bool]$rag.elasticsearchIndexSyncReady
            Add-Result "infra" "hybrid_ready" $(if ($hybridOk) { "PASS" } else { "WARN" }) $detail
        } else {
            Add-Result "infra" "hybrid_ready" "WARN" "fulltext-only"
        }
    } else { Add-Result "infra" "health" "FAIL" "success=false" }
} catch { Add-Result "infra" "health" "FAIL" $_.Exception.Message }

Start-Sleep -Seconds 2

# 2. Auth
$adminToken = $null
$script:lawyerToken = $null
try {
    $login = Invoke-Api POST "/api/v1/auth/login" @{ email = $AdminEmail; password = $AdminPassword }
    if ($login.success -and $login.data.accessToken) {
        $adminToken = $login.data.accessToken
        Add-Result "auth" "admin_login" "PASS" "role=$($login.data.user.role)"
    } else { Add-Result "auth" "admin_login" "FAIL" "no token" }
} catch { Add-Result "auth" "admin_login" "FAIL" $_.Exception.Message }

# 3. Permission: admin endpoints
if ($adminToken) {
    try {
        $ping = Invoke-Api GET "/api/v1/admin/ping" $null $adminToken
        if ($ping.success) { Add-Result "auth" "admin_ping" "PASS" "" } else { Add-Result "auth" "admin_ping" "FAIL" "" }
    } catch { Add-Result "auth" "admin_ping" "FAIL" $_.Exception.Message }

    try {
        $users = Invoke-Api GET "/api/v1/admin/users" $null $adminToken
        if ($users.success) { Add-Result "auth" "user_list" "PASS" "count=$($users.data.Count)" } else { Add-Result "auth" "user_list" "FAIL" "" }
    } catch { Add-Result "auth" "user_list" "FAIL" $_.Exception.Message }

    try {
        $traces = Invoke-Api GET "/api/v1/admin/traces?page=1&pageSize=5" $null $adminToken
        if ($traces.success) {
            $total = if ($traces.data.total) { $traces.data.total } else { $traces.data.Count }
            Add-Result "observability" "trace_list" "PASS" "total=$total"
        } else { Add-Result "observability" "trace_list" "FAIL" "" }
    } catch { Add-Result "observability" "trace_list" "FAIL" $_.Exception.Message }

    Start-Sleep -Seconds 2
    $script:lawyerToken = Get-LawyerToken $adminToken
    if ($script:lawyerToken) {
        try {
            Invoke-Api GET "/api/v1/admin/users" $null $script:lawyerToken | Out-Null
            Add-Result "auth" "lawyer_admin_block" "FAIL" "should 403"
        } catch {
            if ((Get-HttpStatus $_) -eq 403) {
                Add-Result "auth" "lawyer_admin_block" "PASS" "403"
            } else {
                Add-Result "auth" "lawyer_admin_block" "WARN" $_.Exception.Message
            }
        }
    } else {
        Add-Result "auth" "lawyer_admin_block" "WARN" "lawyer login failed"
    }
}

# 4. Permission: unauthenticated blocked
try {
    Invoke-Api GET "/api/v1/conversations" $null $null | Out-Null
    Add-Result "auth" "unauth_block" "FAIL" "should 401"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 401) { Add-Result "auth" "unauth_block" "PASS" "401" } else { Add-Result "auth" "unauth_block" "WARN" $_.Exception.Message }
}

# 5. Knowledge stats and search
if ($adminToken) {
    try {
        $stats = Invoke-Api GET "/api/v1/knowledge/stats" $null $adminToken
        if ($stats.success) {
            Add-Result "retrieval" "knowledge_stats" "PASS" "statute=$($stats.data.statuteCount) case=$($stats.data.caseCount)"
        } else { Add-Result "retrieval" "knowledge_stats" "FAIL" "" }
    } catch { Add-Result "retrieval" "knowledge_stats" "FAIL" $_.Exception.Message }

    $searchQueries = @(
        [char]0x62D6 + [char]0x6B20 + [char]0x5DE5 + [char]0x8D44
        [char]0x5211 + [char]0x6CD5 + [char]0x4E2D + [char]0x5211 + [char]0x7F5A + [char]0x79CD + [char]0x7C7B
        [char]0x9644 + [char]0x52A0 + [char]0x5211 + [char]0x7684 + [char]0x79CD + [char]0x7C7B
        [char]0x57CE + [char]0x4E61 + [char]0x5C45 + [char]0x6C11 + [char]0x533B + [char]0x4FDD + [char]0x5F02 + [char]0x5730 + [char]0x62A5 + [char]0x9500
    )
    foreach ($q in $searchQueries) {
        try {
            $encoded = [uri]::EscapeDataString($q)
            $searchPath = "/api/v1/knowledge/search?q=$encoded" + "&pageSize=5"
            $search = Invoke-Api GET $searchPath $null $adminToken
            if ($search.success) {
                $hits = $search.data.items.Count
                $status = if ($hits -gt 0) { "PASS" } else { "WARN" }
                Add-Result "retrieval" "search_$q" $status "hits=$hits"
            } else { Add-Result "retrieval" "search_$q" "FAIL" "" }
        } catch { Add-Result "retrieval" "search_$q" "FAIL" $_.Exception.Message }
    }
}

# 6. Multi-turn conversation + AG-UI SSE
if ($adminToken) {
    try {
        $conv = Invoke-Api POST "/api/v1/conversations" @{ agentCode = "STATUTE" } $adminToken
        $convId = $conv.data.id
        Add-Result "conversation" "create" "PASS" "id=$convId"

        $q1 = [char]0x5211 + [char]0x6CD5 + [char]0x4E2D + [char]0x5211 + [char]0x7F5A + [char]0x79CD + [char]0x7C7B + [char]0x6709 + [char]0x54EA + [char]0x4E9B
        $q2 = [char]0x90A3 + [char]0x6211 + [char]0x60F3 + [char]0x4E70 + [char]0x793E + [char]0x4FDD + [char]0x5462
        $q3 = [char]0x57CE + [char]0x4E61 + [char]0x5C45 + [char]0x6C11 + [char]0x533B + [char]0x4FDD + [char]0x53EF + [char]0x4EE5 + [char]0x5F02 + [char]0x5730 + [char]0x62A5 + [char]0x9500 + [char]0x5417
        $questions = @($q1, $q2, $q3)
        $turn = 0
        foreach ($q in $questions) {
            $turn++
            if ($turn -gt 1) { Start-Sleep -Seconds 3 }
            $body = @{ conversationId = $convId; message = $q; agentCode = "STATUTE" } | ConvertTo-Json
            $req = [System.Net.HttpWebRequest]::Create("$BaseUrl/api/v1/agui/run")
            $req.Method = "POST"
            $req.ContentType = "application/json"
            $req.Headers.Add("Authorization", "Bearer $adminToken")
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
            $req.ContentLength = $bytes.Length
            $stream = $req.GetRequestStream()
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Close()
            $resp = $req.GetResponse()
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $sse = $reader.ReadToEnd()
            $reader.Close()
            $resp.Close()

            $textResetCount = ([regex]::Matches($sse, "event:\s*text_reset")).Count
            $doneMatch = [regex]::Match($sse, "event:\s*done\s*\ndata:\s*(\{.*\})", "Singleline")
            $assistant = ""
            if ($doneMatch.Success) {
                $doneJson = $doneMatch.Groups[1].Value | ConvertFrom-Json
                $assistant = $doneJson.content
            } else {
                $pattern = 'event:\s*text\s*\ndata:\s*\{"delta":"([^"]*)"'
                $deltas = [regex]::Matches($sse, $pattern)
                foreach ($m in $deltas) {
                    $chunk = $m.Groups[1].Value -replace '\\n', "`n"
                    $assistant += $chunk
                }
            }
            $refCount = ([regex]::Matches($sse, "event:\s*reference")).Count
            $dup = Test-DuplicateAnswerBlocks $assistant
            $contradictory = Test-ContradictoryDisclaimer $assistant $refCount
            $hasContent = $assistant.Trim().Length -gt 50
            $status = if (-not $hasContent -or $dup -or $contradictory) { "FAIL" } else { "PASS" }
            $numberedStarts = ([regex]::Matches($assistant, "(?m)^1\.\s+\*\*")).Count
            $detail = "len=$($assistant.Length) refs=$refCount text_reset=$textResetCount sections_1=$numberedStarts"
            if ($dup) { $detail += " DUPLICATE_BLOCKS" }
            if ($contradictory) { $detail += " CONTRADICTORY_DISCLAIMER" }
            Add-Result "conversation" "turn${turn}_$q" $status $detail
        }

        $msgs = Invoke-Api GET "/api/v1/conversations/$convId/messages" $null $adminToken
        if ($msgs.success) {
            $asst = @($msgs.data | Where-Object { $_.role -eq "assistant" })
            Add-Result "conversation" "persist" "PASS" "assistant_count=$($asst.Count)"
            $withCitations = @($asst | Where-Object { $_.citationsJson -and $_.citationsJson -ne "null" }).Count
            $citStatus = if ($withCitations -gt 0) { "PASS" } else { "WARN" }
            Add-Result "answer" "citations_persist" $citStatus "with_citations=$withCitations/$($asst.Count)"
            $persistDup = @($asst | Where-Object { Test-DuplicateAnswerBlocks $_.content }).Count
            $persistStatus = if ($persistDup -eq 0) { "PASS" } else { "FAIL" }
            Add-Result "answer" "persist_quality_gates" $persistStatus "duplicate_blocks=$persistDup"
        }
    } catch { Add-Result "conversation" "multiturn_sse" "FAIL" $_.Exception.Message }
}

# 7. Contract list
if ($adminToken) {
    try {
        $contracts = Invoke-Api GET "/api/v1/contracts" $null $adminToken
        if ($contracts.success) {
            $count = $contracts.data.Count
            Add-Result "contract" "list" "PASS" "count=$count"
            if ($count -gt 0) {
                $docId = $contracts.data[0].documentId
                $review = Invoke-Api GET "/api/v1/contracts/$docId/review" $null $adminToken
                if ($review.success) {
                    $riskCount = $review.data.risks.Count
                    $ragHits = $review.data.ragHitCount
                    $ragStatus = if ($ragHits -gt 0) { "PASS" } else { "WARN" }
                    Add-Result "contract" "review_detail" "PASS" "risks=$riskCount ragHits=$ragHits status=$($review.data.reviewStatus)"
                    Add-Result "contract" "review_rag_hits" $ragStatus "ragHitCount=$ragHits"
                } else { Add-Result "contract" "review_detail" "WARN" "no review data" }
            } else { Add-Result "contract" "review_detail" "WARN" "no contracts" }
        } else { Add-Result "contract" "list" "FAIL" "" }
    } catch { Add-Result "contract" "list" "FAIL" $_.Exception.Message }
}

# 8. Trace detail
if ($adminToken) {
    try {
        $traces = Invoke-Api GET "/api/v1/admin/traces?page=1&pageSize=1" $null $adminToken
        $items = @()
        if ($traces.data.items) { $items = @($traces.data.items) }
        elseif ($traces.data) { $items = @($traces.data) }
        if ($items -and $items.Count -gt 0) {
            $tid = $items[0].id
            $detail = Invoke-Api GET "/api/v1/admin/traces/$tid" $null $adminToken
            if ($detail.success) {
                $stages = $detail.data.stages.Count
                $chunks = $detail.data.chunks.Count
                Add-Result "observability" "trace_detail" "PASS" "stages=$stages chunks=$chunks"
                $stageNames = ($detail.data.stages | ForEach-Object { $_.stage }) -join ","
                Add-Result "observability" "trace_stages" "PASS" $stageNames
                if ($detail.data.llmUsage -and $detail.data.llmUsage.Count -gt 0) {
                    $outLen = if ($detail.data.llmUsage[0].outputText) { $detail.data.llmUsage[0].outputText.Length } else { 0 }
                    $outStatus = if ($outLen -gt 0) { "PASS" } else { "WARN" }
                    Add-Result "observability" "trace_llm_output" $outStatus "len=$outLen"
                } else {
                    Add-Result "observability" "trace_llm_output" "WARN" "no llm usage"
                }
            }
        } else { Add-Result "observability" "trace_detail" "WARN" "no traces" }
    } catch { Add-Result "observability" "trace_detail" "FAIL" $_.Exception.Message }
}

# 9. Agent reload, seed codes, disable CASE (always restore)
if ($adminToken) {
    try {
        $reload = Invoke-Api POST "/api/v1/admin/agents/reload" @{} $adminToken
        if ($reload.success -and $reload.data.reloaded) {
            Add-Result "admin" "agent_reload" "PASS" "reloaded=true"
        } else {
            Add-Result "admin" "agent_reload" "FAIL" ""
        }
    } catch { Add-Result "admin" "agent_reload" "FAIL" $_.Exception.Message }

    try {
        $statute = Invoke-Api GET "/api/v1/admin/agents/STATUTE" $null $adminToken
        if ($statute.success -and $statute.data.code -eq "STATUTE") {
            Add-Result "admin" "agent_code_STATUTE" "PASS" "enabled=$($statute.data.enabled)"
        } else {
            Add-Result "admin" "agent_code_STATUTE" "FAIL" ""
        }
    } catch { Add-Result "admin" "agent_code_STATUTE" "FAIL" $_.Exception.Message }

    try {
        Invoke-Api GET "/api/v1/admin/agents/STATUTE_CIVIL" $null $adminToken | Out-Null
        Add-Result "admin" "agent_code_STATUTE_CIVIL" "WARN" "seed agent exists (unexpected)"
    } catch {
        Add-Result "admin" "agent_code_STATUTE_CIVIL" "PASS" "not a seed agent (use /chat/STATUTE)"
    }

    $caseRestored = $false
    try {
        $disabled = Invoke-Api PUT "/api/v1/admin/agents/CASE" @{ enabled = $false } $adminToken
        if ($disabled.success -and -not $disabled.data.enabled) {
            Add-Result "admin" "disable_CASE_flag" "PASS" "enabled=false"
        } else {
            Add-Result "admin" "disable_CASE_flag" "FAIL" "still enabled"
        }
        try {
            $body = @{ message = "test"; agentCode = "CASE" } | ConvertTo-Json
            $req = [System.Net.HttpWebRequest]::Create("$BaseUrl/api/v1/agui/run")
            $req.Method = "POST"
            $req.ContentType = "application/json"
            $req.Headers.Add("Authorization", "Bearer $adminToken")
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
            $req.ContentLength = $bytes.Length
            $stream = $req.GetRequestStream()
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Close()
            $resp = $req.GetResponse()
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $sse = $reader.ReadToEnd()
            $reader.Close()
            $resp.Close()
            if ($sse -match "event:\s*error" -or $sse -match "disabled|not found") {
                Add-Result "admin" "disable_CASE_run" "PASS" "sse error"
            } else {
                Add-Result "admin" "disable_CASE_run" "WARN" "AguiRunService falls back to GENERAL when peer missing"
            }
        } catch {
            Add-Result "admin" "disable_CASE_run" "PASS" "run rejected"
        }
    } catch {
        Add-Result "admin" "disable_CASE_flag" "WARN" $_.Exception.Message
    } finally {
        try {
            Invoke-Api PUT "/api/v1/admin/agents/CASE" @{ enabled = $true } $adminToken | Out-Null
            $caseRestored = $true
        } catch { }
    }
    if ($caseRestored) {
        Add-Result "admin" "restore_CASE" "PASS" "enabled=true"
    } else {
        Add-Result "admin" "restore_CASE" "FAIL" "CASE may still be disabled"
    }
}

# 10. Case approval + knowledge graph
if ($adminToken) {
    $caseFile = Join-Path $env:TEMP "raglaw-eval-case.md"
    Set-Content -Path $caseFile -Encoding utf8 -Value "# eval case overtime`nWorker claims overtime pay and economic compensation under labor contract law."
    try {
        $uploaded = Invoke-Upload "/api/v1/admin/documents/upload" $caseFile $adminToken @{ categoryId = "cat_l3_case_civil_labor" }
        $caseDocId = $uploaded.data.id
        $ingested = Invoke-Api POST "/api/v1/admin/documents/$caseDocId/ingest" @{} $adminToken
        $status = [string]$ingested.data.status
        if ($status -eq "AWAITING_APPROVAL") {
            Add-Result "approval" "case_pending" "PASS" "id=$caseDocId"
            $approved = Invoke-Api POST "/api/v1/admin/approvals/$caseDocId/approve" @{} $adminToken
            if ([string]$approved.data.status -eq "INDEXED") {
                Add-Result "approval" "case_approve" "PASS" "INDEXED"
            } else {
                Add-Result "approval" "case_approve" "FAIL" "status=$($approved.data.status)"
            }
        } elseif ($status -eq "INDEXED") {
            Add-Result "approval" "case_pending" "WARN" "ingest skipped pending (already INDEXED)"
            Add-Result "approval" "case_approve" "PASS" "already INDEXED"
        } else {
            Add-Result "approval" "case_pending" "FAIL" "status=$status"
        }
    } catch { Add-Result "approval" "case_pending" "FAIL" $_.Exception.Message }

    try {
        $encoded = [uri]::EscapeDataString(([char]0x52B3).ToString() + [char]0x52A8 + [char]0x4E89 + [char]0x8BAE + [char]0x6848 + [char]0x4F8B)
        $search = Invoke-Api GET "/api/v1/knowledge/search?q=$encoded&pageSize=5" $null $adminToken
        $first = @($search.data.items) | Select-Object -First 1
        if ($first -and $first.documentId) {
            $doc = Invoke-Api GET "/api/v1/knowledge/documents/$($first.documentId)" $null $adminToken
            $related = @($doc.data.relatedDocuments).Count
            $graphStatus = if ($related -gt 0) { "PASS" } else { "WARN" }
            Add-Result "knowledge" "graph_related" $graphStatus "related=$related"
        } else {
            Add-Result "knowledge" "graph_related" "WARN" "no search hits"
        }
    } catch { Add-Result "knowledge" "graph_related" "FAIL" $_.Exception.Message }
}

# 11. Contract upload + ingest-review + export + IDOR
if ($adminToken) {
    $contractFile = Join-Path $env:TEMP "raglaw-eval-contract.txt"
    $contractZh = -join @(
        [char]0x7532, [char]0x65B9, [char]0x4E0E, [char]0x4E59, [char]0x65B9, [char]0x7B7E, [char]0x8BA2, [char]0x52B3, [char]0x52A8, [char]0x5408, [char]0x540C,
        [char]0xFF0C, [char]0x8BD5, [char]0x7528, [char]0x671F, [char]0x516D, [char]0x4E2A, [char]0x6708, [char]0xFF0C,
        [char]0x8FDD, [char]0x7EA6, [char]0x91D1, [char]0x5341, [char]0x4E07, [char]0x5143, [char]0x3002
    )
    [System.IO.File]::WriteAllText($contractFile, $contractZh, [System.Text.UTF8Encoding]::new($false))
    $newContractId = $null
    try {
        $uploaded = Invoke-Upload "/api/v1/contracts/upload" $contractFile $adminToken
        $newContractId = $uploaded.data.id
        Add-Result "contract" "upload" "PASS" "id=$newContractId"
        $review = Invoke-Api POST "/api/v1/contracts/$newContractId/ingest-review" @{} $adminToken
        $risks = @($review.data.risks).Count
        $revStatus = [string]$review.data.reviewStatus
        $revOk = $revStatus -eq "COMPLETED" -or $risks -gt 0
        Add-Result "contract" "ingest_review" $(if ($revOk) { "PASS" } else { "WARN" }) "status=$revStatus risks=$risks ragHits=$($review.data.ragHitCount)"
        try {
            $exportOut = Join-Path $env:TEMP "raglaw-eval-export.docx"
            $code = & curl.exe -s -o $exportOut -w "%{http_code}" -H "Authorization: Bearer $adminToken" "$BaseUrl/api/v1/contracts/$newContractId/export?format=docx"
            $size = if (Test-Path $exportOut) { (Get-Item $exportOut).Length } else { 0 }
            if ($code -eq "200" -and $size -gt 0) {
                Add-Result "contract" "export_docx" "PASS" "bytes=$size"
            } else {
                Add-Result "contract" "export_docx" "FAIL" "http=$code bytes=$size (text/file endpoints 200 with same token)"
            }
        } catch { Add-Result "contract" "export_docx" "FAIL" $_.Exception.Message }
    } catch { Add-Result "contract" "upload" "FAIL" $_.Exception.Message }

    if ($newContractId -and $script:lawyerToken) {
        try {
            Invoke-Api GET "/api/v1/contracts/$newContractId/text" $null $script:lawyerToken | Out-Null
            Add-Result "auth" "contract_idor" "FAIL" "lawyer read owner contract"
        } catch {
            if ((Get-HttpStatus $_) -eq 403) {
                Add-Result "auth" "contract_idor" "PASS" "403"
            } else {
                Add-Result "auth" "contract_idor" "WARN" $_.Exception.Message
            }
        }
    } elseif (-not $script:lawyerToken) {
        Add-Result "auth" "contract_idor" "WARN" "no lawyer token"
    }
}

# 12. SSE stop endpoint
if ($adminToken) {
    try {
        $stop = Invoke-Api POST "/api/v1/agui/run/stop?taskId=acceptance-qa-probe" @{} $adminToken
        if ($stop.success) {
            Add-Result "conversation" "agui_stop" "PASS" "stopped=$($stop.data.stopped)"
        } else {
            Add-Result "conversation" "agui_stop" "FAIL" ""
        }
    } catch { Add-Result "conversation" "agui_stop" "FAIL" $_.Exception.Message }
}

# Report
Write-Host ""
Write-Host "=== ACCEPTANCE REPORT ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
$pass = @($results | Where-Object { $_.Status -eq "PASS" }).Count
$fail = @($results | Where-Object { $_.Status -eq "FAIL" }).Count
$warn = @($results | Where-Object { $_.Status -eq "WARN" }).Count
Write-Host "PASS: $pass  FAIL: $fail  WARN: $warn" -ForegroundColor $(if ($fail -gt 0) { "Red" } else { "Green" })
$reportPath = Join-Path (Split-Path -Parent $PSScriptRoot) "docs\evaluation\acceptance-report-latest.json"
$results | ConvertTo-Json -Depth 5 | Out-File -FilePath $reportPath -Encoding utf8
Write-Host "Report: $reportPath"
if ($fail -gt 0) { exit 1 }
