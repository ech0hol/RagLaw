# Reset local dev corpus to MVP 3 fixtures (upload + ingest).
# Does not delete existing documents; for a clean slate, reset MySQL volume or clear raglaw_document manually.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@raglaw.local",
    [string]$AdminPassword = $(if ($env:RAGLAW_SEED_ADMIN_PASSWORD) { $env:RAGLAW_SEED_ADMIN_PASSWORD } else { "admin12345" })
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

Write-Host "=== RagLaw MVP corpus reset ==="
Write-Host "Backend: $BaseUrl"

$ready = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -Method Get | Out-Null
        $ready = $true
        break
    } catch {
        if ($i -eq 30) { throw "Backend not ready at $BaseUrl" }
        Start-Sleep -Seconds 2
    }
}

$loginBody = @{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token" }
Write-Host "Logged in"

$sqlPath = Join-Path $RepoRoot "docs\sql\mysql\004_seed_l3_categories.sql"
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $mysqlRunning = docker ps --format "{{.Names}}" 2>$null | Select-String -Pattern "^raglaw-mysql$"
    if ($mysqlRunning) {
        Get-Content $sqlPath -Raw | docker exec -i raglaw-mysql mysql -uraglaw -praglaw --default-character-set=utf8mb4 raglaw 2>$null
        Write-Host "L3 categories synced"
    }
}

function Upload-And-Ingest {
    param([string]$FilePath, [string]$CategoryId, [bool]$Approve)

    $fileName = [System.IO.Path]::GetFileName($FilePath)
    $form = @{
        file       = Get-Item -Path $FilePath
        categoryId = $CategoryId
    }
    $upload = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/documents/upload" -Method Post -Headers $headers -Form $form
    $docId = $upload.data.id
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/documents/$docId/ingest" -Method Post -Headers $headers | Out-Null
    if ($Approve) {
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/approvals/$docId/approve" -Method Post -Headers $headers | Out-Null
    }
    Write-Host "  $fileName -> INDEXED"
}

Upload-And-Ingest -FilePath (Join-Path $RepoRoot "docs\fixtures\statutes\labor-contract-law-excerpt.md") -CategoryId "cat_l3_statute_civil_labor" -Approve $false
Upload-And-Ingest -FilePath (Join-Path $RepoRoot "docs\fixtures\statutes\civil-code-contract-excerpt.md") -CategoryId "cat_l3_statute_civil_contract" -Approve $false
Upload-And-Ingest -FilePath (Join-Path $RepoRoot "docs\fixtures\cases\labor-overtime-case.md") -CategoryId "cat_l3_case_civil_labor" -Approve $true

Write-Host "MVP corpus ready (3 fixtures uploaded)"
