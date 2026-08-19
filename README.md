# Enterprise AI Knowledge & Operations Platform

A production-oriented foundation for turning governed enterprise knowledge into
traceable, human-supervised operations. The platform is being built as a secure
modular monolith with a Spring Boot API, React web client, PostgreSQL with
pgvector, and standards-based identity.

> **Current milestone: Human review and append-only audit.** Day 6 persists the
> exact bounded Day 5 answer/evidence snapshot, lets authorized users escalate
> uncertainty into a workspace-scoped review, serializes reviewer claims, and
> records every lifecycle transition as a typed audit event. Chat, memory,
> agents, MCP, tools, and automated workflow execution remain absent.

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
  workspaces, memberships, documents, immutable versions, ingestion/index jobs,
  ordered text units, cited retrieval chunks, answer snapshots, review cases,
  and audit events. Composite foreign keys enforce tenant ownership through the
  complete provenance and review hierarchy.
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
[retrieval architecture](docs/architecture/retrieval.md),
[grounded-answer architecture](docs/architecture/grounded-answers.md),
[human-review architecture](docs/day6-review-workflow.md),
[technology stack](docs/architecture/technology-stack.md), and
[grounded RAG threat model](docs/security/rag-threat-model.md).

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

Start the complete identity, ingestion, indexing, and retrieval stack:

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
| `auditor@example.com` | `AuditorDevOnly123!` | `AUDITOR` | `AUDITOR` in Acme |

These credentials and the direct-grant smoke client are synthetic local/CI
fixtures. Never reuse them or this Keycloak configuration in production.

## Migration and fixture boundary

The production migration location, `classpath:db/migration`, contains durable
schema only and never creates Acme, Globex, synthetic profiles, or memberships.
The explicit `local`, `container`, and `test` profiles additionally load
`classpath:db/devdata`, which contains the deterministic application fixtures as
the idempotent repeatable migration `R__synthetic_identity_fixtures.sql`.
Versioned migrations are reserved for durable schema evolution, so the fixture
does not advance the schema version. Day 3 added V3 for document ingestion; Day 4
added V4 for retrieval indexes, durable index jobs, and chunks; Day 6 adds V5 for
answer snapshots, review cases, and audit events without modifying V1–V4. If the
repeatable fixture checksum changes,
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

## Cited retrieval

Day 4 completes this non-generative pipeline:

`upload → durable extraction → provenance-preserving chunking → retrieval index → tenant-filtered lexical/vector candidates → RRF fusion → cited results`

Only READY text units from ACTIVE documents are eligible. The scheduled reconciler
backfills READY versions created before V4, and PostgreSQL-backed jobs claim work
transactionally, recover stale claims, retry at most three times, and commit chunks
with the READY transition. Normal search uses the newest READY index per logical
document; the previous READY/indexed version remains available until the replacement
index succeeds. Archival removes every version from candidate SQL immediately.

Search supports `LEXICAL`, `VECTOR`, `HYBRID`, and `AUTO`. Lexical candidates use
PostgreSQL full-text search; vectors use exact pgvector cosine distance; hybrid uses
Reciprocal Rank Fusion with `k=60`. Tenant/workspace, ACTIVE-document, READY-version,
and current-index predicates execute inside both candidate queries before ranking.
Results expose immutable chunk, document, version, character-offset, and persisted
page/document-locator provenance with bounded plain-text snippets.

The base/production default has no embedding provider: lexical stays available,
`AUTO` reports lexical, and explicit vector/hybrid requests fail with a controlled
conflict. The local Compose/test profile explicitly uses a 64-dimensional
`deterministic-smoke` hashed-token provider so CI can exercise vector plumbing
without secrets, paid APIs, or internet. It is not a meaningful semantic model.
Enabling any future remote provider would send normalized document text and search
queries across an external trust boundary and requires explicit configuration plus
organizational privacy/data-governance approval.

| Role | Cited content search | Retrieval/index metadata |
| --- | --- | --- |
| `PLATFORM_ADMIN` | Any tenant | Yes |
| `TENANT_ADMIN` | Authorized workspace | Yes |
| `MEMBER` | Authorized workspace | Capabilities only |
| `AUDITOR` | No (`403`) | Existing document provenance only |

![Tenant-authorized cited hybrid search](docs/assets/day-4-cited-hybrid-search.png)

## Grounded answers

`POST /api/v1/organizations/{organization}/workspaces/{workspace}/answers` checks
`ANSWER_FROM_KNOWLEDGE` before retrieval or generation. It accepts only a bounded
question, retrieval mode, and topK. Clients cannot supply prompts, provider URLs,
models, credentials, raw context, tenant IDs, or provenance.

Only authorized Day 4 results enter a deterministic JSON evidence envelope. The
server assigns `C1..Cn`; models return structured `ANSWERED` or
`INSUFFICIENT_EVIDENCE` output, and the backend rejects fabricated, duplicate, or
malformed aliases before reconstructing immutable citations. Retrieved documents
are untrusted data, including any embedded instructions. This does not make model
answers infallible or eliminate prompt-injection/hallucination risk.

Production defaults to `ANSWER_PROVIDER=none`. Local/CI explicitly use the
test-only `deterministic-smoke` provider. `openai-compatible` is optional and must
be explicitly configured with an approved HTTPS base URL, model, environment API
key, timeout, and output limit. Remote use transmits bounded enterprise question
and evidence text outside the platform and requires privacy/data-governance review.

The **Ask Knowledge** UI is single-turn and renders plain text plus canonical
document/version/locator citations; it has no conversation history or generated
HTML.

![Grounded answer with server-validated citation provenance](docs/assets/day-5-grounded-answer.png)

## Human review and audit

Every Day 5 answer attempt is now persisted with the exact bounded evidence and
canonical provenance that entered its context. A member can request human review
from the answer card, then follow their own case in the workspace Review Inbox.
Tenant and platform administrators can inspect the frozen question, answer, and
evidence, claim one unassigned case, and resolve or dismiss it. Review detail does
not rerun retrieval or generation.

The enforced lifecycle is `OPEN → IN_REVIEW → RESOLVED`, with dismissal allowed
from `OPEN` or `IN_REVIEW`. Invalid or stale transitions return `409`. Claim and
completion lock the scoped review row, while a version column prevents stale UI
decisions. A partial database index allows at most one active case per answer.
State changes and their `REVIEW_CASE_CREATED`, `REVIEW_CASE_CLAIMED`,
`REVIEW_CASE_RESOLVED`, or `REVIEW_CASE_DISMISSED` event commit together.

Members can create/view only their own review content. `TENANT_ADMIN` and
`PLATFORM_ADMIN` reviewers can manage cases within their authorized scope;
`AUDITOR` remains unable to read answer/evidence content. Review responses omit
provider configuration, model identifiers, prompts, object keys, credentials,
and tokens. See the [Day 6 review workflow](docs/day6-review-workflow.md) for the
API, schema, race handling, and local demo.

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

Run the composed retrieval boundary demonstration:

```bash
make retrieval-verify
```

It uploads synthetic content, waits for ingestion/index readiness, validates index
model provenance, exercises lexical/vector/hybrid/AUTO citations, proves `401`,
Auditor and cross-tenant `403`, proves last-known-good new-version cutover, archives
the document, and confirms its chunks disappear from normal search. No token,
credential, object key, vector, or external provider is exposed.

Run the composed grounded-answer boundary demonstration:

```bash
make answer-verify
```

It proves answer `401`/role/cross-tenant denial, canonical server citations,
controlled abstention, prompt-injection alias rejection, and archive exclusion
using only the deterministic local provider.

Run the deterministic human-review demonstration:

```bash
make review-verify
```

It creates an `INSUFFICIENT_EVIDENCE` answer, escalates it as the member, opens
the frozen evidence snapshot as the admin, claims and resolves the case as a
`KNOWLEDGE_GAP`, and verifies the ordered append-only audit timeline. It uses
only synthetic local identities and never prints tokens or credentials.

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
make retrieval-verify
make answer-verify
make review-verify
```

Backend integration tests require a working Docker daemon because they use a
real pgvector-enabled PostgreSQL container, never H2. CI repeats both suites,
builds the Compose stack, checks pgvector, and runs identity, ingestion,
retrieval, and answer verifiers. The backend suite writes and CI publishes
`retrieval-evaluation.json` and `answer-evaluation.json`. The identity verifier
also proves the repeatable local fixture was explicitly applied without
recording version 900, confirms V5 is the latest versioned
migration, accepts same-organization and nullable-workspace memberships in
rollback-only transactions, and confirms the database rejects Acme membership
paired with the Globex Research workspace.

For host-based development, realm reset behavior, issuer checks, and 401/403
troubleshooting, follow the
[local development runbook](docs/runbooks/local-development.md).

## Explicitly unfinished

Day 6 does not implement malware scanning, OCR, password-protected PDF support,
Office/image/HTML/URL/ZIP ingestion, physical retention deletion, per-object
encryption keys, separate production-grade object-storage identities, public
registration, password reset, social login, production identity deployment,
production secrets, billing, a production embedding adapter, production LLM
approval/operations, ANN indexes, model reindex orchestration, chat, memory,
reranking, agents, MCP, tool calling, Redis, Kafka, Elasticsearch, OpenSearch,
Kubernetes, or cloud deployment. It also does not implement review notifications,
SLAs, reassignment, generic BPMN, or automated remediation. Extraction is bounded parsing, not a
claim that uploaded content is safe. The `simple` PostgreSQL tokenizer has limited
stemming and CJK segmentation, and exact vector scans target the current bounded
corpus rather than large-scale production workloads.

Object storage and PostgreSQL are not written atomically. The API attempts a
narrow compensating object delete when database persistence fails, but a process
or container crash after the S3 write and before the metadata commit can leave an
orphaned object. Production hardening must add reconciliation and bounded orphan
garbage collection; Day 6 does not claim distributed transaction semantics.

## Contribution workflow

Work is issue-first, uses Conventional Commits, and reaches `main` through pull
requests. See [CONTRIBUTING.md](CONTRIBUTING.md) for branch names, required
checks, and the definition of done.

## Roadmap

1. Foundation: governed repository, API/web/database scaffolds, tests, CI.
2. Identity and tenant isolation with explicit threat modeling.
3. Document ingestion, provenance, and access-controlled storage.
4. Citation-grounded retrieval and automated evaluation.
5. Grounded single-turn answers, server-validated citations, and abstention.
6. Human review workflow and append-only audit evidence.
7. Independent MCP boundary, hardened observability, and production deployment.

Roadmap items describe intended direction, not completed production features.
