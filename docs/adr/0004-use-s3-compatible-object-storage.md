# ADR 0004: Use Private S3-Compatible Object Storage

- Status: Accepted
- Date: 2026-08-09
- Decision owners: Platform engineering

## Context

The knowledge module must retain original document bytes independently from
queryable document metadata. PostgreSQL is the transactional system of record for
identity, authorization, lifecycle, provenance, and ingestion state; using it as a
large binary store would enlarge backups, replication traffic, and transaction
work while coupling binary retention to relational scaling.

The application also needs one storage contract that works with a reproducible
local environment and can later target an approved cloud object store without
changing document-domain authorization or lifecycle logic.

## Decision

Store original binaries in a private S3-compatible bucket through an application
interface named `ObjectStorage`. The production adapter uses AWS SDK for Java v2
S3 protocol APIs. The local Compose environment uses a pinned MinIO-compatible
server and client solely as development infrastructure.

Selected versions for Day 3 are:

| Component | Version |
| --- | --- |
| AWS SDK for Java v2 S3 | 2.46.8 |
| MinIO-compatible server image | `minio/minio:RELEASE.2025-09-07T16-13-09Z` |
| MinIO client/bootstrap image | `minio/mc:RELEASE.2025-08-13T08-35-41Z` |

Application code depends on S3 semantics, not MinIO-specific APIs. Production may
point the adapter at AWS S3 or another compatible provider after validating its
authentication, encryption, consistency, retention, backup, and endpoint rules.

## Private bucket and authorization policy

The bucket has no anonymous or public read policy. Its endpoint and synthetic
credentials are available only to backend and bootstrap containers. The browser
never receives credentials, bucket names, presigned public URLs, or permanent
object URLs. Day 3 downloads stream through an authenticated backend endpoint.

Bucket privacy is defense in depth, not the tenant authorization boundary. Every
read and write first resolves the validated subject, organization, workspace,
membership, operation, and document ownership in the API.

## Object-key strategy

The backend generates every identifier and derives keys in this form:

```text
organizations/{organizationId}/workspaces/{workspaceId}/documents/{documentId}/versions/{versionId}/original
```

All path segments are server-created UUIDs from the authorized context. The raw
filename is stored only as sanitized display metadata. Client-supplied keys and
filenames never participate in storage addressing, which prevents traversal and
tenant-prefix confusion.

## Backup and recovery implications

PostgreSQL metadata and object storage form one logical recovery set. Database
backups preserve provenance and object keys but not original bytes; bucket backups
preserve bytes but not authorization or lifecycle state. Restore procedures must
recover compatible snapshots, verify hashes, detect missing/orphaned objects, and
keep the bucket private before reopening access.

## Migration implications

The S3 adapter keeps object keys provider-neutral. Provider migration requires a
verified copy of every retained object, hash/size comparison, a cutover plan, and
rollback while the existing provider remains readable. Database rows do not change
when endpoint credentials change. A key-layout change requires an explicit data
migration and must never trust rewritten client input.

## Alternatives considered

- **Binary columns in PostgreSQL:** rejected because binary volume and relational
  backup/replication scale together and object streaming becomes transactional DB
  work.
- **MinIO SDK in domain code:** rejected because it couples the knowledge model to
  local infrastructure and narrows production choices.
- **Public or permanent presigned URLs:** rejected for Day 3 because backend
  authorization must be re-evaluated for each download.
- **Filesystem storage:** rejected because container-local paths are not durable,
  horizontally safe, or provider-portable.

## Consequences and limitations

- Upload completion spans object storage and PostgreSQL without a distributed
  transaction. The service compensates failed metadata writes by deleting the
  newly written object where safe and logs orphan cleanup needs without content.
- Object retention during archive preserves provenance and consumes capacity.
- Local fixed credentials and HTTP endpoints are synthetic and unsafe outside a
  developer workstation.
- Server-side encryption, versioned buckets, object lock, lifecycle policies,
  malware scanning, cross-region replication, and production key management are
  later deployment/hardening decisions.
