# ADR 0002: Use a Monorepo

- Status: Accepted
- Date: 2026-08-01

## Context

The API, web client, container topology, governance, and documentation evolve as
one product. Contract changes regularly span these boundaries.

## Decision

Store backend, frontend, infrastructure, automation, and documentation in one
repository with application-local build files and a root verification entry
point.

## Alternatives

- Separate repositories for API, web, and infrastructure.
- A JavaScript workspace controlling all builds.
- A generated umbrella repository containing submodules.

## Consequences

Pull requests can change contracts atomically and CI provides one evidence set.
Repository permissions and checkout size are shared, so path-based ownership and
selective CI may be needed as the team grows. Applications keep independent
dependency managers to avoid artificial build coupling.
