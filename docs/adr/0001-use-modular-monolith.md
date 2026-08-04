# ADR 0001: Use a Modular Monolith

- Status: Accepted
- Date: 2026-08-01

## Context

The product spans identity, tenancy, knowledge, conversations, workflows, and
audit concerns, but its domain contracts and scale profile are not yet proven.
It needs strong boundaries without premature distributed-system complexity.

## Decision

Build the backend as one deployable Spring Boot application organized into
explicit domain packages: `identity`, `tenant`, `knowledge`, `conversation`,
`workflow`, `audit`, and `shared`. Cross-module access must use intentional
interfaces; shared code must remain small and infrastructure-oriented.

## Alternatives

- Microservices per tentative domain.
- An unstructured layered monolith organized only by technical role.
- Serverless functions for individual operations.

## Consequences

Atomic transactions, local refactoring, testing, and deployment stay simple.
The team must actively enforce package boundaries and avoid a growing `shared`
dumping ground. A single deployment scales as a unit until evidence supports
separation.

## Extraction criteria

A module may become a service when it has a stable contract and ownership,
meaningfully different scale or availability needs, independent data lifecycle,
and operational benefits that exceed network, consistency, security, and on-call
costs. Extraction requires an ADR, threat model, migration plan, and observed
metrics—not repository aesthetics.
