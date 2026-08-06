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
