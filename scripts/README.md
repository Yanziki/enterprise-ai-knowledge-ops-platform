# Scripts

Repository automation belongs here when a task cannot be expressed clearly in
the root Makefile. Scripts must be non-interactive by default, fail on errors,
avoid secrets in output, and document prerequisites and side effects.

- `verify-identity-stack.sh` obtains short-lived tokens from the development-only
  Keycloak smoke client and verifies 401, role-based 403, authenticated identity,
  platform-admin access, and bidirectional Acme/Globex tenant isolation. It also
  checks pgvector and explicit development fixtures, proves valid nullable and
  same-organization workspace memberships, and confirms the composite foreign
  key rejects an Acme membership referencing Globex Research.

Run it only after copying `.env.example` to `.env` and starting the Compose stack.
The database membership checks use transactions that are rolled back or rejected,
so they do not add fixture data. The script never prints access tokens and
deletes its temporary response files on exit.

- `verify-document-ingestion.sh` uploads the repository-owned TXT fixture as an
  authorized tenant administrator, polls the durable job to `READY`, verifies
  provenance and download hashes, proves MEMBER upload and cross-tenant access
  are denied, checks anonymous object-store access receives `403`, and archives
  the document. It obtains short-lived tokens without printing them and deletes
every temporary response and download on exit.

- `verify-answer.sh` uploads synthetic evidence, waits for its current retrieval
  index, and verifies grounded `ANSWERED`, safe `INSUFFICIENT_EVIDENCE`, canonical
  server citations, prompt-injection alias rejection, `401`/role/cross-tenant
  denial, and immediate archive exclusion. It never prints tokens or provider
  credentials and requires no remote model.

- `verify-review.sh` creates a deterministic insufficient-evidence answer, sends
  it to review as the synthetic member, opens the frozen evidence snapshot as
  the synthetic admin, claims and resolves it as a knowledge gap, and verifies
  the ordered append-only lifecycle timeline. It never prints access tokens or
  provider credentials and intentionally leaves the closed case in the local
  database as demo evidence.
