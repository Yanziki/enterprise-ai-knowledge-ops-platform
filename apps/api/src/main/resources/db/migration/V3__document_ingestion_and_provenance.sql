CREATE TABLE documents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMPTZ NULL,
    CONSTRAINT documents_workspace_organization_fk
        FOREIGN KEY (workspace_id, organization_id)
        REFERENCES workspaces (id, organization_id)
        ON DELETE RESTRICT,
    CONSTRAINT documents_id_tenant_unique UNIQUE (id, organization_id, workspace_id),
    CONSTRAINT documents_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT documents_creator_not_blank CHECK (btrim(created_by_subject) <> ''),
    CONSTRAINT documents_status_allowed CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT documents_archive_state_consistent CHECK (
        (status = 'ACTIVE' AND archived_at IS NULL)
        OR (status = 'ARCHIVED' AND archived_at IS NOT NULL)
    )
);

CREATE INDEX documents_workspace_listing_idx
    ON documents (organization_id, workspace_id, status, created_at DESC);

CREATE TABLE document_versions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    declared_content_type VARCHAR(160) NOT NULL,
    detected_content_type VARCHAR(160) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    parser_name VARCHAR(120) NULL,
    parser_version VARCHAR(64) NULL,
    ingestion_status VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ready_at TIMESTAMPTZ NULL,
    CONSTRAINT document_versions_document_tenant_fk
        FOREIGN KEY (document_id, organization_id, workspace_id)
        REFERENCES documents (id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT document_versions_id_tenant_unique
        UNIQUE (id, organization_id, workspace_id),
    CONSTRAINT document_versions_number_unique UNIQUE (document_id, version_number),
    CONSTRAINT document_versions_number_positive CHECK (version_number > 0),
    CONSTRAINT document_versions_filename_not_blank CHECK (btrim(original_filename) <> ''),
    CONSTRAINT document_versions_declared_type_not_blank
        CHECK (btrim(declared_content_type) <> ''),
    CONSTRAINT document_versions_detected_type_not_blank
        CHECK (btrim(detected_content_type) <> ''),
    CONSTRAINT document_versions_size_positive CHECK (byte_size > 0),
    CONSTRAINT document_versions_sha256_format CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    CONSTRAINT document_versions_object_key_not_blank CHECK (btrim(object_key) <> ''),
    CONSTRAINT document_versions_creator_not_blank CHECK (btrim(created_by_subject) <> ''),
    CONSTRAINT document_versions_status_allowed CHECK (
        ingestion_status IN ('STORED', 'QUEUED', 'PROCESSING', 'READY', 'FAILED')
    ),
    CONSTRAINT document_versions_ready_state_consistent CHECK (
        (ingestion_status = 'READY' AND ready_at IS NOT NULL)
        OR (ingestion_status <> 'READY' AND ready_at IS NULL)
    ),
    CONSTRAINT document_versions_failure_state_consistent CHECK (
        (ingestion_status = 'FAILED' AND failure_code IS NOT NULL AND failure_message IS NOT NULL)
        OR (ingestion_status <> 'FAILED' AND failure_code IS NULL AND failure_message IS NULL)
    )
);

CREATE INDEX document_versions_document_order_idx
    ON document_versions (document_id, version_number DESC);
CREATE INDEX document_versions_tenant_status_idx
    ON document_versions (organization_id, workspace_id, ingestion_status, created_at DESC);

CREATE TABLE document_ingestion_jobs (
    id UUID PRIMARY KEY,
    document_version_id UUID NOT NULL UNIQUE,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT document_ingestion_jobs_version_tenant_fk
        FOREIGN KEY (document_version_id, organization_id, workspace_id)
        REFERENCES document_versions (id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT document_ingestion_jobs_status_allowed
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT document_ingestion_jobs_attempts_bounded
        CHECK (attempt_count >= 0 AND attempt_count <= 3),
    CONSTRAINT document_ingestion_jobs_claim_state_consistent CHECK (
        (status = 'PROCESSING' AND claimed_at IS NOT NULL AND completed_at IS NULL)
        OR (status <> 'PROCESSING')
    ),
    CONSTRAINT document_ingestion_jobs_completion_state_consistent CHECK (
        (status IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
        OR (status IN ('QUEUED', 'PROCESSING') AND completed_at IS NULL)
    )
);

CREATE INDEX document_ingestion_jobs_polling_idx
    ON document_ingestion_jobs (next_attempt_at, created_at)
    WHERE status = 'QUEUED';
CREATE INDEX document_ingestion_jobs_stale_claim_idx
    ON document_ingestion_jobs (claimed_at)
    WHERE status = 'PROCESSING';

CREATE TABLE document_text_units (
    id UUID PRIMARY KEY,
    document_version_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,
    locator_type VARCHAR(32) NOT NULL,
    locator_value VARCHAR(120) NOT NULL,
    text_content TEXT NOT NULL,
    character_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT document_text_units_version_tenant_fk
        FOREIGN KEY (document_version_id, organization_id, workspace_id)
        REFERENCES document_versions (id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT document_text_units_order_unique
        UNIQUE (document_version_id, ordinal),
    CONSTRAINT document_text_units_ordinal_positive CHECK (ordinal > 0),
    CONSTRAINT document_text_units_locator_allowed
        CHECK (locator_type IN ('PAGE', 'DOCUMENT')),
    CONSTRAINT document_text_units_locator_not_blank CHECK (btrim(locator_value) <> ''),
    CONSTRAINT document_text_units_text_not_blank CHECK (btrim(text_content) <> ''),
    CONSTRAINT document_text_units_character_count_positive CHECK (character_count > 0),
    CONSTRAINT document_text_units_character_count_matches
        CHECK (character_count = char_length(text_content))
);

CREATE INDEX document_text_units_version_order_idx
    ON document_text_units (document_version_id, ordinal);
