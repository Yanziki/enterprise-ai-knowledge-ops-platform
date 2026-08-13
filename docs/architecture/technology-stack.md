# Technology Stack

Versions are exact application or build-tool selections. Container tags are
also pinned in their Dockerfiles and `compose.yaml`; future upgrades must update
this document and the corresponding lockfiles together.

| Area | Selection | Version |
| --- | --- | --- |
| JVM | Java | 21 |
| Backend framework | Spring Boot | 4.1.0 |
| Backend build | Maven Wrapper / Maven | 3.3.4 / 3.9.11 |
| Database migration | Flyway (Boot-managed) | Spring Boot 4.1.0 BOM |
| Database driver | PostgreSQL JDBC (Boot-managed) | Spring Boot 4.1.0 BOM |
| Backend tests | JUnit / Testcontainers (Boot-managed) | Spring Boot 4.1.0 BOM |
| Web runtime | Node.js | 24.14.0 |
| Package manager | pnpm | 11.9.0 |
| UI | React / React DOM | 19.2.8 |
| Language | TypeScript | 6.0.3 |
| Build tool | Vite / React plugin | 8.2.0 / 6.0.5 |
| Unit tests | Vitest | 4.1.10 |
| DOM tests | React Testing Library | 16.3.2 |
| Lint / format | ESLint / Prettier | 10.8.0 / 3.9.6 |
| Database | PostgreSQL / pgvector | 17 / 0.8.1 |
| Reverse proxy | Nginx | 1.29.0 |
| Local identity provider | Keycloak | 26.7.0 |
| Browser OIDC integration | react-oidc-context / oidc-client-ts | 3.3.1 / 3.5.0 |
| API authentication | Spring Security OAuth2 Resource Server | Spring Boot 4.1.0 BOM |
| Object-storage client | AWS SDK for Java S3 | 2.46.8 |
| MIME detection | Apache Tika Core | 3.3.2 |
| PDF extraction | Apache PDFBox | 3.0.7 |
| Local object storage | MinIO server | RELEASE.2025-09-07T16-13-09Z |
| Local storage bootstrap | MinIO Client (`mc`) | RELEASE.2025-08-13T08-35-41Z |

## Why these technologies

- **Java 21 and Spring Boot 4.1:** an LTS JVM, mature security and operations
  ecosystem, explicit configuration, and production diagnostics.
- **Maven Wrapper:** makes the backend build independent of a globally installed
  Maven version and keeps CI/local behavior aligned.
- **PostgreSQL and pgvector:** transactional data and vector storage can share a
  governed operational boundary while the product validates its access model.
- **React, TypeScript, and Vite:** typed UI boundaries, a small initial surface,
  fast builds, and broad ecosystem support without a component framework.
- **pnpm:** strict, efficient dependency installation with a committed lockfile.
- **Testcontainers:** verifies schema, Flyway, and pgvector behavior against the
  real database family instead of an incompatible in-memory substitute.
- **Docker Compose and Nginx:** reproducible local integration and same-origin
  routing that resembles a production container boundary.
- **OIDC, Keycloak, and Spring Security:** delegate password authentication to a
  standards-based provider while preserving signed-token validation at the API.
- **react-oidc-context:** maintained React bindings over `oidc-client-ts` with
  Authorization Code + PKCE and explicit session-storage configuration.
- **S3-compatible object storage and AWS SDK:** originals stay outside the
  relational database behind a small application-owned interface, while local
  MinIO exercises the same private-bucket boundary used by the API and tests.
- **Tika Core and PDFBox:** Tika detects supported media types without enabling
  a broad parser surface; PDFBox performs explicitly bounded page extraction.

The Day 5 language-model boundary uses the JDK HTTP client and an
application-owned interface; no provider SDK or Spring AI dependency is added.
Spring AI remains absent until a separate compatibility/value decision.
Spring Modulith is also absent; module boundaries are expressed as Java packages
until compatibility and value are evaluated separately.

## Alternatives considered

- **Microservices:** rejected for Day 1 because distributed failure modes and
  operational overhead would precede validated domain boundaries.
- **Multiple repositories:** rejected because atomic contract changes and one CI
  entry point are more valuable at the current team and product size.
- **H2 for tests:** rejected because PostgreSQL extension and migration behavior
  are acceptance criteria.
- **Permissive CORS development:** rejected in favor of Vite/Nginx proxies that
  exercise a same-origin browser model.
- **Large UI framework:** deferred until repeatable interaction patterns justify
  its accessibility, bundle, and maintenance costs.
- **Custom username/password authentication:** rejected because credential
  storage, recovery, MFA, and session security belong to an identity provider.
- **Tokens in localStorage:** rejected because persistence expands the exposure
  window; Day 2 uses per-tab `sessionStorage` and records the remaining XSS risk.

## Version-upgrade policy

Dependabot proposes version changes. Each upgrade must be reviewed for release
notes and security advisories, update exact pins and lockfiles, and pass backend,
frontend, and container verification. Major upgrades require an issue and an ADR
when they change architectural constraints. Production images will be pinned by
immutable digest before the first deployment milestone.
