# Clear all STATUTE and CASE documents from the knowledge base (admin API).
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminEmail = "admin@raglaw.local",
    [string]$AdminPassword = $(if ($env:RAGLAW_SEED_ADMIN_PASSWORD) { $env:RAGLAW_SEED_ADMIN_PASSWORD } else { "admin12345" }),
    [string]$DocTypes = "STATUTE,CASE"
)

$ErrorActionPreference = "Stop"

Write-Host "=== RagLaw clear knowledge documents ==="
Write-Host "Backend: $BaseUrl"
Write-Host "Doc types: $DocTypes"

for ($i = 1; $i -le 30; $i++) {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -Method Get | Out-Null
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

$result = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/documents?docType=$DocTypes" -Method Delete -Headers $headers
$deleted = $result.data.deleted
Write-Host "Deleted $deleted document(s)"

$stats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/knowledge/stats" -Method Get -Headers $headers
Write-Host "Remaining — statutes: $($stats.data.statuteCount), cases: $($stats.data.caseCount)"
