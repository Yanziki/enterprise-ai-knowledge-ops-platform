# Deterministic grounded-answer evaluation

Day 5 commits `tests/fixtures/answer/golden-answer.jsonl` as the project-owned
synthetic evaluation specification. It covers answerable single- and multi-evidence
questions, abstention, distractors, inaccessible tenants, archived sources,
superseded versions, prompt injection, fabricated citations, and malformed output.

CI uses only `deterministic-smoke` and writes
`apps/api/target/answer-evaluation.json`. Gates cover synthetic fact correctness,
citation precision/coverage, abstention accuracy, invalid-citation rejection,
tenant leakage failures, and canonical provenance. A passing deterministic result
proves plumbing and policy behavior only. It is not a benchmark of real model
reasoning, groundedness, safety, or semantic quality.

Any production provider/model change needs a separately approved representative
offline dataset, human review, privacy controls, prompt-injection red teaming,
cost/latency thresholds, and provider-specific regression evidence.
