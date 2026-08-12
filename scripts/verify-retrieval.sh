#!/usr/bin/env bash

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${REPOSITORY_ROOT}/.env}"
FIXTURE="${REPOSITORY_ROOT}/tests/fixtures/retrieval/composed-search.txt"
NEW_VERSION_FIXTURE="${REPOSITORY_ROOT}/tests/fixtures/retrieval/composed-search-v2.txt"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example to .env first." >&2
  exit 1
fi
if [[ ! -f "${FIXTURE}" ]]; then
  echo "Missing synthetic fixture ${FIXTURE}." >&2
  exit 1
fi
if [[ ! -f "${NEW_VERSION_FIXTURE}" ]]; then
  echo "Missing synthetic fixture ${NEW_VERSION_FIXTURE}." >&2
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
TOKEN_ENDPOINT="${OIDC_ISSUER_URI}/protocol/openid-connect/token"
DOCUMENTS_URL="${WEB_BASE_URL}/api/v1/organizations/acme/workspaces/operations/documents"
RETRIEVAL_URL="${WEB_BASE_URL}/api/v1/organizations/acme/workspaces/operations/retrieval"
TEMP_DIRECTORY="$(mktemp -d)"
WORKER_DISABLED=0
cleanup() {
  rm -rf "${TEMP_DIRECTORY}"
  if [[ "${WORKER_DISABLED}" == "1" ]]; then
    RETRIEVAL_WORKER_ENABLED=true docker compose \
      --project-directory "${REPOSITORY_ROOT}" \
      --env-file "${ENV_FILE}" \
      up --detach --no-deps --force-recreate --wait api >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

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

request_status() {
  local expected="$1"
  local label="$2"
  local method="$3"
  local url="$4"
  local token="${5:-}"
  local body="${6:-}"
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
  if [[ -n "${body}" ]]; then
    arguments+=(--header 'Content-Type: application/json' --data "${body}")
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

json_field() {
  local field="$1"
  node -e '
    const fs = require("node:fs");
    const value = JSON.parse(fs.readFileSync(process.argv[1], "utf8"))[process.argv[2]];
    if (typeof value !== "string" || value.length === 0) process.exit(1);
    process.stdout.write(value);
  ' "${TEMP_DIRECTORY}/response.json" "${field}"
}

assert_search_result() {
  local expected_mode="$1"
  local document_id="$2"
  node -e '
    const fs = require("node:fs");
    const response = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
    const [expectedMode, documentId] = process.argv.slice(2);
    if (response.effectiveMode !== expectedMode) process.exit(1);
    const result = response.results.find(item => item.citation?.documentId === documentId);
    if (!result || result.rank < 1 || !Number.isFinite(result.score)) process.exit(1);
    const citation = result.citation;
    if (citation.locatorType !== "DOCUMENT" || citation.locatorValue !== "body") process.exit(1);
    if (!citation.snippet.toLowerCase().includes("nebulacedar")) process.exit(1);
    if (!Number.isInteger(citation.startCharacter) || !Number.isInteger(citation.endCharacter)) process.exit(1);
    const serialized = JSON.stringify(response).toLowerCase();
    if (["objectkey", "accesskey", "secretkey", "embedding\":"].some(value => serialized.includes(value))) process.exit(1);
  ' "${TEMP_DIRECTORY}/response.json" "${expected_mode}" "${document_id}" || {
    echo "FAIL ${expected_mode} search did not return the expected bounded citation" >&2
    exit 1
  }
  echo "PASS ${expected_mode} search returns the expected provenance citation"
}

ADMIN_TOKEN="$(token_for 'admin@example.com' "${ADMIN_DEV_PASSWORD}")"
MEMBER_TOKEN="$(token_for 'member@example.com' "${MEMBER_DEV_PASSWORD}")"
OTHER_TOKEN="$(token_for 'other@example.com' "${OTHER_DEV_PASSWORD}")"
AUDITOR_TOKEN="$(token_for 'auditor@example.com' "${AUDITOR_DEV_PASSWORD:-AuditorDevOnly123!}")"

UPLOAD_STATUS="$(
  curl --silent --show-error \
    --request POST "${DOCUMENTS_URL}" \
    --output "${TEMP_DIRECTORY}/response.json" \
    --write-out '%{http_code}' \
    --header 'Accept: application/json' \
    --header "Authorization: Bearer ${ADMIN_TOKEN}" \
    --form "file=@${FIXTURE};type=text/plain" \
    --form 'title=Day 4 composed retrieval evidence'
)"
if [[ "${UPLOAD_STATUS}" != "202" ]]; then
  echo "FAIL retrieval fixture upload: expected HTTP 202, received ${UPLOAD_STATUS}" >&2
  exit 1
fi
echo 'PASS tenant administrator uploads the retrieval fixture: HTTP 202'

DOCUMENT_ID="$(json_field documentId)"
VERSION_ID="$(json_field versionId)"
DETAIL_URL="${DOCUMENTS_URL}/${DOCUMENT_ID}"

READY=0
for _ in $(seq 1 60); do
  request_status 200 'retrieval fixture metadata remains readable' GET "${DETAIL_URL}" "${MEMBER_TOKEN}" >/dev/null
  if grep -Fq '"ingestionStatus":"READY"' "${TEMP_DIRECTORY}/response.json"; then
    READY=1
    break
  fi
  if grep -Fq '"ingestionStatus":"FAILED"' "${TEMP_DIRECTORY}/response.json"; then
    echo "FAIL retrieval fixture ingestion reached FAILED" >&2
    exit 1
  fi
  sleep 1
done
if [[ "${READY}" != "1" ]]; then
  echo "FAIL retrieval fixture ingestion did not reach READY within 60 seconds" >&2
  exit 1
fi
echo 'PASS retrieval fixture ingestion reaches READY'

INDEX_READY=0
for _ in $(seq 1 60); do
  INDEX_STATE="$(database_scalar "
    SELECT COALESCE(MAX(status), 'MISSING')
    FROM retrieval_indexes
    WHERE document_version_id = '${VERSION_ID}'::uuid;
  ")"
  if [[ "${INDEX_STATE}" == "READY" ]]; then
    INDEX_READY=1
    break
  fi
  if [[ "${INDEX_STATE}" == "FAILED" ]]; then
    echo "FAIL retrieval indexing reached FAILED" >&2
    exit 1
  fi
  sleep 1
done
if [[ "${INDEX_READY}" != "1" ]]; then
  echo "FAIL retrieval indexing did not reach READY within 60 seconds" >&2
  exit 1
fi
echo 'PASS durable retrieval indexing reaches READY'

INDEX_EVIDENCE="$(database_scalar "
  SELECT COUNT(*) || ':' || MIN(index.embedding_provider) || ':' ||
         MIN(index.embedding_model) || ':' || MIN(index.embedding_dimension) || ':' ||
         COUNT(chunk.id)
  FROM retrieval_indexes AS index
  JOIN retrieval_chunks AS chunk ON chunk.retrieval_index_id = index.id
  WHERE index.document_version_id = '${VERSION_ID}'::uuid
    AND index.status = 'READY';
")"
if [[ "${INDEX_EVIDENCE}" != 1:deterministic-smoke:hashed-token-v1-test-only:64:* ]]; then
  echo "FAIL retrieval index provenance is incomplete: ${INDEX_EVIDENCE}" >&2
  exit 1
fi
echo "PASS retrieval index records provider/model/dimension and chunks: ${INDEX_EVIDENCE}"

request_status 401 'unauthenticated retrieval is rejected' POST "${RETRIEVAL_URL}/search" '' \
  '{"query":"nebulacedar","mode":"AUTO","topK":5}'
request_status 403 'cross-tenant retrieval is rejected before ranking' POST "${RETRIEVAL_URL}/search" "${OTHER_TOKEN}" \
  '{"query":"nebulacedar","mode":"AUTO","topK":5}'
request_status 403 'auditor content retrieval is rejected' POST "${RETRIEVAL_URL}/search" "${AUDITOR_TOKEN}" \
  '{"query":"nebulacedar","mode":"AUTO","topK":5}'
request_status 200 'authorized capabilities are available' GET "${RETRIEVAL_URL}/capabilities" "${MEMBER_TOKEN}"
node -e '
  const fs = require("node:fs");
  const value = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
  if (!value.lexicalAvailable || !value.vectorAvailable || value.autoMode !== "HYBRID") process.exit(1);
  if (value.embeddingProvider !== "deterministic-smoke" || value.embeddingDimension !== 64) process.exit(1);
  if (!["AUTO", "LEXICAL", "VECTOR", "HYBRID"].every(mode => value.availableModes.includes(mode))) process.exit(1);
' "${TEMP_DIRECTORY}/response.json" || {
  echo 'FAIL retrieval capabilities do not match the explicit smoke provider' >&2
  exit 1
}
echo 'PASS capabilities advertise explicit lexical/vector/hybrid availability'

for MODE in LEXICAL VECTOR HYBRID AUTO; do
  request_status 200 "${MODE} retrieval succeeds" POST "${RETRIEVAL_URL}/search" "${MEMBER_TOKEN}" \
    "{\"query\":\"nebulacedar\",\"mode\":\"${MODE}\",\"topK\":5}"
  if [[ "${MODE}" == "AUTO" ]]; then
    assert_search_result HYBRID "${DOCUMENT_ID}"
  else
    assert_search_result "${MODE}" "${DOCUMENT_ID}"
  fi
done

RETRIEVAL_WORKER_ENABLED=false docker compose \
  --project-directory "${REPOSITORY_ROOT}" \
  --env-file "${ENV_FILE}" \
  up --detach --no-deps --force-recreate --wait api >/dev/null
WORKER_DISABLED=1
NEW_VERSION_STATUS="$(
  curl --silent --show-error \
    --request POST "${DETAIL_URL}/versions" \
    --output "${TEMP_DIRECTORY}/response.json" \
    --write-out '%{http_code}' \
    --header 'Accept: application/json' \
    --header "Authorization: Bearer ${ADMIN_TOKEN}" \
    --form "file=@${NEW_VERSION_FIXTURE};type=text/plain"
)"
if [[ "${NEW_VERSION_STATUS}" != "202" ]]; then
  echo "FAIL new-version upload: expected HTTP 202, received ${NEW_VERSION_STATUS}" >&2
  exit 1
fi
NEW_VERSION_ID="$(json_field versionId)"

NEW_READY=0
for _ in $(seq 1 60); do
  request_status 200 'new-version metadata remains readable' GET "${DETAIL_URL}" "${MEMBER_TOKEN}" >/dev/null
  if node -e '
    const fs = require("node:fs");
    const detail = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
    const version = detail.versions.find(item => item.id === process.argv[2]);
    process.exit(version?.ingestionStatus === "READY" ? 0 : 1);
  ' "${TEMP_DIRECTORY}/response.json" "${NEW_VERSION_ID}"; then
    NEW_READY=1
    break
  fi
  sleep 1
done
if [[ "${NEW_READY}" != "1" ]]; then
  echo 'FAIL new version did not reach ingestion READY' >&2
  exit 1
fi
request_status 200 'last-known-good search succeeds before new index is READY' POST "${RETRIEVAL_URL}/search" "${MEMBER_TOKEN}" \
  '{"query":"nebulacedar","mode":"LEXICAL","topK":5}'
if ! grep -Fq "\"documentVersionId\":\"${VERSION_ID}\"" "${TEMP_DIRECTORY}/response.json" \
  || grep -Fq "\"documentVersionId\":\"${NEW_VERSION_ID}\"" "${TEMP_DIRECTORY}/response.json"; then
  echo 'FAIL version 1 was not the sole last-known-good citation before version 2 indexing' >&2
  exit 1
fi
echo 'PASS version 1 remains searchable while READY version 2 lacks a READY index'

RETRIEVAL_WORKER_ENABLED=true docker compose \
  --project-directory "${REPOSITORY_ROOT}" \
  --env-file "${ENV_FILE}" \
  up --detach --no-deps --force-recreate --wait api >/dev/null
WORKER_DISABLED=0
NEW_INDEX_READY=0
for _ in $(seq 1 60); do
  NEW_INDEX_STATE="$(database_scalar "
    SELECT COALESCE(MAX(status), 'MISSING')
    FROM retrieval_indexes
    WHERE document_version_id = '${NEW_VERSION_ID}'::uuid;
  ")"
  if [[ "${NEW_INDEX_STATE}" == "READY" ]]; then
    NEW_INDEX_READY=1
    break
  fi
  sleep 1
done
if [[ "${NEW_INDEX_READY}" != "1" ]]; then
  echo 'FAIL new-version retrieval index did not reach READY' >&2
  exit 1
fi
request_status 200 'new-version search succeeds after index cutover' POST "${RETRIEVAL_URL}/search" "${MEMBER_TOKEN}" \
  '{"query":"nebulacedar","mode":"LEXICAL","topK":5}'
if ! grep -Fq "\"documentVersionId\":\"${NEW_VERSION_ID}\"" "${TEMP_DIRECTORY}/response.json" \
  || grep -Fq "\"documentVersionId\":\"${VERSION_ID}\"" "${TEMP_DIRECTORY}/response.json"; then
  echo 'FAIL version 2 did not atomically replace version 1 after its index became READY' >&2
  exit 1
fi
echo 'PASS READY/indexed version 2 replaces version 1 without mixing historical results'

request_status 200 'tenant administrator archives the indexed document' POST "${DETAIL_URL}/archive" "${ADMIN_TOKEN}"
request_status 200 'search remains available after archive' POST "${RETRIEVAL_URL}/search" "${MEMBER_TOKEN}" \
  '{"query":"nebulacedar","mode":"LEXICAL","topK":20}'
if grep -Fq "${DOCUMENT_ID}" "${TEMP_DIRECTORY}/response.json"; then
  echo 'FAIL archived content remains retrievable' >&2
  exit 1
fi
echo 'PASS archive excludes all indexed versions before ranking'

echo 'Tenant-authorized lexical, vector, hybrid, citation, and archive verification passed.'
