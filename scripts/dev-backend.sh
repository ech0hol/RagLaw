#!/usr/bin/env bash
# Load root .env and start raglaw-server.
# Usage: ./scripts/dev-backend.sh [--skip-build]

set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"
ENV_EXAMPLE="$REPO_ROOT/.env.example"
SKIP_BUILD=false

for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=true ;;
  esac
done

if [[ ! -f "$ENV_FILE" ]]; then
  if [[ ! -f "$ENV_EXAMPLE" ]]; then
    echo ".env.example not found at $ENV_EXAMPLE" >&2
    exit 1
  fi
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo "Created .env from .env.example"
fi

set -a
# shellcheck disable=SC1090
source <(grep -v '^\s*#' "$ENV_FILE" | grep -v '^\s*$' | sed 's/\r$//')
set +a

echo "Loaded environment from $ENV_FILE"

cd "$REPO_ROOT/backend"
if [[ "$SKIP_BUILD" != "true" ]]; then
  mvn -pl raglaw-server -am install -DskipTests
fi
mvn -pl raglaw-server spring-boot:run
