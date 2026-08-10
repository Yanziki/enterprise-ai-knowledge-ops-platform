# Enterprise AI Knowledge & Operations Platform

A production-oriented foundation for turning governed enterprise knowledge into
traceable, human-supervised operations. The platform is being built as a secure
modular monolith with a Spring Boot API, React web client, PostgreSQL with
pgvector, and standards-based identity.

> **Current milestone: Document ingestion and provenance.** Day 3 adds private
> original storage, immutable provenance, durable asynchronous extraction, and
> an access-controlled Knowledge workspace on top of the Day 2 identity and
> tenant boundary. Retrieval, embeddings, RAG, LLM calls, chat, MCP, and business
> workflows are still deliberately absent.

## Problem statement

Enterprise teams need answers and operational assistance grounded in approved
source material, with citations, authorization, human approval for mutations,
and an audit trail. This project establishes the engineering controls required
before those capabilities are introduced.

## Architecture overview

- `apps/api`: Java 21 and Spring Boot 4.1 resource server. It validates JWT
  signature, issuer, audience, expiry, and an allowlist of realm roles before
  resolving the token subject to a local user profile and membership.
- `apps/web`: React 19.2 and TypeScript single-page application using OIDC
  Authorization Code Flow with PKCE. Tokens remain in tab-scoped session
  storage and are sent through one typed bearer-token API client.
- `postgres`: PostgreSQL 17 with pgvector, Flyway-managed organizations,
  workspaces, memberships, documents, immutable versions, ingestion jobs, and
  ordered text units. Composite foreign keys enforce tenant ownership through
  the complete document/provenance hierarchy.
- `keycloak`: pinned Keycloak 26.7.0 development container with a deterministic
  imported realm, public web client, API audience, and synthetic users.
- `object-storage`: pinned MinIO development service with a private bucket. The
  API is the only browser-facing path for originals; object keys and storage
  credentials never leave the backend.
- `infra/nginx`: same-origin frontend and API reverse proxy.
- `docs`: product, architecture, security, ADR, and operational documentation.

Identity-provider roles and application memberships are deliberately separate:
realm roles answer what a principal may do, while PostgreSQL memberships answer
where they may do it. Server-side tenant authorization is applied even when the
frontend offers only authorized organizations.

See the [system context](docs/architecture/context.md),
[document-ingestion architecture](docs/architecture/document-ingestion.md),
[technology stack](docs/architecture/technology-stack.md), and
[document-ingestion threat model](docs/security/document-ingestion-threat-model.md).

## Repository structure

```text
apps/api/                 Spring Boot API and integration tests
apps/web/                 React application and frontend tests
apps/api/src/main/resources/db/devdata/
                          Explicit local/test synthetic application fixtures
infra/keycloak/           Development realm import
infra/nginx/              Production-style Nginx configuration
docs/product/             Product direction and policies
docs/architecture/        Architecture views and technology choices
docs/adr/                 Architecture decision records
docs/security/            Security baseline and threat models
docs/runbooks/            Operational procedures
scripts/                  Repository verification scripts
.github/                  Governance, CI, and dependency automation
```

## Prerequisites

- Java 21
- Docker Engine with Docker Compose v2
- Node.js 24 and pnpm 11.9
- Git

Maven is provided through `apps/api/mvnw`. JavaScript dependencies are locked
with `apps/web/pnpm-lock.yaml`.

## Local login

Start the complete identity and document-ingestion stack:

```bash
cp .env.example .env
docker compose up --build --detach --wait
```

Open <http://localhost:8080>, select **Log in with Keycloak**, and use one of
these development-only accounts:

| Account | Password | Realm role | Application membership |
| --- | --- | --- | --- |
| `admin@example.com` | `AdminDevOnly123!` | `PLATFORM_ADMIN` | `TENANT_ADMIN` in Acme |
| `member@example.com` | `MemberDevOnly123!` | `MEMBER` | `MEMBER` in Acme |
| `other@example.com` | `OtherDevOnly123!` | `MEMBER` | `MEMBER` in Globex |

These credentials and the direct-grant smoke client are synthetic local/CI
fixtures. Never reuse them or this Keycloak configuration in production.

## Migration and fixture boundary

The production migration location, `classpath:db/migration`, contains durable
schema only and never creates Acme, Globex, synthetic profiles, or memberships.
The explicit `local`, `container`, and `test` profiles additionally load
`classpath:db/devdata`, which contains the deterministic application fixtures as
the idempotent repeatable migration `R__synthetic_identity_fixtures.sql`.
Versioned migrations are reserved for durable schema evolution, so the fixture
does not advance the schema version. Day 3 adds forward-only V3 for the document,
version, job, and text-unit schema without modifying V1 or V2. If the repeatable
fixture checksum changes,
Flyway reruns its `INSERT ... ON CONFLICT DO NOTHING` statements without deleting,
overwriting, or duplicating existing fixture records. Synthetic application data
and Keycloak users remain local/test-only.

After a Flyway migration is merged or applied, never edit or repair it in place;
make corrections with a new forward migration.

The member dashboard exposes only its authorized organization:

![Authenticated Acme member dashboard](docs/assets/day-2-member-dashboard.jpg)

The platform-admin role additionally unlocks a protected system summary:

![Authenticated platform-admin dashboard](docs/assets/day-2-admin-dashboard.jpg)

## Knowledge workspace

The Knowledge workspace accepts PDF, UTF-8 plain-text, and Markdown originals.
Uploads are limited to 20 MiB, extracted normalized text to 2,000,000 characters,
and PDFs to 200 pages. An accepted upload returns `202 Accepted`, is stored under
a backend-generated opaque object key, and progresses asynchronously through
`QUEUED` and `PROCESSING` to `READY` or a safe `FAILED` state. Jobs survive API
restarts, use transactional claims, recover stale work, and stop after three
attempts. Authorized tenant administrators may retry a failed version.

Each immutable version records sanitized filename, declared and detected media
type, byte count, server-computed SHA-256, uploader subject, timestamps, parser
name/version, and ordered source locators. PDFs produce one text unit per page;
text and Markdown produce one document-body unit. A logical document can receive
new immutable versions or be archived; archive hides it from the default list but
does not pretend that the original or provenance was physically erased.

| Role | Metadata/provenance | Original download | Upload/new version | Archive/retry |
| --- | --- | --- | --- | --- |
| `PLATFORM_ADMIN` | Any tenant | Yes | Yes | Yes |
| `TENANT_ADMIN` | Authorized scope | Yes | Yes | Yes |
| `MEMBER` | Authorized scope | Yes | No | No |
| `AUDITOR` | Authorized scope | No | No | No |

Every operation re-resolves the authenticated subject, organization, workspace,
membership, role, and resource ownership on the server. The UI is not an
authorization boundary. Downloads are streamed through the API after that check;
the private MinIO/S3-compatible bucket is never made anonymous and the browser
receives no object key or storage credential.

The completed Knowledge UI and document provenance views are captured here:

![Tenant-admin Knowledge workspace](docs/assets/day-3-knowledge-workspace.jpg)

![Immutable document version provenance](docs/assets/day-3-document-provenance.jpg)

## Authorization behavior

- `/api/v1/system/status` and Actuator health probes remain public.
- `/api/v1/me` returns the authenticated local profile, allowlisted platform
  roles, organization memberships, and workspaces. An unknown token subject is
  denied instead of being auto-provisioned.
- `/api/v1/admin/system-summary` requires `PLATFORM_ADMIN`; an authenticated
  member receives `403 Forbidden`.
- `/api/v1/organizations/{slug}/summary` requires a matching organization
  membership unless the principal is a platform admin.
- Missing, expired, invalidly signed, wrong-issuer, or wrong-audience bearer
  tokens receive `401 Unauthorized`.

Run the live tenant-isolation demonstration after the stack is healthy:

```bash
make identity-verify
```

The verifier obtains short-lived synthetic tokens without printing them and
proves unauthenticated `401`, role-based `403`, authorized Acme/Globex access,
and denial in both cross-tenant directions.

Run the composed document-ingestion boundary demonstration:

```bash
make knowledge-verify
```

It proves unauthenticated upload `401`, member upload `403`, tenant-admin upload
`202`, asynchronous transition to `READY`, persisted provenance, byte-for-byte
authorized download with matching SHA-256, cross-tenant denial, anonymous object
storage denial, and archive behavior. It uses only synthetic fixtures and never
prints access tokens or storage credentials.

## Verification

Run the repository test suites and static checks:

```bash
cd apps/api && ./mvnw verify
cd ../web && pnpm install --frozen-lockfile
pnpm lint
pnpm test --run
pnpm build
cd ../..
docker compose --env-file .env.example config --quiet
```

Run the real composed identity stack:

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build --detach --wait
docker compose ps
make identity-verify
make knowledge-verify
```

Backend integration tests require a working Docker daemon because they use a
real pgvector-enabled PostgreSQL container, never H2. CI repeats both suites,
builds the Compose stack, checks pgvector, and runs the identity/isolation and
document-ingestion verifiers. The identity verifier also proves the repeatable
local fixture was explicitly applied without recording version 900, confirms V3 is the latest versioned
migration, accepts same-organization and nullable-workspace memberships in
rollback-only transactions, and confirms the database rejects Acme membership
paired with the Globex Research workspace.

For host-based development, realm reset behavior, issuer checks, and 401/403
troubleshooting, follow the
[local development runbook](docs/runbooks/local-development.md).

## Explicitly unfinished

Day 3 does not implement malware scanning, OCR, password-protected PDF support,
Office/image/HTML/URL/ZIP ingestion, physical retention deletion, per-object
encryption keys, separate production-grade object-storage identities, public
registration, password reset, social login, production identity deployment,
production secrets, billing, embeddings, vector search, retrieval, RAG, LLM
calls, chat, MCP, audit business workflows, Redis, Kafka, Kubernetes, or cloud
deployment. Extraction is bounded parsing, not a claim that uploaded content is
safe. See the threat model before extending the supported format surface.

Object storage and PostgreSQL are not written atomically. The API attempts a
narrow compensating object delete when database persistence fails, but a process
or container crash after the S3 write and before the metadata commit can leave an
orphaned object. Production hardening must add reconciliation and bounded orphan
garbage collection; Day 3 does not claim distributed transaction semantics.

## Contribution workflow

Work is issue-first, uses Conventional Commits, and reaches `main` through pull
requests. See [CONTRIBUTING.md](CONTRIBUTING.md) for branch names, required
checks, and the definition of done.

## Roadmap

1. Foundation: governed repository, API/web/database scaffolds, tests, CI.
2. Identity and tenant isolation with explicit threat modeling.
3. Document ingestion, provenance, and access-controlled storage.
4. Citation-grounded retrieval and automated evaluation.
5. Human-approved workflows, audit evidence, and an independent MCP server.
6. Hardened observability and production deployment.

Roadmap items describe intended direction, not completed production features.
