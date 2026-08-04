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
endpoints are public. No fake identity is introduced. All unspecified requests
are denied. Health responses must not disclose credentials, environment values,
stack traces, or dependency topology.

## Future tenant-isolation risks—not implemented

Tenant context must be derived from verified identity rather than request-supplied
identifiers. Future designs must address row filtering, cache keys, background
jobs, vector indexes, object paths, logs, exports, administrative access, and
confused-deputy flows. Cross-tenant negative tests are required before tenancy is
considered complete.

## Future LLM-specific risks—not implemented

Prompt injection, indirect injection, data exfiltration, insecure tool use,
retrieval poisoning, ungrounded claims, sensitive prompt logging, model-provider
retention, excessive agency, and denial-of-wallet must be threat-modeled. Model
output is untrusted. Write tools require authorization, constrained arguments,
human approval, idempotency, and durable audit evidence.
