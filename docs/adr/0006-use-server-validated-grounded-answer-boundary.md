# ADR 0006: Use a server-validated grounded answer boundary

- Status: Accepted
- Date: 2026-08-13

## Context

Day 4 returns authorized cited retrieval results but does not generate answers.
Day 5 needs bounded single-turn answers without allowing a model to choose tenants,
documents, raw context, provider endpoints, or trusted provenance.

## Decision

The application owns `LanguageModelProvider`, context assembly, structured-output
parsing, abstention, and citation validation. Authorization for
`ANSWER_FROM_KNOWLEDGE` happens before reusing the existing retrieval service.
Only Day 4 results can enter context. The application assigns request-local aliases
`C1..Cn`; the model returns aliases only, and the server maps them back to immutable
retrieval provenance.

The default provider is `none`. Local/test may explicitly select
`deterministic-smoke`. An optional `openai-compatible` HTTP adapter accepts only
server configuration: HTTPS base URL, model, environment API key, timeout, and
output-token limit. No provider SDK enters domain logic.

No database migration is needed because answers are stateless in this milestone.

## Consequences

- Fabricated, duplicate, malformed, or out-of-request citations fail closed.
- Archived, superseded, cross-tenant, or otherwise retrieval-ineligible content
  cannot enter generation.
- Remote provider enablement sends authorized enterprise question/evidence text
  across an external boundary and requires explicit privacy approval.
- Generated answers can still be wrong. Abstention and validation reduce but do
  not eliminate hallucination or prompt-injection risk.
- No chat history, memory, agent, tool, MCP, or workflow semantics are introduced.
