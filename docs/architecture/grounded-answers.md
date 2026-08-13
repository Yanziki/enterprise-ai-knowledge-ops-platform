# Grounded answer architecture

## Scope and data flow

Day 5 adds one stateless, single-turn path:

`authenticated question -> ANSWER_FROM_KNOWLEDGE -> Day 4 retrieval -> bounded evidence JSON -> language model -> structured output -> citation validation -> ANSWERED | INSUFFICIENT_EVIDENCE`

The answer service resolves organization/workspace/current membership before any
retrieval or provider call. It calls `RetrievalSearchService.searchAuthorized` with
that already-authorized scope; it does not duplicate retrieval SQL. Therefore the
existing tenant, ACTIVE-document, READY-version, current READY-index,
last-known-good, and archive rules remain authoritative.

## Bounded context

Defaults are 2,000 question characters, 8 evidence chunks, 600 characters per
chunk, 8,000 serialized context characters, 4,000 answer characters, and 1,000
output tokens. Results keep retrieval rank order. Each chunk is truncated at a
fixed character boundary, assigned `C1..Cn`, and serialized as a JSON array.
Assembly stops before adding the first item that would exceed the total limit.

Prompt context contains titles, versions, locators, source offsets, and bounded
content but not tenant IDs, database document/version/chunk IDs, object keys,
credentials, or arbitrary URLs. Full provenance stays server-side.

## Model output and citation authority

Providers must return one JSON object with `status`, `answer`, and `citationIds`.
For `ANSWERED`, the answer must be non-empty and at least one unique valid alias is
required. For `INSUFFICIENT_EVIDENCE`, citations must be empty and the server emits
its canonical safe abstention text.

The model never supplies trusted provenance. Every alias must belong to the exact
request context; the backend reconstructs chunk/document/version/locator/offset
fields from the authorized retrieval result. Unknown, duplicate, malformed, or
abstention citations produce controlled `MODEL_OUTPUT_INVALID` failure.

## Providers and privacy

- `none`: production/default; answer requests return a controlled unavailable error.
- `deterministic-smoke`: local/test extractive plumbing only, never semantic quality.
- `openai-compatible`: optional explicit HTTP adapter for compatible chat-completion
  services. Configuration is server-owned and secrets come from the environment.

Remote inference transmits the bounded question and evidence text outside the
platform. Enablement requires organizational privacy/data-governance approval,
TLS, an approved endpoint/model, secret management, retention review, and provider
contract review. CI never requires network inference.

## Observability

Structured logs include request ID, tenant-scoped IDs, outcome, retrieval mode,
retrieved count, context size, citations, provider/model, optional token counts,
failure class, and latency. They exclude questions, prompts, chunks, answers,
embeddings, bearer/API keys, and object keys.
