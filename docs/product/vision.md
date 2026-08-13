# Product Vision

## Target users

- Knowledge workers who need reliable answers from approved enterprise sources.
- Operations specialists who need repeatable, supervised workflows.
- Security, compliance, and audit teams that require evidence and traceability.
- Platform engineers who operate governed AI capabilities across tenants.

## Business problem

Enterprise knowledge is fragmented, access-sensitive, and difficult to turn into
safe action. Generic assistants can produce unsupported answers, leak context,
or trigger mutations without adequate oversight. The platform will make source
provenance, authorization, human approval, evaluation, and audit evidence part
of the core design.

## Core use cases

1. Ingest and govern approved knowledge with provenance and tenant boundaries.
2. Answer questions with citations that can be inspected and evaluated.
3. Prepare operational actions while requiring human approval before writes.
4. Expose independently governed tools through an MCP server.
5. Produce auditable records of access, reasoning inputs, approvals, and effects.

Grounded single-turn cited answers are implemented in Day 5; operational actions,
independent tools, and MCP remain roadmap intent.

## Non-goals

- Replacing human accountability for consequential decisions.
- Training foundation models or accepting ungoverned public data.
- Autonomous write operations without explicit approval and authorization.
- Claiming correctness from fluent output alone.
- Supporting every storage system or model provider in the first releases.

## Success metrics

- Citation precision and recall meet documented evaluation thresholds.
- Tenant-isolation tests produce zero cross-tenant disclosures.
- All write operations have attributable approvals and audit records.
- Critical services meet defined availability and recovery objectives.
- Developers can reproduce validation and local startup from a clean checkout.
- Security findings and dependency updates are resolved within policy windows.

## Synthetic-data policy

Development, tests, demonstrations, screenshots, and automated evaluation use
synthetic or explicitly licensed public data only. Synthetic fixtures must be
clearly labeled and must not imitate identifiable individuals. Customer data,
credentials, internal documents, and personal data are prohibited in Git,
issues, CI logs, and public demonstrations.
