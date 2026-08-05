# Scripts

Repository automation belongs here when a task cannot be expressed clearly in
the root Makefile. Scripts must be non-interactive by default, fail on errors,
avoid secrets in output, and document prerequisites and side effects.

- `verify-identity-stack.sh` obtains short-lived tokens from the development-only
  Keycloak smoke client and verifies 401, role-based 403, authenticated identity,
  platform-admin access, and bidirectional Acme/Globex tenant isolation.

Run it only after copying `.env.example` to `.env` and starting the Compose stack.
It never prints access tokens and deletes its temporary response file on exit.
