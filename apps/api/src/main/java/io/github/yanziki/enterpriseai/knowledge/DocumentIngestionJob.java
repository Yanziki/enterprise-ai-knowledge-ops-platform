package io.github.yanziki.enterpriseai.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_ingestion_jobs")
public class DocumentIngestionJob {

    public static final int MAX_ATTEMPTS = 3;

    @Id private UUID id;

    @Column(name = "document_version_id", nullable = false)
    private UUID documentVersionId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IngestionJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_code", length = 64)
    private IngestionFailureCode lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentIngestionJob() {}

    public DocumentIngestionJob(
            UUID id,
            UUID documentVersionId,
            UUID organizationId,
            UUID workspaceId,
            Instant createdAt) {
        this.id = id;
        this.documentVersionId = documentVersionId;
        this.organizationId = organizationId;
        this.workspaceId = workspaceId;
        this.status = IngestionJobStatus.QUEUED;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void claim(Instant claimedAt) {
        if (status != IngestionJobStatus.QUEUED || attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Ingestion job cannot be claimed");
        }
        status = IngestionJobStatus.PROCESSING;
        attemptCount += 1;
        this.claimedAt = claimedAt;
        updatedAt = claimedAt;
    }

    public void complete(Instant completedAt) {
        requireProcessing();
        status = IngestionJobStatus.COMPLETED;
        this.completedAt = completedAt;
        updatedAt = completedAt;
    }

    public void reschedule(
            IngestionFailureCode errorCode, String safeMessage, Instant failedAt, Duration delay) {
        requireProcessing();
        if (attemptCount >= MAX_ATTEMPTS) {
            fail(errorCode, safeMessage, failedAt);
            return;
        }
        status = IngestionJobStatus.QUEUED;
        claimedAt = null;
        nextAttemptAt = failedAt.plus(delay);
        lastErrorCode = errorCode;
        lastErrorMessage = safeMessage;
        updatedAt = failedAt;
    }

    public void fail(IngestionFailureCode errorCode, String safeMessage, Instant completedAt) {
        requireProcessing();
        status = IngestionJobStatus.FAILED;
        lastErrorCode = errorCode;
        lastErrorMessage = safeMessage;
        this.completedAt = completedAt;
        updatedAt = completedAt;
    }

    public void retry(Instant retriedAt) {
        if (status != IngestionJobStatus.FAILED || attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Ingestion job cannot be retried");
        }
        status = IngestionJobStatus.QUEUED;
        claimedAt = null;
        completedAt = null;
        nextAttemptAt = retriedAt;
        updatedAt = retriedAt;
    }

    private void requireProcessing() {
        if (status != IngestionJobStatus.PROCESSING) {
            throw new IllegalStateException("Ingestion job is not processing");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentVersionId() {
        return documentVersionId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public IngestionJobStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public IngestionFailureCode getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}
