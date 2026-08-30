#!/usr/bin/env bash
# Seed minimal RAG corpus for Playwright E2E (login + reference cards).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@raglaw.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-${RAGLAW_SEED_ADMIN_PASSWORD:-admin12345}}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== RagLaw E2E corpus seed ==="
echo "Backend: $BASE_URL"

for i in $(seq 1 30); do
  if curl -sf "$BASE_URL/api/v1/health" >/dev/null; then
    break
  fi
  if [[ "$i" -eq 30 ]]; then
    echo "Backend not ready" >&2
    exit 1
  fi
  sleep 2
done

TOKEN="$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")"

echo "Logged in"

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q '^raglaw-mysql$'; then
  docker exec -i raglaw-mysql mysql -uraglaw -praglaw --default-character-set=utf8mb4 raglaw \
    < "$REPO_ROOT/docs/sql/mysql/004_seed_l3_categories.sql" >/dev/null 2>&1 || true
  docker exec -i raglaw-mysql mysql -uraglaw -praglaw --default-character-set=utf8mb4 raglaw \
    < "$REPO_ROOT/docs/sql/mysql/005_seed_social_l3_category.sql" >/dev/null 2>&1 || true
  echo "L3 categories synced"
fi

upload_and_ingest() {
  local file_path="$1"
  local category_id="$2"
  local approve="$3"
  local upload_json
  upload_json="$(curl -sf -X POST "$BASE_URL/api/v1/admin/documents/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$file_path" \
    -F "categoryId=$category_id")"
  local doc_id
  doc_id="$(echo "$upload_json" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")"
  curl -sf -X POST "$BASE_URL/api/v1/admin/documents/$doc_id/ingest" \
    -H "Authorization: Bearer $TOKEN" >/dev/null
  if [[ "$approve" == "true" ]]; then
    curl -sf -X POST "$BASE_URL/api/v1/admin/approvals/$doc_id/approve" \
      -H "Authorization: Bearer $TOKEN" >/dev/null
  fi
  echo "  $(basename "$file_path") -> INDEXED"
}

upload_and_ingest "$REPO_ROOT/docs/fixtures/statutes/labor-contract-law-excerpt.md" cat_l3_statute_civil_labor false
upload_and_ingest "$REPO_ROOT/docs/fixtures/statutes/civil-code-contract-excerpt.md" cat_l3_statute_civil_contract false
upload_and_ingest "$REPO_ROOT/docs/fixtures/statutes/social-insurance-excerpt.md" 7b2f27be-dbb0-49fe-866a-059ad938bebe false
upload_and_ingest "$REPO_ROOT/docs/fixtures/statutes/medical-insurance-remote-settlement.md" 7b2f27be-dbb0-49fe-866a-059ad938bebe false
upload_and_ingest "$REPO_ROOT/docs/fixtures/cases/labor-overtime-case.md" cat_l3_case_civil_labor true

echo "E2E corpus ready"
