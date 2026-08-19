#!/usr/bin/env bash

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${REPOSITORY_ROOT}/.env}"
TEMP_DIRECTORY="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIRECTORY}"' EXIT

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.example to .env first." >&2
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 24 is required to parse synthetic review responses." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

WEB_BASE_URL="http://localhost:${WEB_PORT:-8080}"
TOKEN_ENDPOINT="${OIDC_ISSUER_URI}/protocol/openid-connect/token"
WORKSPACE_URL="${WEB_BASE_URL}/api/v1/organizations/acme/workspaces/operations"

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

request() {
  local expected="$1"
  local label="$2"
  local method="$3"
  local url="$4"
  local token="$5"
  local output="$6"
  local body="${7:-}"
  local -a arguments=(
    --silent --show-error --request "${method}"
    --output "${output}" --write-out '%{http_code}'
    --header 'Accept: application/json'
    --header "Authorization: Bearer ${token}"
  )
  if [[ -n "${body}" ]]; then
    arguments+=(--header 'Content-Type: application/json' --data "${body}")
  fi
  local actual
  actual="$(curl "${arguments[@]}" "${url}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "FAIL ${label}: expected HTTP ${expected}, received ${actual}" >&2
    sed -n '1,5p' "${output}" >&2
    exit 1
  fi
  echo "PASS ${label}: HTTP ${actual}"
}

json_field() {
  local file="$1"
  local field="$2"
  node -e '
    const fs = require("fs");
    const value = JSON.parse(fs.readFileSync(process.argv[1], "utf8"))[process.argv[2]];
    if (value === undefined || value === null) process.exit(1);
    process.stdout.write(String(value));
  ' "${file}" "${field}"
}

ADMIN_TOKEN="$(token_for 'admin@example.com' "${ADMIN_DEV_PASSWORD}")"
MEMBER_TOKEN="$(token_for 'member@example.com' "${MEMBER_DEV_PASSWORD}")"

request 200 "unsupported grounded question" POST \
  "${WORKSPACE_URL}/answers" "${MEMBER_TOKEN}" "${TEMP_DIRECTORY}/answer.json" \
  '{"question":"What is the Zephyrquartz lunar expense policy?","retrievalMode":"LEXICAL","retrievalTopK":5}'

node -e '
  const answer = require(process.argv[1]);
  if (answer.status !== "INSUFFICIENT_EVIDENCE" || answer.citations.length !== 0) process.exit(1);
' "${TEMP_DIRECTORY}/answer.json"
ANSWER_ID="$(json_field "${TEMP_DIRECTORY}/answer.json" requestId)"
echo "PASS persisted answer abstains without citations"

request 201 "member creates review" POST \
  "${WORKSPACE_URL}/answers/${ANSWER_ID}/review-case" "${MEMBER_TOKEN}" \
  "${TEMP_DIRECTORY}/created.json" \
  '{"reason":"INSUFFICIENT_EVIDENCE","note":"Deterministic Day 6 knowledge-gap demo."}'
CASE_ID="$(json_field "${TEMP_DIRECTORY}/created.json" id)"

request 200 "reviewer opens frozen evidence" GET \
  "${WORKSPACE_URL}/review-cases/${CASE_ID}" "${ADMIN_TOKEN}" \
  "${TEMP_DIRECTORY}/detail.json"
node -e '
  const review = require(process.argv[1]);
  if (review.answerId !== process.argv[2]) process.exit(1);
  if (review.status !== "OPEN" || review.evidence.length !== 0) process.exit(1);
  if ("provider" in review || "model" in review || "providerId" in review) process.exit(1);
' "${TEMP_DIRECTORY}/detail.json" "${ANSWER_ID}"
echo "PASS original question, abstention, and empty evidence snapshot are frozen"

request 200 "reviewer claims case" POST \
  "${WORKSPACE_URL}/review-cases/${CASE_ID}/claim" "${ADMIN_TOKEN}" \
  "${TEMP_DIRECTORY}/claimed.json"
VERSION="$(json_field "${TEMP_DIRECTORY}/claimed.json" version)"

request 200 "reviewer resolves knowledge gap" POST \
  "${WORKSPACE_URL}/review-cases/${CASE_ID}/resolve" "${ADMIN_TOKEN}" \
  "${TEMP_DIRECTORY}/resolved.json" \
  "{\"resolution\":\"KNOWLEDGE_GAP\",\"reviewerNote\":\"No approved source covers this question.\",\"version\":${VERSION}}"

request 200 "ordered append-only timeline" GET \
  "${WORKSPACE_URL}/review-cases/${CASE_ID}/audit-events" "${ADMIN_TOKEN}" \
  "${TEMP_DIRECTORY}/audit.json"
node -e '
  const events = require(process.argv[1]).map(event => event.eventType);
  const expected = ["REVIEW_CASE_CREATED", "REVIEW_CASE_CLAIMED", "REVIEW_CASE_RESOLVED"];
  if (JSON.stringify(events) !== JSON.stringify(expected)) process.exit(1);
' "${TEMP_DIRECTORY}/audit.json"
echo "PASS review timeline is CREATED -> CLAIMED -> RESOLVED"
echo "Review workflow verification passed. Closed case: ${CASE_ID}"
