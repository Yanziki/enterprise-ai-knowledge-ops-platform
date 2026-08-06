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

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required to verify the local database." >&2
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

database_sql() {
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

assert_database_scalar() {
  local expected="$1"
  local label="$2"
  local sql="$3"
  local actual
  actual="$(database_sql "${sql}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "FAIL ${label}: expected ${expected}, received ${actual}" >&2
    exit 1
  fi
  echo "PASS ${label}: ${actual}"
}

verify_workspace_ownership_invariant() {
  if ! database_sql "
    BEGIN;
    INSERT INTO user_profiles (id, identity_subject, email, display_name)
    VALUES (
      '39999999-0000-0000-0000-000000000001',
      'local-invariant-valid',
      'local-invariant-valid@example.test',
      'Local Invariant Verification'
    );
    INSERT INTO memberships (id, user_profile_id, organization_id, workspace_id, role)
    VALUES
      (
        '49999999-0000-0000-0000-000000000001',
        '39999999-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        NULL,
        'MEMBER'
      ),
      (
        '49999999-0000-0000-0000-000000000002',
        '39999999-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'MEMBER'
      );
    ROLLBACK;
  " >/dev/null; then
    echo "FAIL valid organization and workspace memberships were rejected" >&2
    exit 1
  fi
  echo "PASS organization-level membership permits a NULL workspace"
  echo "PASS same-organization workspace membership is accepted"

  if database_sql "
    BEGIN;
    INSERT INTO user_profiles (id, identity_subject, email, display_name)
    VALUES (
      '39999999-0000-0000-0000-000000000002',
      'local-invariant-invalid',
      'local-invariant-invalid@example.test',
      'Local Invariant Verification'
    );
    INSERT INTO memberships (id, user_profile_id, organization_id, workspace_id, role)
    VALUES (
      '49999999-0000-0000-0000-000000000003',
      '39999999-0000-0000-0000-000000000002',
      '10000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000002',
      'MEMBER'
    );
    ROLLBACK;
  " >"${TEMP_DIRECTORY}/invalid-membership.log" 2>&1; then
    echo "FAIL cross-organization workspace membership was accepted" >&2
    exit 1
  fi
  if ! grep -Fq 'memberships_workspace_organization_fk' \
    "${TEMP_DIRECTORY}/invalid-membership.log"; then
    echo "FAIL cross-organization membership failed for an unexpected reason" >&2
    exit 1
  fi
  echo "PASS cross-organization workspace membership is rejected by the composite foreign key"
}

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

assert_database_scalar 1 'pgvector extension is installed' \
  "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector';"
assert_database_scalar 0 'no version 900 migration is recorded' \
  "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '900' AND success;"
assert_database_scalar 2 'latest successful versioned migration is V2' \
  "SELECT MAX(version::integer) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';"
assert_database_scalar 1 'repeatable development fixture is applied successfully' \
  "SELECT CASE WHEN COUNT(*) >= 1 THEN 1 ELSE 0 END FROM flyway_schema_history WHERE version IS NULL AND description = 'synthetic identity fixtures' AND success;"
assert_database_scalar 2 'development organizations are loaded' \
  'SELECT COUNT(*) FROM organizations;'
assert_database_scalar 2 'development workspaces are loaded' \
  'SELECT COUNT(*) FROM workspaces;'
assert_database_scalar 3 'development profiles are loaded' \
  'SELECT COUNT(*) FROM user_profiles;'
assert_database_scalar 3 'development memberships are loaded' \
  'SELECT COUNT(*) FROM memberships;'
verify_workspace_ownership_invariant

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
