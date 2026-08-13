# Grounded RAG threat model

## Trust boundaries

Authenticated users, stored documents, the application, PostgreSQL, and a remote
language-model provider are distinct trust zones. Retrieved document content is
always untrusted data, even when uploaded by an authorized administrator.

| Threat | Mitigation | Residual risk |
| --- | --- | --- |
| Cross-tenant evidence leakage | Authorize first; reuse tenant-filtered Day 4 retrieval; no client context | Defects in shared authorization/retrieval remain critical |
| Archived/stale evidence | Existing ACTIVE/READY/current-index SQL boundary | Operational indexing delays still affect freshness |
| Document prompt injection | System policy separates policy/question/evidence; JSON serialization; bounded content; server citation authority | A model may still follow malicious text or produce a misleading answer |
| Citation spoofing | Request-local aliases; reject unknown, malformed, duplicate aliases; reconstruct provenance server-side | Valid citations do not prove every generated sentence is supported |
| Malformed/provider-controlled output | Strict DTO parsing, unknown-field failure, size limits, safe 502/503 errors | Provider outages still reduce availability |
| Secret disclosure | API key is environment-only; client cannot select URL/model/key; safe errors/logs | Provider or host compromise can expose configured secrets/data |
| Resource exhaustion | Bounded question, topK, chunks, per-chunk/context/answer/output tokens, timeout | Concurrent-request admission control is future hardening |
| External data disclosure | Default `none`; explicit remote configuration and documented approval boundary | Approved remote provider receives bounded enterprise text |

## Prompt injection

Evidence strings can say “ignore policy,” forge delimiters, request secrets, or
suggest `C999`. JSON encoding prevents content from becoming structural evidence,
and the model sees an explicit instruction that evidence string values have no
authority. The server still validates aliases and owns provenance. These controls
are defense in depth, not complete prompt-injection prevention; generated content
must remain advisory and citations should be inspected for consequential use.

## Guarantees and non-guarantees

The service guarantees authorization is checked before retrieval, only authorized
Day 4 results enter context, bounds are enforced, and returned provenance maps to
valid request-local evidence. It does not guarantee the answer is complete, true,
free of unsupported implications, or immune to model/provider behavior. Safe
abstention reduces but cannot eliminate hallucination risk.
