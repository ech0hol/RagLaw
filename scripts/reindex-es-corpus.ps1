# Reindex INDEXED corpus into Elasticsearch and verify with audit-es-migration.ps1
# Usage: .\scripts\reindex-es-corpus.ps1 -AdminPassword admin12345

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminPassword = $(if ($env:RAGLAW_ADMIN_PASSWORD) { $env:RAGLAW_ADMIN_PASSWORD } elseif ($env:RAGLAW_SEED_ADMIN_PASSWORD) { $env:RAGLAW_SEED_ADMIN_PASSWORD } else { "admin12345" }),
    [string]$AdminEmail = "admin@raglaw.local",
    [string]$DocType = "STATUTE",
    [int]$Limit = 50,
    [string]$AuditOutput = "docs/evaluation/es-migration-audit-after-fix.json"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$AuditScript = Join-Path $RepoRoot "scripts\audit-es-migration.ps1"

function Write-Section($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = $null,
        [object]$Body = $null
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $uri = "$BaseUrl$Path"
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

Write-Section "RagLaw ES corpus reindex"
Write-Host "BaseUrl: $BaseUrl"

Write-Section "Health check"
$health = Invoke-Api -Method GET -Path "/api/v1/health"
$rag = $health.data.rag
Write-Host "hybridRetrievalReady=$($rag.hybridRetrievalReady)"
if (-not $rag.hybridRetrievalReady) {
    throw "Backend is not hybrid-ready. Set ELASTICSEARCH_ENABLED=true and EMBEDDING_ENABLED=true, then restart."
}

$missingBefore = $null
if ($rag.PSObject.Properties.Name -contains "indexedDocumentsMissingEsSync") {
    $missingBefore = [long]$rag.indexedDocumentsMissingEsSync
    Write-Host "indexedDocumentsMissingEsSync (before)=$missingBefore"
    Write-Host "elasticsearchIndexSyncReady (before)=$($rag.elasticsearchIndexSyncReady)"
}

if ($null -ne $missingBefore -and $missingBefore -eq 0) {
    Write-Host "No documents missing ES sync; running audit only." -ForegroundColor Yellow
} else {
    Write-Section "Batch reindex"
    $token = Login-Admin
    $result = Invoke-Api -Method POST -Path "/api/v1/admin/documents/reindex-batch?docType=$DocType&limit=$Limit" -Token $token
    Write-Host "requested=$($result.data.requested) succeeded=$($result.data.succeeded) failed=$($result.data.failed)"
    if ($result.data.failed -gt 0) {
        foreach ($failure in $result.data.failures) {
            Write-Host "  FAILED $($failure.documentId): $($failure.message)" -ForegroundColor Red
        }
        throw "Reindex batch had failures"
    }

    Start-Sleep -Seconds 2
    $healthAfter = Invoke-Api -Method GET -Path "/api/v1/health"
    $ragAfter = $healthAfter.data.rag
    if ($ragAfter.PSObject.Properties.Name -contains "indexedDocumentsMissingEsSync") {
        Write-Host "indexedDocumentsMissingEsSync (after)=$($ragAfter.indexedDocumentsMissingEsSync)"
        Write-Host "elasticsearchIndexSyncReady (after)=$($ragAfter.elasticsearchIndexSyncReady)"
    }
}

Write-Section "Audit"
& $AuditScript -BaseUrl $BaseUrl -OutputJson $AuditOutput
if ($LASTEXITCODE -ne 0) {
    throw "Audit script reported issues. See $AuditOutput"
}

Write-Host ""
Write-Host "ES corpus reindex completed successfully." -ForegroundColor Green
