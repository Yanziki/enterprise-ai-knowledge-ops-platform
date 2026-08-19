ALTER TABLE retrieval_chunks
    ADD CONSTRAINT retrieval_chunks_id_source_tenant_unique
    UNIQUE (id, document_id, document_version_id, organization_id, workspace_id);

CREATE TABLE answer_attempts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    answer_text TEXT NOT NULL,
    requested_retrieval_mode VARCHAR(32) NOT NULL,
    effective_retrieval_mode VARCHAR(32) NOT NULL,
    retrieved_chunk_count INTEGER NOT NULL,
    context_characters INTEGER NOT NULL,
    provider_id VARCHAR(120) NOT NULL,
    model_id VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT answer_attempts_workspace_tenant_fk
        FOREIGN KEY (workspace_id, organization_id)
        REFERENCES workspaces (id, organization_id)
        ON DELETE RESTRICT,
    CONSTRAINT answer_attempts_creator_fk
        FOREIGN KEY (created_by_subject)
        REFERENCES user_profiles (identity_subject)
        ON DELETE RESTRICT,
    CONSTRAINT answer_attempts_id_tenant_unique
        UNIQUE (id, organization_id, workspace_id),
    CONSTRAINT answer_attempts_question_not_blank CHECK (btrim(question) <> ''),
    CONSTRAINT answer_attempts_question_bounded CHECK (char_length(question) <= 2000),
    CONSTRAINT answer_attempts_status_allowed
        CHECK (status IN ('ANSWERED', 'INSUFFICIENT_EVIDENCE')),
    CONSTRAINT answer_attempts_answer_not_blank CHECK (btrim(answer_text) <> ''),
    CONSTRAINT answer_attempts_answer_bounded CHECK (char_length(answer_text) <= 4000),
    CONSTRAINT answer_attempts_requested_mode_allowed
        CHECK (requested_retrieval_mode IN ('AUTO', 'LEXICAL', 'VECTOR', 'HYBRID')),
    CONSTRAINT answer_attempts_effective_mode_allowed
        CHECK (effective_retrieval_mode IN ('LEXICAL', 'VECTOR', 'HYBRID')),
    CONSTRAINT answer_attempts_counts_bounded CHECK (
        retrieved_chunk_count >= 0
        AND retrieved_chunk_count <= 8
        AND context_characters >= 0
        AND context_characters <= 8000
    ),
    CONSTRAINT answer_attempts_provider_not_blank CHECK (btrim(provider_id) <> ''),
    CONSTRAINT answer_attempts_model_not_blank CHECK (btrim(model_id) <> '')
);

CREATE INDEX answer_attempts_workspace_creator_created_idx
    ON answer_attempts (organization_id, workspace_id, created_by_subject, created_at DESC);

CREATE TABLE answer_attempt_evidence (
    id UUID PRIMARY KEY,
    answer_attempt_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    citation_alias VARCHAR(24) NOT NULL,
    rank INTEGER NOT NULL,
    cited BOOLEAN NOT NULL,
    retrieval_chunk_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    document_title VARCHAR(255) NOT NULL,
    locator_type VARCHAR(32) NOT NULL,
    locator_value VARCHAR(120) NOT NULL,
    start_character INTEGER NOT NULL,
    end_character INTEGER NOT NULL,
    excerpt TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT answer_attempt_evidence_attempt_tenant_fk
        FOREIGN KEY (answer_attempt_id, organization_id, workspace_id)
        REFERENCES answer_attempts (id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT answer_attempt_evidence_source_tenant_fk
        FOREIGN KEY (
            retrieval_chunk_id,
            document_id,
            document_version_id,
            organization_id,
            workspace_id
        ) REFERENCES retrieval_chunks (
            id,
            document_id,
            document_version_id,
            organization_id,
            workspace_id
        ) ON DELETE RESTRICT,
    CONSTRAINT answer_attempt_evidence_alias_unique
        UNIQUE (answer_attempt_id, citation_alias),
    CONSTRAINT answer_attempt_evidence_rank_unique
        UNIQUE (answer_attempt_id, rank),
    CONSTRAINT answer_attempt_evidence_alias_format
        CHECK (citation_alias ~ '^C[1-9][0-9]*$'),
    CONSTRAINT answer_attempt_evidence_rank_positive CHECK (rank > 0),
    CONSTRAINT answer_attempt_evidence_version_positive CHECK (version_number > 0),
    CONSTRAINT answer_attempt_evidence_title_not_blank CHECK (btrim(document_title) <> ''),
    CONSTRAINT answer_attempt_evidence_locator_allowed
        CHECK (locator_type IN ('PAGE', 'DOCUMENT')),
    CONSTRAINT answer_attempt_evidence_locator_not_blank CHECK (btrim(locator_value) <> ''),
    CONSTRAINT answer_attempt_evidence_offsets_valid
        CHECK (start_character >= 0 AND end_character > start_character),
    CONSTRAINT answer_attempt_evidence_excerpt_not_blank CHECK (btrim(excerpt) <> ''),
    CONSTRAINT answer_attempt_evidence_excerpt_bounded CHECK (char_length(excerpt) <= 600)
);

CREATE INDEX answer_attempt_evidence_attempt_rank_idx
    ON answer_attempt_evidence (answer_attempt_id, rank);
CREATE INDEX answer_attempt_evidence_source_idx
    ON answer_attempt_evidence (document_id, document_version_id, retrieval_chunk_id);

CREATE TABLE review_cases (
    id UUID PRIMARY KEY,
    answer_attempt_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    assigned_to_subject VARCHAR(255) NULL,
    reason VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_note VARCHAR(1000) NULL,
    resolution VARCHAR(64) NULL,
    reviewer_note VARCHAR(2000) NULL,
    resolved_by_subject VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT review_cases_answer_tenant_fk
        FOREIGN KEY (answer_attempt_id, organization_id, workspace_id)
        REFERENCES answer_attempts (id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT review_cases_creator_fk
        FOREIGN KEY (created_by_subject)
        REFERENCES user_profiles (identity_subject)
        ON DELETE RESTRICT,
    CONSTRAINT review_cases_assignee_fk
        FOREIGN KEY (assigned_to_subject)
        REFERENCES user_profiles (identity_subject)
        ON DELETE RESTRICT,
    CONSTRAINT review_cases_resolver_fk
        FOREIGN KEY (resolved_by_subject)
        REFERENCES user_profiles (identity_subject)
        ON DELETE RESTRICT,
    CONSTRAINT review_cases_id_tenant_unique
        UNIQUE (id, organization_id, workspace_id),
    CONSTRAINT review_cases_reason_allowed CHECK (
        reason IN ('INSUFFICIENT_EVIDENCE', 'USER_ESCALATION', 'REVIEW_REQUESTED')
    ),
    CONSTRAINT review_cases_status_allowed
        CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT review_cases_resolution_allowed CHECK (
        resolution IS NULL OR resolution IN (
            'EVIDENCE_CONFIRMED',
            'KNOWLEDGE_GAP',
            'DOCUMENT_UPDATE_REQUIRED',
            'QUESTION_OUT_OF_SCOPE',
            'OTHER'
        )
    ),
    CONSTRAINT review_cases_request_note_not_blank
        CHECK (request_note IS NULL OR btrim(request_note) <> ''),
    CONSTRAINT review_cases_reviewer_note_not_blank
        CHECK (reviewer_note IS NULL OR btrim(reviewer_note) <> ''),
    CONSTRAINT review_cases_state_consistent CHECK (
        (status = 'OPEN'
            AND assigned_to_subject IS NULL
            AND resolution IS NULL
            AND resolved_by_subject IS NULL
            AND resolved_at IS NULL)
        OR (status = 'IN_REVIEW'
            AND assigned_to_subject IS NOT NULL
            AND resolution IS NULL
            AND resolved_by_subject IS NULL
            AND resolved_at IS NULL)
        OR (status = 'RESOLVED'
            AND assigned_to_subject IS NOT NULL
            AND resolution IS NOT NULL
            AND resolved_by_subject = assigned_to_subject
            AND resolved_at IS NOT NULL)
        OR (status = 'DISMISSED'
            AND resolution IS NULL
            AND resolved_by_subject IS NOT NULL
            AND resolved_at IS NOT NULL
            AND (assigned_to_subject IS NULL OR resolved_by_subject = assigned_to_subject))
    )
);

CREATE UNIQUE INDEX review_cases_one_active_per_answer_idx
    ON review_cases (answer_attempt_id)
    WHERE status IN ('OPEN', 'IN_REVIEW');
CREATE INDEX review_cases_workspace_status_created_idx
    ON review_cases (organization_id, workspace_id, status, created_at DESC);
CREATE INDEX review_cases_workspace_assignee_created_idx
    ON review_cases (organization_id, workspace_id, assigned_to_subject, created_at DESC);
CREATE INDEX review_cases_creator_created_idx
    ON review_cases (organization_id, workspace_id, created_by_subject, created_at DESC);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT audit_events_review_case_tenant_fk
        FOREIGN KEY (aggregate_id, organization_id, workspace_id)
        REFERENCES review_cases (id, organization_id, workspace_id)
        ON DELETE RESTRICT,
    CONSTRAINT audit_events_actor_fk
        FOREIGN KEY (actor_subject)
        REFERENCES user_profiles (identity_subject)
        ON DELETE RESTRICT,
    CONSTRAINT audit_events_aggregate_type_allowed
        CHECK (aggregate_type = 'REVIEW_CASE'),
    CONSTRAINT audit_events_event_type_allowed CHECK (
        event_type IN (
            'REVIEW_CASE_CREATED',
            'REVIEW_CASE_CLAIMED',
            'REVIEW_CASE_RESOLVED',
            'REVIEW_CASE_DISMISSED'
        )
    ),
    CONSTRAINT audit_events_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX audit_events_aggregate_history_idx
    ON audit_events (organization_id, workspace_id, aggregate_type, aggregate_id, occurred_at, id);
CREATE INDEX audit_events_workspace_occurred_idx
    ON audit_events (organization_id, workspace_id, occurred_at DESC);
