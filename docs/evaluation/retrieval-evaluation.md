# Retrieval Evaluation

## Purpose

Day 4 includes a deterministic synthetic benchmark so relevance changes are
reviewed as testable behavior rather than judged only through screenshots. It is
a regression suite for this repository's controlled fixtures, not a scientific
claim about production corpora or any commercial embedding model.

## Dataset

`tests/fixtures/retrieval/golden-retrieval.jsonl` contains eight project-owned
synthetic policies and queries. Stable fixture keys replace runtime UUIDs, and
unique readable markers make every expected rank auditable. Cross-tenant
near-duplicates, page provenance, archive exclusion, and last-known-good cutover
are covered by dedicated integration/composed cases rather than folded into the
metric corpus.

## Metrics

For `LEXICAL`, `VECTOR`, and `HYBRID`, the evaluator computes:

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

CI fails when any mode falls below its threshold and publishes the JSON as the
`retrieval-evaluation` workflow artifact. Generated reports are build output and
are not committed. The committed `day4-v1` corpus currently scores Recall@1/3/5
and MRR of `1.00` in all three modes.

## Interpretation and limitations

- Scores apply only to the committed synthetic questions and relevance labels.
- The `simple` PostgreSQL text configuration has limited stemming and CJK word
  segmentation.
- The deterministic smoke embedding shares hashed tokens; it is designed for
  stable plumbing/ranking assertions, not language understanding.
- Real provider/model adoption requires a separately governed representative
  dataset, privacy review, offline evaluation, and a new immutable index generation.
