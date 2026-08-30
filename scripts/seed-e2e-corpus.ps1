# Seed minimal RAG corpus for evaluation and E2E (PowerShell)
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminPassword = $(if ($env:RAGLAW_ADMIN_PASSWORD) { $env:RAGLAW_ADMIN_PASSWORD } elseif ($env:RAGLAW_SEED_ADMIN_PASSWORD) { $env:RAGLAW_SEED_ADMIN_PASSWORD } else { "admin12345" }),
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Upload-And-Ingest($filePath, $categoryId, [bool]$approve) {
    $token = $script:Token
    $uploadJson = curl.exe -s -X POST "$BaseUrl/api/v1/admin/documents/upload" `
        -H "Authorization: Bearer $token" `
        -F "file=@$filePath" `
        -F "categoryId=$categoryId"
    $upload = $uploadJson | ConvertFrom-Json
    if (-not $upload.success) { throw "Upload failed: $uploadJson" }
    $docId = $upload.data.id
    Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/v1/admin/documents/$docId/ingest" `
        -Headers @{ Authorization = "Bearer $token" } | Out-Null
    if ($approve) {
        Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/v1/admin/approvals/$docId/approve" `
            -Headers @{ Authorization = "Bearer $token" } | Out-Null
    }
    Write-Host "  $(Split-Path -Leaf $filePath) -> INDEXED"
}

Write-Host "=== RagLaw E2E corpus seed (PowerShell) ==="
for ($i = 1; $i -le 30; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health"
        if ($health.success) { break }
    } catch {}
    if ($i -eq 30) { throw "Backend not ready" }
    Start-Sleep -Seconds 2
}

$login = Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/v1/auth/login" `
    -ContentType "application/json" `
    -Body (@{ email = "admin@raglaw.local"; password = $AdminPassword } | ConvertTo-Json)
$script:Token = $login.data.accessToken
Write-Host "Logged in"

$socialSql = Join-Path $RepoRoot "docs\sql\mysql\005_seed_social_l3_category.sql"
if (Test-Path $socialSql) {
    try {
        docker exec -i raglaw-mysql mysql -uraglaw -praglaw --default-character-set=utf8mb4 raglaw `
            -e (Get-Content $socialSql -Raw) 2>$null | Out-Null
        Write-Host "Social L3 category synced"
    } catch {
        Write-Host "Social L3 category sync skipped (docker/mysql unavailable)"
    }
}

$files = @(
    @{ Path = "docs\fixtures\statutes\labor-contract-law-excerpt.md"; Category = "cat_l3_statute_civil_labor"; Approve = $false },
    @{ Path = "docs\fixtures\statutes\civil-code-contract-excerpt.md"; Category = "cat_l3_statute_civil_contract"; Approve = $false },
    @{ Path = "docs\fixtures\statutes\social-insurance-excerpt.md"; Category = "7b2f27be-dbb0-49fe-866a-059ad938bebe"; Approve = $false },
    @{ Path = "docs\fixtures\statutes\medical-insurance-remote-settlement.md"; Category = "7b2f27be-dbb0-49fe-866a-059ad938bebe"; Approve = $false },
    @{ Path = "docs\fixtures\cases\labor-overtime-case.md"; Category = "cat_l3_case_civil_labor"; Approve = $true }
)
foreach ($item in $files) {
    Upload-And-Ingest (Join-Path $RepoRoot $item.Path) $item.Category $item.Approve
}
Write-Host "E2E corpus ready"
