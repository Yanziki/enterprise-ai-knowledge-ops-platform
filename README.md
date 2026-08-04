# Enterprise AI Knowledge & Operations Platform

A production-oriented foundation for turning governed enterprise knowledge into
traceable, human-supervised operations. The platform is being built as a secure
modular monolith with a Spring Boot API, React web client, and PostgreSQL with
pgvector.

> **Current milestone: Foundation.** This repository contains no production AI
> functionality yet. There are no LLM calls, embeddings, retrieval, RAG, chat,
> MCP tools, authentication flows, or business workflows in Day 1.

## Problem statement

Enterprise teams need answers and operational assistance grounded in approved
source material, with citations, authorization, human approval for mutations,
and an audit trail. This project establishes the engineering controls required
before those capabilities are introduced.

## Architecture overview

- `apps/api`: Java 21 and Spring Boot 4.1 API with secure-by-default routing.
- `apps/web`: React 19.2 and TypeScript single-page application.
- `postgres`: PostgreSQL 17 with the pgvector extension enabled by Flyway.
- `infra/nginx`: same-origin production frontend and API reverse proxy.
- `docs`: product, architecture, security, ADR, and runbook documentation.

See [system context](docs/architecture/context.md) and the
[technology stack](docs/architecture/technology-stack.md).

## Repository structure

```text
apps/api/                 Spring Boot API and tests
apps/web/                 React application and tests
infra/docker/             Container-related documentation
infra/nginx/              Production Nginx configuration
docs/product/             Product direction and policies
docs/architecture/        Architecture views and technology choices
docs/adr/                 Architecture decision records
docs/security/            Security baseline
docs/runbooks/            Operational procedures
scripts/                  Repository automation entry point
.github/                  Governance, CI, and dependency automation
```

## Prerequisites

- Java 21
- Docker Engine with Docker Compose v2
- Node.js 24 and pnpm 11.9
- Git

Maven is provided through `apps/api/mvnw`. JavaScript dependencies are locked
with `apps/web/pnpm-lock.yaml`.

## Quick start

```bash
cp .env.example .env
docker compose up --build
```

Open <http://localhost:8080>. The frontend calls the real API through Nginx and
reports backend status and service version.

For host-based development and troubleshooting, follow the
[local development runbook](docs/runbooks/local-development.md).

## Verification

```bash
make verify

cd apps/api && ./mvnw verify
cd apps/web && pnpm install --frozen-lockfile
cd apps/web && pnpm lint && pnpm test --run && pnpm build
docker compose config
```

Backend integration tests require a working Docker daemon because they use a
real pgvector-enabled PostgreSQL container, never H2.

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

Roadmap items describe intended direction, not completed features.
