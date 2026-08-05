# ADR 0003: Use an OIDC Identity Provider

- Status: Accepted
- Date: 2026-08-05
- Decision owners: Platform engineering

## Context

The platform needs authenticated browser sessions, API bearer-token validation,
role-aware administration, and an identity boundary that can later integrate with
enterprise identity. Building password storage, recovery, multi-factor
authentication, session management, and account lifecycle inside the application
would create avoidable security-critical responsibilities.

## Decision

Use OpenID Connect (OIDC) and OAuth 2.0 Authorization Code Flow with Proof Key for
Code Exchange (PKCE) for the React single-page application. The browser uses the
public `enterprise-ai-web` client and never receives a client secret. Keycloak
26.7.0 is the version-controlled local development identity provider.

The Spring Boot API is an OAuth 2.0 Resource Server. It accepts access tokens only
after cryptographic signature, issuer, expiration, and audience validation. It
maps allowlisted realm roles to Spring authorities. Controllers and application
services do not decode or trust unsigned browser claims.

Passwords, password policy, authentication UI, and identity-provider sessions are
delegated to the OIDC provider. The application database stores only the stable
OIDC subject and application profile/membership data; it never stores passwords.

Keycloak is local infrastructure, not the production identity-provider decision.
Production alternatives include a managed Keycloak deployment, Microsoft Entra
ID, Okta, Auth0, or another standards-compliant enterprise OIDC provider that can
meet issuer, audience, signing-key, lifecycle, MFA, and audit requirements.

## Token validation boundary

The browser obtains tokens through the authorization endpoint and attaches the
access token through one typed API client. The API is the trust boundary: Nginx,
the React UI, token contents displayed in developer tools, and organization slugs
in URLs do not establish authorization. Application tenant access is resolved by
joining the validated token subject to database memberships.

The local browser stores the OIDC user in `sessionStorage`, which limits persistence
to the current tab/session but remains accessible to JavaScript. Tokens are never
written to `localStorage`, rendered, or logged. A future production assessment may
replace the SPA bearer-token model with a backend-for-frontend and HttpOnly cookies.

## Logout limitations

Logout redirects to the provider's end-session endpoint and removes local OIDC
state. Already-issued access tokens remain valid until expiration unless the
provider and resource server use revocation or another session-binding mechanism.
Closing a tab clears `sessionStorage` but does not necessarily end the provider
single-sign-on session. Production logout, refresh-token rotation, revocation,
idle timeout, and global-session requirements remain deployment decisions.

## Consequences

- Local development adds a pinned Keycloak container and synthetic realm import.
- Browser login depends on the identity provider and on correct redirect URIs.
- The API depends on issuer/JWK availability and must fail closed when validation
  cannot be completed.
- Identity-provider roles and application memberships remain separate concepts.
- Tenant authorization requires database access on protected requests.
- Cross-tenant denial tests become mandatory for future tenant-owned resources.
- Local HTTP is acceptable only for localhost development; production requires TLS.

## Migration strategy

Keep OIDC configuration externalized and use only standard issuer discovery,
claims, and role mapping at the application boundary. A production provider can
replace Keycloak by changing issuer/client configuration and claim mapping, while
preserving immutable application memberships keyed by issuer plus subject. Before
supporting multiple issuers, extend the profile uniqueness model from subject-only
to `(issuer, subject)` through a forward Flyway migration.

