# Local Development Runbook

This runbook operates the development-only identity and document-ingestion
stack. The imported realm, users, passwords, direct-grant smoke client,
application data, and object-storage credentials are deterministic test
fixtures, not a production deployment.

## Prerequisites

Install Git, Java 21, Node.js 24, pnpm 11.9, and Docker Engine with Compose v2.
Confirm Docker has enough resources for PostgreSQL, Keycloak, MinIO, and
application image builds.

## Complete container startup

```bash
git clone https://github.com/Yanziki/enterprise-ai-knowledge-ops-platform.git
cd enterprise-ai-knowledge-ops-platform
cp .env.example .env
docker compose config --quiet
docker compose up --build --detach --wait
docker compose ps
curl --fail http://localhost:8080/api/v1/system/status
curl --fail http://localhost:8080/actuator/health/readiness
open http://localhost:8080
```

The service URLs are:

| Service | URL |
| --- | --- |
| Web application | <http://localhost:8080> |
| API direct port | <http://localhost:8081> |
| Keycloak | <http://localhost:8082> |
| Keycloak administration | <http://localhost:8082/admin> |
| MinIO S3 API (not a public document URL) | <http://localhost:9000> |

Sign in through the web application with `admin@example.com`,
`member@example.com`, or `other@example.com`. Their development passwords are
defined in `.env.example` and summarized in the project README.

Stop the stack without deleting the PostgreSQL volume:

```bash
docker compose down --remove-orphans
```

Keycloak currently uses its container-local development database, so deleting
its container also deletes local identity state.

## Migration and fixture profiles

Flyway locations are selected only through explicit Spring profiles:

| Configuration | Flyway locations | Demonstration data |
| --- | --- | --- |
| Base/default production style | `classpath:db/migration` | None |
| `local` | `classpath:db/migration,classpath:db/devdata` | Acme/Globex fixtures |
| `container` | `classpath:db/migration,classpath:db/devdata` | Acme/Globex fixtures |
| `test` | `classpath:db/migration,classpath:db/devdata` | Same deterministic test fixtures |

`V2__identity_and_tenant_foundation.sql` contains only durable schema. Versioned
migrations are reserved for durable schema evolution, and V3 is the latest
versioned migration after Day 3. The explicitly enabled
`db/devdata/R__synthetic_identity_fixtures.sql` repeatable migration contains the
application organizations, workspaces, profiles, and memberships without
advancing the versioned schema number. Its deterministic inserts use
`ON CONFLICT DO NOTHING`: changing
the repeatable fixture causes Flyway to rerun it without destructive reset logic,
overwriting records, or creating duplicates. Synthetic application data and
Keycloak users remain local/test-only. No hostname detection or startup seeder
selects these fixtures.

The test suite also migrates a second clean PostgreSQL container with only
`classpath:db/migration` and asserts that every tenant table is empty. Once any
migration is merged or applied to non-disposable data, correct it only with a
new forward migration—never by editing the applied file.

## Realm import behavior

`infra/keycloak/realm-enterprise-ai.json` is mounted read-only and Keycloak runs
with `start-dev --import-realm`. On a new Keycloak container the
`enterprise-ai` realm, clients, roles, mappers, and synthetic users are imported
automatically. Keycloak ignores an import when the realm already exists; a
plain container restart therefore does not apply edits to the JSON.

To discard only the disposable local identity state and re-import the realm:

```bash
docker compose up --detach --force-recreate --wait keycloak
docker compose up --detach --wait api web
```

This does not delete the named PostgreSQL application volume. Existing browser
sessions use signing keys from the old realm and must log in again.

## Login and isolation verification

After the complete stack is healthy:

```bash
make identity-verify
```

The script uses the development-only `enterprise-ai-smoke` client to obtain
short-lived tokens. It never prints tokens and deletes temporary responses. It
asserts:

- unauthenticated `/api/v1/me` returns 401;
- an Acme member can read Acme but receives 403 for Globex;
- a Globex member can read Globex but receives 403 for Acme;
- a member receives 403 from the admin endpoint;
- a platform admin can read the admin summary.
- pgvector and the explicit repeatable fixture migration exist;
- Flyway records no version 900 and reports V3 as the latest versioned migration;
- organization-level and valid same-organization workspace memberships succeed;
- an Acme membership paired with Globex Research is rejected by
  `memberships_workspace_organization_fk`.

The valid membership checks run inside a transaction that is rolled back. The
invalid check is also rolled back when PostgreSQL rejects it, so verification
does not add fixture rows.

## Document-ingestion verification

Run the real storage, parser, provenance, authorization, and lifecycle boundary:

```bash
make knowledge-verify
```

The verifier uploads `tests/fixtures/knowledge/acme-policy.txt` as the Acme
tenant administrator, polls the durable job to `READY`, checks parser/uploader/
locator provenance, and compares the downloaded bytes and SHA-256 to the source.
It also proves unauthenticated and member uploads are denied, Globex cannot list,
read, download, or archive the Acme document, anonymous MinIO access is denied,
and the administrator can archive the logical document. Tokens, credentials,
and object keys are not printed.

## Host development

Start the infrastructure containers:

```bash
docker compose up --detach --wait postgres keycloak object-storage object-storage-init
```

Then run the API and web app in separate terminals:

```bash
cd apps/api
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

cd apps/web
pnpm install --frozen-lockfile
pnpm dev
```

Vite runs at <http://localhost:5173> and proxies `/api` and `/actuator` to
`http://localhost:8081`; no wildcard CORS configuration is required. The realm
allowlists the exact `8080` and `5173` development redirect origins.

## Check JWT issuer configuration

The browser-visible issuer and the API's expected issuer must be identical:

```bash
curl --fail --silent \
  http://localhost:8082/realms/enterprise-ai/.well-known/openid-configuration \
  | jq -r .issuer
grep '^OIDC_ISSUER_URI=' .env
```

Both should report `http://localhost:8082/realms/enterprise-ai`. In the Compose
API container, `OIDC_JWK_SET_URI` intentionally uses the internal hostname
`keycloak:8080`, while issuer validation still expects the external localhost
issuer present in each token.

## Common failures

- **Docker daemon unavailable:** start Docker Desktop/Engine and rerun
  `docker info`. Backend integration tests require Docker.
- **Database unhealthy:** inspect `docker compose logs postgres`; verify the
  credentials in `.env` match the API variables.
- **Flyway checksum mismatch:** never use `flyway repair` to conceal drift. If
  the database contains only disposable local data, use the complete reset below;
  otherwise stop and introduce a forward migration.
- **Flyway version 900 exists locally:** an earlier development-fixture layout
  recorded V900. Reset the disposable project volume with
  `docker compose down --volumes --remove-orphans`; do not enable out-of-order
  execution or use `flyway repair` to rewrite migration history.
- **Keycloak unhealthy or realm absent:** inspect
  `docker compose logs keycloak`; confirm the realm JSON is valid, then force
  recreate Keycloak as described above.
- **Invalid redirect URI:** the web port or exact return URI does not match the
  allowlist in `realm-enterprise-ai.json`. Keep exact localhost entries—do not
  introduce a wildcard. After an intentional port change, update both login and
  post-logout entries and force recreate Keycloak.
- **401 Unauthorized:** confirm a bearer token is present and unexpired, then
  compare its issuer/audience to `OIDC_ISSUER_URI` and `OIDC_AUDIENCE`. Re-login
  after recreating Keycloak because its signing key changes. API logs can also
  reveal failed signature, issuer, audience, or expiry validation.
- **403 Forbidden:** authentication succeeded but the allowlisted realm role,
  local profile, or application membership does not authorize the operation.
  Confirm the token `sub` matches a seeded `user_profiles.identity_subject` and
  that the requested organization has an organization-level membership.
- **Frontend reports unavailable:** call the status URL directly and inspect
  web and API logs. The production-style browser path should use the Nginx
  origin at port 8080.
- **Object storage unhealthy:** inspect `docker compose logs object-storage` and
  confirm port 9000 is free. The readiness probe uses MinIO's health endpoint.
- **Bucket bootstrap failed:** inspect
  `docker compose logs object-storage-init`; confirm the synthetic access key,
  secret, and bucket name agree across `.env` and the API configuration. The
  one-shot bootstrap is idempotent and must finish before the API starts.
- **Upload reaches `FAILED`:** inspect safe failure code/provenance in the
  Knowledge view, then inspect API logs for operator detail. Do not expose raw
  parser errors to users. Unsupported/encrypted/corrupt content is expected to
  fail closed; a tenant administrator may retry only while attempts remain.
- **Anonymous object URL is readable:** stop the stack and treat it as a security
  defect. Re-run `mc anonymous set none` through the bootstrap and
  `make knowledge-verify` before continuing.
- **Port in use:** stop the conflicting process or deliberately update host
  ports and the exact Keycloak redirect settings.
- **Stale dependencies:** use the committed Maven wrapper and
  `pnpm install --frozen-lockfile`; never hand-edit the lockfile.

## Database inspection

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Run `make identity-verify` for the non-mutating valid and negative composite
membership checks instead of manually inserting inconsistent data.

## Complete reset

The following removes only this Compose project's containers and named database
and object-storage volumes. It deletes all local application rows and original
documents, and resets Keycloak so the realm is imported again:

```bash
docker compose down --volumes --remove-orphans
docker compose up --build --detach --wait
```

Back up any needed local data first. Volume deletion is not recoverable. For a
local snapshot, stop writers and use a PostgreSQL logical backup plus an object
copy made with `mc mirror`; restore both halves together so version rows never
refer to missing originals. Production backup/retention design is not yet
implemented.

## Logs

Container logs are written to the Docker logging driver:

```bash
docker compose logs --follow postgres keycloak object-storage object-storage-init api web
```

Host API logs go to its terminal; Vite development logs go to the frontend
terminal. Day 3 does not configure external log shipping.
