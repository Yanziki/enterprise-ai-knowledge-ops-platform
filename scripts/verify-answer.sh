#!/usr/bin/env bash

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${REPOSITORY_ROOT}/.env}"
FIXTURE="${REPOSITORY_ROOT}/tests/fixtures/answer/composed-answer.txt"
TEMP_DIRECTORY="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIRECTORY}"' EXIT

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example to .env first." >&2
  exit 1
fi
if [[ ! -f "${FIXTURE}" ]]; then
  echo "Missing synthetic answer fixture ${FIXTURE}." >&2
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 24 is required to parse synthetic answer responses." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

WEB_BASE_URL="http://localhost:${WEB_PORT:-8080}"
TOKEN_ENDPOINT="${OIDC_ISSUER_URI}/protocol/openid-connect/token"
DOCUMENTS_URL="${WEB_BASE_URL}/api/v1/organizations/acme/workspaces/operations/documents"
ANSWERS_URL="${WEB_BASE_URL}/api/v1/organizations/acme/workspaces/operations/answers"

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
      process.stdin.on("end", () => process.stdout.write(JSON.parse(input).access_token || ""));
    '
}

request_status() {
  local expected="$1"
  local label="$2"
  local url="$3"
  local token="${4:-}"
  local body="$5"
  local -a arguments=(
    --silent --show-error --request POST
    --output "${TEMP_DIRECTORY}/response.json"
    --write-out '%{http_code}'
    --header 'Accept: application/json'
    --header 'Content-Type: application/json'
    --data "${body}"
  )
  if [[ -n "${token}" ]]; then
    arguments+=(--header "Authorization: Bearer ${token}")
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

ADMIN_TOKEN="$(token_for 'admin@example.com' "${ADMIN_DEV_PASSWORD}")"
MEMBER_TOKEN="$(token_for 'member@example.com' "${MEMBER_DEV_PASSWORD}")"
OTHER_TOKEN="$(token_for 'other@example.com' "${OTHER_DEV_PASSWORD}")"
AUDITOR_TOKEN="$(token_for 'auditor@example.com' "${AUDITOR_DEV_PASSWORD}")"

UPLOAD_STATUS="$(
  curl --silent --show-error \
    --request POST "${DOCUMENTS_URL}" \
    --output "${TEMP_DIRECTORY}/response.json" \
    --write-out '%{http_code}' \
    --header 'Accept: application/json' \
    --header "Authorization: Bearer ${ADMIN_TOKEN}" \
    --form "file=@${FIXTURE};type=text/plain" \
    --form 'title=Day 5 grounded answer evidence'
)"
if [[ "${UPLOAD_STATUS}" != "202" ]]; then
  echo "FAIL answer fixture upload: expected HTTP 202, received ${UPLOAD_STATUS}" >&2
  exit 1
fi
DOCUMENT_ID="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).documentId' "${TEMP_DIRECTORY}/response.json")"
VERSION_ID="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).versionId' "${TEMP_DIRECTORY}/response.json")"
echo 'PASS tenant administrator uploads the answer fixture: HTTP 202'

READY=0
for _ in $(seq 1 60); do
  STATE="$(
    docker compose --project-directory "${REPOSITORY_ROOT}" --env-file "${ENV_FILE}" \
      exec -T postgres psql -X --quiet --tuples-only --no-align \
      --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
      --command "SELECT COUNT(*) FROM retrieval_indexes WHERE document_version_id = '${VERSION_ID}'::uuid AND status = 'READY';"
  )"
  if [[ "${STATE}" == "1" ]]; then
    READY=1
    break
  fi
  sleep 1
done
if [[ "${READY}" != "1" ]]; then
  echo 'FAIL answer fixture did not reach retrieval READY' >&2
  exit 1
fi
echo 'PASS answer fixture reaches durable retrieval READY'

BODY='{"question":"Copperlark","retrievalMode":"AUTO","retrievalTopK":5}'
request_status 401 'unauthenticated answer is rejected' "${ANSWERS_URL}" '' "${BODY}"
request_status 403 'cross-tenant answer is rejected before retrieval' "${ANSWERS_URL}" "${OTHER_TOKEN}" "${BODY}"
request_status 403 'auditor answer is rejected' "${ANSWERS_URL}" "${AUDITOR_TOKEN}" "${BODY}"
request_status 200 'authorized grounded answer succeeds' "${ANSWERS_URL}" "${MEMBER_TOKEN}" "${BODY}"
node -e '
  const fs = require("node:fs");
  const response = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
  const [documentId, versionId] = process.argv.slice(2);
  if (response.status !== "ANSWERED" || !response.answer.includes("two reviewers")) process.exit(1);
  if (response.citations.length !== 1 || response.citations[0].citationId !== "C1") process.exit(1);
  if (response.citations[0].documentId !== documentId || response.citations[0].documentVersionId !== versionId) process.exit(1);
  if (JSON.stringify(response).includes("C999\"")) process.exit(1);
' "${TEMP_DIRECTORY}/response.json" "${DOCUMENT_ID}" "${VERSION_ID}" || {
  echo 'FAIL answer or server-owned citation provenance is invalid' >&2
  exit 1
}
echo 'PASS answer is grounded in the authorized source and fabricated C999 is not trusted'

request_status 200 'insufficient evidence returns a controlled response' "${ANSWERS_URL}" "${MEMBER_TOKEN}" \
  '{"question":"unrelatedmoonpayroll","retrievalMode":"LEXICAL","retrievalTopK":5}'
node -e '
  const fs = require("node:fs");
  const response = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
  if (response.status !== "INSUFFICIENT_EVIDENCE" || response.citations.length !== 0) process.exit(1);
' "${TEMP_DIRECTORY}/response.json" || {
  echo 'FAIL insufficient evidence did not abstain safely' >&2
  exit 1
}
echo 'PASS insufficient evidence abstains with no citations'

request_status 200 'tenant administrator archives answer source' \
  "${DOCUMENTS_URL}/${DOCUMENT_ID}/archive" "${ADMIN_TOKEN}" '{}'
request_status 200 'archived-only answer request remains safe' "${ANSWERS_URL}" "${MEMBER_TOKEN}" "${BODY}"
if ! grep -Fq '"status":"INSUFFICIENT_EVIDENCE"' "${TEMP_DIRECTORY}/response.json"; then
  echo 'FAIL archived content remained available to answer generation' >&2
  exit 1
fi
echo 'PASS archived content is excluded before generation'

echo 'Grounded answer, citation authority, abstention, and tenant isolation verification passed.'
