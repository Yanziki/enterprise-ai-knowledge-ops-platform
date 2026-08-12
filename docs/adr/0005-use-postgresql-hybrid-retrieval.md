# ADR 0005: Use PostgreSQL for Tenant-Scoped Hybrid Retrieval

- Status: Accepted
- Date: 2026-08-12

## Context

Day 3 stores authorized originals, immutable versions, and normalized text units
with source locators. Day 4 must search that content without weakening tenant,
workspace, lifecycle, or provenance controls. Candidate authorization must happen
before ranking, and CI cannot depend on a paid or internet-hosted model.

The existing database is PostgreSQL 17 with pgvector 0.8.1. PostgreSQL provides
transactional ownership joins, full-text search, exact vector distance, and
durable job coordination in the same consistency boundary as document metadata.

## Decision

Use PostgreSQL for both retrieval paths:

- a generated `tsvector` with a GIN index and `websearch_to_tsquery('simple', ...)`
  for lexical candidates;
- pgvector's cosine-distance operator for exact vector candidates;
- Reciprocal Rank Fusion with `k = 60` over independently ranked candidate lists;
- tenant, workspace, active-document, READY-version, READY-index, and
  last-known-good predicates inside each candidate SQL query.

Use an application-owned `EmbeddingProvider` interface. No remote provider is
enabled by default. A deterministic 64-dimensional hashed-feature provider exists
only in `test`, local smoke, and composed verification configuration. It proves
plumbing and vector correctness; it is not a semantic-quality claim. A future
remote adapter must be explicitly configured and governance-approved.

Do not add Spring AI. Spring AI 2.0 is compatible with Spring Boot 4.1, but Day 4
needs only a small batch/query embedding boundary. Adding its broader model API
and provider dependency graph would not simplify PostgreSQL-owned authorization,
indexing, or evaluation and would make accidental chat/model scope more likely.

Use an unconstrained pgvector `vector` column and exact scans for this milestone.
Every vector query filters provider, model, and dimension before applying cosine
distance. This permits deliberate future model generations without an ANN index
that assumes one dimension. ANN/HNSW is deferred until measured scale warrants a
model-specific index strategy.

## Consequences

- Ownership and archive/version filters remain in one database query boundary.
- Retrieval is operational without embeddings: lexical search remains available.
- Explicit VECTOR/HYBRID requests fail safely when embeddings are unavailable;
  AUTO reports LEXICAL rather than pretending semantic search occurred.
- Exact vector scans are acceptable for the bounded Day 4 corpus but do not
  target billion-chunk scale.
- The `simple` full-text configuration is predictable and language-neutral but
  has limited stemming and weak CJK segmentation. Specialized multilingual
  analysis is future work.
- Changing chunker or embedding configuration creates a new immutable generation;
  incompatible vector spaces are never mixed.
- Retrieval rows are retained on archive; active-document predicates remove them
  immediately from normal search.

## Alternatives considered

- Elasticsearch/OpenSearch: rejected as unnecessary operational scope and a
  second authorization/index consistency system.
- Raw lexical/vector score addition: rejected because the scales are unrelated.
- Rank globally then filter: rejected because unauthorized chunks would become
  candidates and could leak through ranks, timing, or implementation mistakes.
- In-memory indexing queue: rejected because work would be lost on restart.
- Automatically enabled commercial embeddings: rejected because it silently
  crosses an external data boundary and makes CI depend on secrets/network access.
