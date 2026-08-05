#!/usr/bin/env bash

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${REPOSITORY_ROOT}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example to .env first." >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js is required to parse the token response. Install Node.js 24 or add it to PATH." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

WEB_BASE_URL="http://localhost:${WEB_PORT:-8080}"
TOKEN_ENDPOINT="${OIDC_ISSUER_URI}/protocol/openid-connect/token"
TEMP_DIRECTORY="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIRECTORY}"' EXIT

token_for() {
  local username="$1"
  local password="$2"
  local response
  response="$(
    curl --fail --silent --show-error --retry 10 --retry-connrefused \
      --request POST "${TOKEN_ENDPOINT}" \
      --header 'Content-Type: application/x-www-form-urlencoded' \
      --data-urlencode 'grant_type=password' \
      --data-urlencode 'client_id=enterprise-ai-smoke' \
      --data-urlencode 'scope=openid profile email' \
      --data-urlencode "username=${username}" \
      --data-urlencode "password=${password}"
  )"
  printf '%s' "${response}" | node -e '
    let input = "";
    process.stdin.on("data", chunk => input += chunk);
    process.stdin.on("end", () => {
      const token = JSON.parse(input).access_token;
      if (!token) process.exit(1);
      process.stdout.write(token);
    });
  '
}

assert_status() {
  local expected="$1"
  local label="$2"
  local url="$3"
  local token="${4:-}"
  local actual
  if [[ -n "${token}" ]]; then
    actual="$(
      curl --silent --show-error --output "${TEMP_DIRECTORY}/response.json" \
        --write-out '%{http_code}' \
        --header 'Accept: application/json' \
        --header "Authorization: Bearer ${token}" \
        "${url}"
    )"
  else
    actual="$(
      curl --silent --show-error --output "${TEMP_DIRECTORY}/response.json" \
        --write-out '%{http_code}' \
        --header 'Accept: application/json' \
        "${url}"
    )"
  fi

  if [[ "${actual}" != "${expected}" ]]; then
    echo "FAIL ${label}: expected HTTP ${expected}, received ${actual}" >&2
    exit 1
  fi
  echo "PASS ${label}: HTTP ${actual}"
}

assert_body_contains() {
  local expected="$1"
  local label="$2"
  if ! grep -Fq "${expected}" "${TEMP_DIRECTORY}/response.json"; then
    echo "FAIL ${label}: expected response marker was absent" >&2
    exit 1
  fi
  echo "PASS ${label}"
}

MEMBER_TOKEN="$(token_for 'member@example.com' "${MEMBER_DEV_PASSWORD}")"
OTHER_TOKEN="$(token_for 'other@example.com' "${OTHER_DEV_PASSWORD}")"
ADMIN_TOKEN="$(token_for 'admin@example.com' "${ADMIN_DEV_PASSWORD}")"

assert_status 401 'unauthenticated /me is rejected' "${WEB_BASE_URL}/api/v1/me"

assert_status 200 'member /me is authorized' "${WEB_BASE_URL}/api/v1/me" "${MEMBER_TOKEN}"
assert_body_contains '"email":"member@example.com"' 'member identity returned'
assert_body_contains '"slug":"acme"' 'member Acme membership returned'

assert_status 403 'member admin access is denied' \
  "${WEB_BASE_URL}/api/v1/admin/system-summary" "${MEMBER_TOKEN}"
assert_status 200 'platform admin access is authorized' \
  "${WEB_BASE_URL}/api/v1/admin/system-summary" "${ADMIN_TOKEN}"

assert_status 200 'Acme member can access Acme' \
  "${WEB_BASE_URL}/api/v1/organizations/acme/summary" "${MEMBER_TOKEN}"
assert_status 403 'Acme member cannot access Globex' \
  "${WEB_BASE_URL}/api/v1/organizations/globex/summary" "${MEMBER_TOKEN}"
assert_status 200 'Globex member can access Globex' \
  "${WEB_BASE_URL}/api/v1/organizations/globex/summary" "${OTHER_TOKEN}"
assert_status 403 'Globex member cannot access Acme' \
  "${WEB_BASE_URL}/api/v1/organizations/acme/summary" "${OTHER_TOKEN}"

echo 'Identity and tenant-isolation smoke verification passed.'
