# Enterprise AI Knowledge & Operations Platform

A production-oriented foundation for turning governed enterprise knowledge into
traceable, human-supervised operations. The platform is being built as a secure
modular monolith with a Spring Boot API, React web client, PostgreSQL with
pgvector, and standards-based identity.

> **Current milestone: Identity and tenant isolation.** Day 2 adds a
> production-oriented local identity foundation, not production-ready identity.
> There is still no production AI functionality, document ingestion, retrieval,
> chat, MCP server, or business workflow implementation.

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
  workspaces, user profiles, and memberships, plus database constraints and
  indexes supporting tenant checks.
- `keycloak`: pinned Keycloak 26.7.0 development container with a deterministic
  imported realm, public web client, API audience, and synthetic users.
- `infra/nginx`: same-origin frontend and API reverse proxy.
- `docs`: product, architecture, security, ADR, and operational documentation.

Identity-provider roles and application memberships are deliberately separate:
realm roles answer what a principal may do, while PostgreSQL memberships answer
where they may do it. Server-side tenant authorization is applied even when the
frontend offers only authorized organizations.

See the [system context](docs/architecture/context.md),
[technology stack](docs/architecture/technology-stack.md),
[OIDC ADR](docs/adr/0003-use-oidc-identity-provider.md), and
[identity threat model](docs/security/identity-threat-model.md).

## Repository structure

```text
apps/api/                 Spring Boot API and integration tests
apps/web/                 React application and frontend tests
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

Start the complete identity-enabled stack:

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

The member dashboard exposes only its authorized organization:

![Authenticated Acme member dashboard](docs/assets/day-2-member-dashboard.jpg)

The platform-admin role additionally unlocks a protected system summary:

![Authenticated platform-admin dashboard](docs/assets/day-2-admin-dashboard.jpg)

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

## Verification

Run the repository test suites and static checks:

```bash
cd apps/api && ./mvnw verify
cd ../web && pnpm install --frozen-lockfile
pnpm lint
pnpm test --run
pnpm build
cd ../..
docker compose config --quiet
```

Run the real composed identity stack:

```bash
docker compose up --build --detach --wait
docker compose ps
make identity-verify
```

Backend integration tests require a working Docker daemon because they use a
real pgvector-enabled PostgreSQL container, never H2. CI repeats both suites,
builds the Compose stack, checks pgvector, and runs the identity/isolation
verifier.

For host-based development, realm reset behavior, issuer checks, and 401/403
troubleshooting, follow the
[local development runbook](docs/runbooks/local-development.md).

## Explicitly unfinished

Day 2 does not implement public registration, password reset, social login,
production identity-provider deployment, production secrets, billing, document
upload, object storage, embeddings, vector search, RAG, chat, MCP, audit business
workflows, Redis, Kafka, Kubernetes, or cloud deployment.

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
