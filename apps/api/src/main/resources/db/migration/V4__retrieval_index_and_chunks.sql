ALTER TABLE document_versions
    ADD CONSTRAINT document_versions_id_document_tenant_unique
    UNIQUE (id, document_id, organization_id, workspace_id);

ALTER TABLE document_text_units
    ADD CONSTRAINT document_text_units_id_version_tenant_unique
    UNIQUE (id, document_version_id, organization_id, workspace_id);

CREATE TABLE retrieval_indexes (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    generation INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    chunker_name VARCHAR(120) NOT NULL,
    chunker_version VARCHAR(64) NOT NULL,
    chunk_size INTEGER NOT NULL,
    chunk_overlap INTEGER NOT NULL,
    embedding_provider VARCHAR(120) NULL,
    embedding_model VARCHAR(160) NULL,
    embedding_dimension INTEGER NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ready_at TIMESTAMPTZ NULL,
    superseded_at TIMESTAMPTZ NULL,
    CONSTRAINT retrieval_indexes_version_tenant_fk
        FOREIGN KEY (document_version_id, document_id, organization_id, workspace_id)
        REFERENCES document_versions (id, document_id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT retrieval_indexes_id_tenant_unique
        UNIQUE (id, document_version_id, organization_id, workspace_id),
    CONSTRAINT retrieval_indexes_generation_unique
        UNIQUE (document_version_id, generation),
    CONSTRAINT retrieval_indexes_generation_positive CHECK (generation > 0),
    CONSTRAINT retrieval_indexes_chunk_size_positive CHECK (chunk_size > 0),
    CONSTRAINT retrieval_indexes_overlap_valid
        CHECK (chunk_overlap >= 0 AND chunk_overlap < chunk_size),
    CONSTRAINT retrieval_indexes_status_allowed
        CHECK (status IN ('QUEUED', 'PROCESSING', 'READY', 'FAILED', 'SUPERSEDED')),
    CONSTRAINT retrieval_indexes_embedding_consistent CHECK (
        (embedding_provider IS NULL AND embedding_model IS NULL AND embedding_dimension IS NULL)
        OR (
            embedding_provider IS NOT NULL
            AND embedding_model IS NOT NULL
            AND embedding_dimension IS NOT NULL
            AND
            btrim(embedding_provider) <> ''
            AND btrim(embedding_model) <> ''
            AND embedding_dimension > 0
        )
    ),
    CONSTRAINT retrieval_indexes_ready_state_consistent CHECK (
        (status = 'READY' AND ready_at IS NOT NULL AND failure_code IS NULL AND failure_message IS NULL)
        OR (status <> 'READY' AND ready_at IS NULL)
    ),
    CONSTRAINT retrieval_indexes_failure_state_consistent CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL AND failure_message IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL AND failure_message IS NULL)
    ),
    CONSTRAINT retrieval_indexes_superseded_state_consistent CHECK (
        (status = 'SUPERSEDED' AND superseded_at IS NOT NULL)
        OR (status <> 'SUPERSEDED' AND superseded_at IS NULL)
    )
);

CREATE INDEX retrieval_indexes_version_status_idx
    ON retrieval_indexes (document_version_id, status, generation DESC);
CREATE INDEX retrieval_indexes_document_ready_idx
    ON retrieval_indexes (organization_id, workspace_id, document_id, ready_at DESC)
    WHERE status = 'READY';

CREATE TABLE retrieval_index_jobs (
    id UUID PRIMARY KEY,
    retrieval_index_id UUID NOT NULL UNIQUE,
    document_version_id UUID NOT NULL,
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
    CONSTRAINT retrieval_index_jobs_index_tenant_fk
        FOREIGN KEY (retrieval_index_id, document_version_id, organization_id, workspace_id)
        REFERENCES retrieval_indexes (id, document_version_id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT retrieval_index_jobs_status_allowed
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT retrieval_index_jobs_attempts_bounded
        CHECK (attempt_count >= 0 AND attempt_count <= 3),
    CONSTRAINT retrieval_index_jobs_claim_consistent CHECK (
        (status = 'PROCESSING' AND claimed_at IS NOT NULL AND completed_at IS NULL)
        OR (status <> 'PROCESSING')
    ),
    CONSTRAINT retrieval_index_jobs_completion_consistent CHECK (
        (status IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
        OR (status IN ('QUEUED', 'PROCESSING') AND completed_at IS NULL)
    )
);

CREATE INDEX retrieval_index_jobs_polling_idx
    ON retrieval_index_jobs (next_attempt_at, created_at)
    WHERE status = 'QUEUED';
CREATE INDEX retrieval_index_jobs_stale_claim_idx
    ON retrieval_index_jobs (claimed_at)
    WHERE status = 'PROCESSING';

CREATE TABLE retrieval_chunks (
    id UUID PRIMARY KEY,
    retrieval_index_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    source_text_unit_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,
    locator_type VARCHAR(32) NOT NULL,
    locator_value VARCHAR(120) NOT NULL,
    start_character INTEGER NOT NULL,
    end_character INTEGER NOT NULL,
    text_content TEXT NOT NULL,
    character_count INTEGER NOT NULL,
    lexical_document TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple'::regconfig, text_content)
    ) STORED,
    embedding VECTOR NULL,
    embedding_provider VARCHAR(120) NULL,
    embedding_model VARCHAR(160) NULL,
    embedding_dimension INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT retrieval_chunks_index_tenant_fk
        FOREIGN KEY (retrieval_index_id, document_version_id, organization_id, workspace_id)
        REFERENCES retrieval_indexes (id, document_version_id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT retrieval_chunks_version_tenant_fk
        FOREIGN KEY (document_version_id, document_id, organization_id, workspace_id)
        REFERENCES document_versions (id, document_id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT retrieval_chunks_text_unit_tenant_fk
        FOREIGN KEY (source_text_unit_id, document_version_id, organization_id, workspace_id)
        REFERENCES document_text_units (id, document_version_id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT retrieval_chunks_order_unique UNIQUE (retrieval_index_id, ordinal),
    CONSTRAINT retrieval_chunks_source_range_unique
        UNIQUE (retrieval_index_id, source_text_unit_id, start_character, end_character),
    CONSTRAINT retrieval_chunks_ordinal_positive CHECK (ordinal > 0),
    CONSTRAINT retrieval_chunks_locator_allowed CHECK (locator_type IN ('PAGE', 'DOCUMENT')),
    CONSTRAINT retrieval_chunks_locator_not_blank CHECK (btrim(locator_value) <> ''),
    CONSTRAINT retrieval_chunks_offsets_valid
        CHECK (start_character >= 0 AND end_character > start_character),
    CONSTRAINT retrieval_chunks_text_not_blank CHECK (btrim(text_content) <> ''),
    CONSTRAINT retrieval_chunks_character_count_valid CHECK (
        character_count = end_character - start_character
    ),
    CONSTRAINT retrieval_chunks_embedding_consistent CHECK (
        (embedding IS NULL AND embedding_provider IS NULL AND embedding_model IS NULL
            AND embedding_dimension IS NULL)
        OR (
            embedding IS NOT NULL
            AND embedding_provider IS NOT NULL
            AND embedding_model IS NOT NULL
            AND embedding_dimension IS NOT NULL
            AND btrim(embedding_provider) <> ''
            AND btrim(embedding_model) <> ''
            AND embedding_dimension > 0
            AND vector_dims(embedding) = embedding_dimension
        )
    )
);

CREATE INDEX retrieval_chunks_lexical_idx
    ON retrieval_chunks USING GIN (lexical_document);
CREATE INDEX retrieval_chunks_tenant_index_idx
    ON retrieval_chunks (organization_id, workspace_id, retrieval_index_id, ordinal);
CREATE INDEX retrieval_chunks_version_idx
    ON retrieval_chunks (document_version_id, retrieval_index_id);
