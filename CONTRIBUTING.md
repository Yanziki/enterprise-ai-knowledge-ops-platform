# Contributing

## Issue-first workflow

Create or select an issue before implementation. Capture desired behavior,
security and architecture effects, acceptance criteria, and explicit non-goals.

## Branches

Branch from current `main` using one of:

- `feat/<issue>-<description>`
- `fix/<issue>-<description>`
- `docs/<issue>-<description>`
- `chore/<issue>-<description>`

Never commit feature work directly to `main`.

## Commits

Use focused [Conventional Commit](https://www.conventionalcommits.org/) messages,
for example `feat(api): add system status endpoint`. Keep generated artifacts,
secrets, personal data, and unrelated changes out of commits.

## Pull requests

Open a draft pull request early. Complete the repository template, link the
issue with `Closes #<number>`, include reproducible evidence, and keep the branch
up to date. Do not merge until required checks pass and conversations resolve.

## Required verification

Run `make verify`. Changes affecting containers must also pass
`docker compose up --build` and targeted health checks. Never weaken a test to
make CI pass; fix the behavior or document a genuine infrastructure blocker.

## Definition of done

- Acceptance criteria and non-goals are respected.
- Tests cover meaningful success and failure behavior.
- Security and architecture effects are reviewed.
- Documentation matches commands and observable behavior.
- Dependency versions and lockfiles are reproducible.
- No credentials, private data, or synthetic data presented as real data exists.
- CI passes and the pull request remains reviewable and reversible.
