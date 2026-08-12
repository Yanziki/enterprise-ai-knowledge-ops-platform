# Retrieval Evaluation

## Purpose

Day 4 includes a deterministic synthetic benchmark so relevance changes are
reviewed as testable behavior rather than judged only through screenshots. It is
a regression suite for this repository's controlled fixtures, not a scientific
claim about production corpora or any commercial embedding model.

## Dataset

`tests/fixtures/retrieval/golden-retrieval.jsonl` uses stable fixture keys rather
than runtime UUIDs. Repository-owned Acme and Globex documents cover exact terms,
paraphrases, page-specific facts, distractors, a cross-tenant near-duplicate,
archive exclusion, and last-known-good version cutover. Each judgment identifies
the logical fixture key plus expected locator.

## Metrics

For every supported mode the evaluator computes:

- Recall@1, Recall@3, and Recall@5: whether at least one expected citation appears
  within the first K results, averaged over queries;
- Mean Reciprocal Rank (MRR): the average reciprocal rank of the first expected
  citation.

Lexical thresholds are fixed before implementation at Recall@1 >= 0.75,
Recall@3 >= 1.00, Recall@5 >= 1.00, and MRR >= 0.85 for the intentionally direct
synthetic set. When the deterministic smoke provider is enabled, vector and
hybrid use the same minimums. The smoke provider is hashed test plumbing and the
metrics do not predict real semantic model quality.

## Running

The backend integration suite constructs and evaluates the synthetic corpus and
writes the machine-readable build artifact to:

`apps/api/target/retrieval-evaluation.json`

The composed stack exposes the same deterministic evaluation through the
repository verifier:

```bash
make retrieval-verify
```

CI fails when a supported mode falls below its threshold. Generated reports are
build output and are not committed.

## Interpretation and limitations

- Scores apply only to the committed synthetic questions and relevance labels.
- The `simple` PostgreSQL text configuration has limited stemming and CJK word
  segmentation.
- The deterministic smoke embedding shares hashed tokens; it is designed for
  stable plumbing/ranking assertions, not language understanding.
- Real provider/model adoption requires a separately governed representative
  dataset, privacy review, offline evaluation, and a new immutable index generation.
