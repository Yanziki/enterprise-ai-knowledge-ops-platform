# Initial Security Baseline

## Secrets and environment variables

- No credentials, tokens, private keys, customer content, or personal data in Git.
- `.env` is ignored; `.env.example` contains development-only placeholders.
- Production secrets must come from an approved secret manager, be rotated, and
  never appear in process arguments, images, frontend bundles, or logs.
- Configuration validation should fail startup clearly when database values are
  missing or malformed.

## Dependencies and supply chain

- Pin direct application versions and commit lockfiles/wrappers.
- Dependabot proposes updates; CI verifies every change.
- Review release notes, licenses, provenance, and advisories before upgrades.
- Container images must use explicit tags now and immutable digests before
  production; builds must use narrow contexts and non-root runtime users.

## Least privilege

- GitHub Actions receives read-only repository contents permission.
- Public API access is allowlisted. Unknown routes are denied by Spring Security.
- PostgreSQL is reachable only by services on the Compose network by default.
- Future runtime, migration, and administration identities must be separated.

## Endpoint baseline

Only `/api/v1/system/status` and the health, liveness, and readiness actuator
endpoints are public. All identity, administration, and tenant endpoints require
a validated bearer token. All unspecified requests are denied. Authentication
and authorization failures use generic 401/403 responses and must not disclose
tokens, claims, credentials, stack traces, or dependency topology.

## Identity and token trust boundary

- The API validates signature, issuer, expiry, and audience before trusting JWT
  claims. It maps only the Keycloak realm roles used by the application.
- Nginx and the frontend do not establish identity or authorization.
- Identity-provider roles grant platform capabilities; database memberships grant
  organization/workspace access. One must not be substituted for the other.
- The validated subject is resolved to a current application profile. Unknown
  subjects fail closed and are not implicitly provisioned.

## Tenant and confused-deputy controls

Tenant context is derived from verified identity and database membership rather
than request-supplied identifiers. Organization slugs select a resource but never
authorize it. A centralized tenant authorization service checks membership before
tenant repositories return data. Platform-admin bypass is explicit and tested.
Cross-tenant denial is tested in both Acme-to-Globex and Globex-to-Acme directions.
Future row filters, cache keys, background jobs, vector indexes, object paths,
logs, exports, and administrative impersonation must carry the same context.

## Browser token storage and logout

The OIDC client keeps user/token state in `sessionStorage`, not `localStorage`.
Tokens are attached only by the typed API client and are never logged or displayed.
This reduces persistence but does not mitigate successful same-origin XSS; the CSP
and dependency controls remain important. Logout removes local state and invokes
OIDC end-session, but access tokens may remain valid until expiry. Production must
decide on a backend-for-frontend, refresh-token rotation, revocation, and global
session behavior.

## Synthetic identity policy

The committed Keycloak realm contains only clearly labeled development identities
and fixed local passwords. Redirect URIs are restricted to localhost. These
credentials must never be reused, exposed to a shared environment, or described as
production-safe. Realm reset is destructive only to local synthetic data.

## Document-ingestion boundary

- Original documents live in a private S3-compatible bucket. Browser clients
  cannot address that bucket directly; every metadata or download operation is
  authorized again by the API. Object keys are backend-generated UUID paths and
  are not exposed in API responses.
- The server computes byte size and SHA-256 while staging a bounded stream; it
  does not trust the filename, browser media type, hash, tenant IDs, lifecycle,
  parser provenance, or object key supplied by a client.
- The parser surface is intentionally limited to PDF, strict UTF-8 plain text,
  and Markdown. Originals are capped at 20 MiB, PDF processing at 200 pages, and
  normalized output at 2,000,000 characters. Text is never rendered as raw HTML.
- Durable jobs carry tenant IDs and use database constraints, bounded claims,
  stale-work recovery, and a maximum of three attempts. User-facing failures
  contain stable safe codes rather than parser exceptions or stack traces.
- Extraction is not malware scanning. Day 3 has no antivirus or content
  disarm/reconstruction service and makes no claim that an accepted original is
  safe to open. Production rollout requires quarantining/scanning policy,
  separate least-privilege storage identities, encryption/retention decisions,
  and immutable image digests.

## Future LLM-specific risks—not implemented

Prompt injection, indirect injection, data exfiltration, insecure tool use,
retrieval poisoning, ungrounded claims, sensitive prompt logging, model-provider
retention, excessive agency, and denial-of-wallet must be threat-modeled. Model
output is untrusted. Write tools require authorization, constrained arguments,
human approval, idempotency, and durable audit evidence.
