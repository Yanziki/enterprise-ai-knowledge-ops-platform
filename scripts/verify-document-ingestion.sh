#!/usr/bin/env bash

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${REPOSITORY_ROOT}/.env}"
FIXTURE="${REPOSITORY_ROOT}/tests/fixtures/knowledge/acme-policy.txt"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example to .env first." >&2
  exit 1
fi
if [[ ! -f "${FIXTURE}" ]]; then
  echo "Missing synthetic fixture ${FIXTURE}." >&2
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 24 is required to parse synthetic verification responses." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

WEB_BASE_URL="http://localhost:${WEB_PORT:-8080}"
OBJECT_STORAGE_BASE_URL="http://localhost:${OBJECT_STORAGE_PORT:-9000}"
TOKEN_ENDPOINT="${OIDC_ISSUER_URI}/protocol/openid-connect/token"
DOCUMENTS_URL="${WEB_BASE_URL}/api/v1/organizations/acme/workspaces/operations/documents"
TEMP_DIRECTORY="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIRECTORY}"' EXIT

token_for() {
  local username="$1"
  local password="$2"
  curl --fail --silent --show-error --retry 10 --retry-connrefused \
    --request POST "${TOKEN_ENDPOINT}" \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=enterprise-ai-smoke' \
    --data-urlencode 'scope=openid profile email' \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    | node -e '
      let input = "";
      process.stdin.on("data", chunk => input += chunk);
      process.stdin.on("end", () => {
        const token = JSON.parse(input).access_token;
        if (!token) process.exit(1);
        process.stdout.write(token);
      });
    '
}

request_status() {
  local expected="$1"
  local label="$2"
  local method="$3"
  local url="$4"
  local token="${5:-}"
  local file="${6:-}"
  local -a arguments=(
    --silent --show-error
    --request "${method}"
    --output "${TEMP_DIRECTORY}/response.json"
    --write-out '%{http_code}'
    --header 'Accept: application/json'
  )
  if [[ -n "${token}" ]]; then
    arguments+=(--header "Authorization: Bearer ${token}")
  fi
  if [[ -n "${file}" ]]; then
    arguments+=(--form "file=@${file};type=text/plain" --form 'title=Composed synthetic policy')
  fi

  local actual
  actual="$(curl "${arguments[@]}" "${url}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "FAIL ${label}: expected HTTP ${expected}, received ${actual}" >&2
    sed -n '1,5p' "${TEMP_DIRECTORY}/response.json" >&2
    exit 1
  fi
  echo "PASS ${label}: HTTP ${actual}"
}

assert_response_contains() {
  local expected="$1"
  local label="$2"
  if ! grep -Fq "${expected}" "${TEMP_DIRECTORY}/response.json"; then
    echo "FAIL ${label}: response marker was absent" >&2
    exit 1
  fi
  echo "PASS ${label}"
}

json_field() {
  local field="$1"
  node -e '
    const fs = require("node:fs");
    const value = JSON.parse(fs.readFileSync(process.argv[1], "utf8"))[process.argv[2]];
    if (typeof value !== "string" || value.length === 0) process.exit(1);
    process.stdout.write(value);
  ' "${TEMP_DIRECTORY}/response.json" "${field}"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

database_scalar() {
  local sql="$1"
  docker compose \
    --project-directory "${REPOSITORY_ROOT}" \
    --env-file "${ENV_FILE}" \
    exec -T postgres \
    psql -X --quiet --tuples-only --no-align --set=ON_ERROR_STOP=1 \
    --username "${POSTGRES_USER}" \
    --dbname "${POSTGRES_DB}" \
    --command "${sql}"
}

ADMIN_TOKEN="$(token_for 'admin@example.com' "${ADMIN_DEV_PASSWORD}")"
MEMBER_TOKEN="$(token_for 'member@example.com' "${MEMBER_DEV_PASSWORD}")"
OTHER_TOKEN="$(token_for 'other@example.com' "${OTHER_DEV_PASSWORD}")"
EXPECTED_SHA256="$(sha256_file "${FIXTURE}")"

request_status 401 'unauthenticated upload is rejected' POST "${DOCUMENTS_URL}" '' "${FIXTURE}"
request_status 403 'member upload is rejected' POST "${DOCUMENTS_URL}" "${MEMBER_TOKEN}" "${FIXTURE}"
request_status 202 'tenant-admin upload is accepted' POST "${DOCUMENTS_URL}" "${ADMIN_TOKEN}" "${FIXTURE}"

DOCUMENT_ID="$(json_field documentId)"
VERSION_ID="$(json_field versionId)"
UPLOAD_SHA256="$(json_field sha256Hex)"
if [[ "${UPLOAD_SHA256}" != "${EXPECTED_SHA256}" ]]; then
  echo "FAIL server SHA-256 differs from the independent fixture hash" >&2
  exit 1
fi
echo "PASS server-computed SHA-256 matches the fixture"

DETAIL_URL="${DOCUMENTS_URL}/${DOCUMENT_ID}"
DOWNLOAD_URL="${DETAIL_URL}/versions/${VERSION_ID}/download"
READY=0
for _ in $(seq 1 60); do
  request_status 200 'ingestion metadata remains readable while polling' GET "${DETAIL_URL}" "${ADMIN_TOKEN}" >/dev/null
  if grep -Fq '"ingestionStatus":"READY"' "${TEMP_DIRECTORY}/response.json"; then
    READY=1
    break
  fi
  if grep -Fq '"ingestionStatus":"FAILED"' "${TEMP_DIRECTORY}/response.json"; then
    echo "FAIL ingestion reached FAILED instead of READY" >&2
    exit 1
  fi
  sleep 1
done
if [[ "${READY}" != "1" ]]; then
  echo "FAIL ingestion did not reach READY within 60 seconds" >&2
  exit 1
fi
echo "PASS durable ingestion reached READY"
assert_response_contains "\"sha256Hex\":\"${EXPECTED_SHA256}\"" 'provenance exposes the expected SHA-256'
assert_response_contains '"parserName":"JDK UTF-8"' 'parser provenance is recorded'
assert_response_contains '"textUnitCount":1' 'normalized text unit is recorded'
assert_response_contains '"createdBySubject":"00000000-0000-0000-0000-000000000001"' 'uploader is derived from the authenticated subject'

request_status 200 'authorized member can list documents' GET "${DOCUMENTS_URL}" "${MEMBER_TOKEN}"
assert_response_contains "${DOCUMENT_ID}" 'member listing contains the uploaded document'

DOWNLOAD_STATUS="$(
  curl --silent --show-error \
    --output "${TEMP_DIRECTORY}/downloaded.txt" \
    --write-out '%{http_code}' \
    --header "Authorization: Bearer ${MEMBER_TOKEN}" \
    "${DOWNLOAD_URL}"
)"
if [[ "${DOWNLOAD_STATUS}" != "200" ]]; then
  echo "FAIL authorized download: expected HTTP 200, received ${DOWNLOAD_STATUS}" >&2
  exit 1
fi
if ! cmp --silent "${FIXTURE}" "${TEMP_DIRECTORY}/downloaded.txt"; then
  echo "FAIL downloaded bytes differ from the uploaded fixture" >&2
  exit 1
fi
if [[ "$(sha256_file "${TEMP_DIRECTORY}/downloaded.txt")" != "${EXPECTED_SHA256}" ]]; then
  echo "FAIL downloaded SHA-256 differs from provenance" >&2
  exit 1
fi
echo "PASS authorized download bytes and SHA-256 match provenance"

request_status 403 'cross-tenant listing is rejected' GET "${DOCUMENTS_URL}" "${OTHER_TOKEN}"
request_status 403 'cross-tenant metadata access is rejected' GET "${DETAIL_URL}" "${OTHER_TOKEN}"
request_status 403 'cross-tenant download is rejected' GET "${DOWNLOAD_URL}" "${OTHER_TOKEN}"
request_status 403 'cross-tenant archive is rejected' POST "${DETAIL_URL}/archive" "${OTHER_TOKEN}"

OBJECT_KEY="$(
  database_scalar "SELECT object_key FROM document_versions WHERE id = '${VERSION_ID}'::uuid;"
)"
ANONYMOUS_STATUS="$(
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
    "${OBJECT_STORAGE_BASE_URL}/${OBJECT_STORAGE_BUCKET}/${OBJECT_KEY}"
)"
if [[ "${ANONYMOUS_STATUS}" != "403" ]]; then
  echo "FAIL private bucket check: expected anonymous HTTP 403, received ${ANONYMOUS_STATUS}" >&2
  exit 1
fi
echo "PASS object storage rejects anonymous reads"

request_status 200 'tenant admin archives the document' POST "${DETAIL_URL}/archive" "${ADMIN_TOKEN}"
assert_response_contains '"status":"ARCHIVED"' 'archive lifecycle is persisted'

echo 'Document ingestion, provenance, download, and isolation verification passed.'
