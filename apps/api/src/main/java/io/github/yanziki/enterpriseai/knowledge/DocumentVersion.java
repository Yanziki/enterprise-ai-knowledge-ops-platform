package io.github.yanziki.enterpriseai.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
public class DocumentVersion {

    @Id private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "declared_content_type", nullable = false, length = 160)
    private String declaredContentType;

    @Column(name = "detected_content_type", nullable = false, length = 160)
    private String detectedContentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "parser_name", length = 120)
    private String parserName;

    @Column(name = "parser_version", length = 64)
    private String parserVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 32)
    private DocumentIngestionStatus ingestionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 64)
    private IngestionFailureCode failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "created_by_subject", nullable = false, length = 255)
    private String createdBySubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    protected DocumentVersion() {}

    public DocumentVersion(
            UUID id,
            UUID documentId,
            UUID organizationId,
            UUID workspaceId,
            int versionNumber,
            String originalFilename,
            String declaredContentType,
            String detectedContentType,
            long byteSize,
            String sha256Hex,
            String objectKey,
            String createdBySubject,
            Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.organizationId = organizationId;
        this.workspaceId = workspaceId;
        this.versionNumber = versionNumber;
        this.originalFilename = originalFilename;
        this.declaredContentType = declaredContentType;
        this.detectedContentType = detectedContentType;
        this.byteSize = byteSize;
        this.sha256Hex = sha256Hex;
        this.objectKey = objectKey;
        this.ingestionStatus = DocumentIngestionStatus.STORED;
        this.createdBySubject = createdBySubject;
        this.createdAt = createdAt;
    }

    public void queue() {
        requireStatus(DocumentIngestionStatus.STORED, DocumentIngestionStatus.FAILED);
        ingestionStatus = DocumentIngestionStatus.QUEUED;
        failureCode = null;
        failureMessage = null;
        readyAt = null;
    }

    public void startProcessing() {
        requireStatus(DocumentIngestionStatus.QUEUED);
        ingestionStatus = DocumentIngestionStatus.PROCESSING;
    }

    public void reschedule() {
        requireStatus(DocumentIngestionStatus.PROCESSING);
        ingestionStatus = DocumentIngestionStatus.QUEUED;
    }

    public void markReady(String parserName, String parserVersion, Instant readyAt) {
        requireStatus(DocumentIngestionStatus.PROCESSING);
        this.parserName = parserName;
        this.parserVersion = parserVersion;
        this.readyAt = readyAt;
        ingestionStatus = DocumentIngestionStatus.READY;
    }

    public void markFailed(IngestionFailureCode code, String safeMessage) {
        requireStatus(DocumentIngestionStatus.PROCESSING);
        failureCode = code;
        failureMessage = safeMessage;
        readyAt = null;
        ingestionStatus = DocumentIngestionStatus.FAILED;
    }

    private void requireStatus(DocumentIngestionStatus... permitted) {
        for (DocumentIngestionStatus candidate : permitted) {
            if (ingestionStatus == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Invalid document version lifecycle transition");
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getDeclaredContentType() {
        return declaredContentType;
    }

    public String getDetectedContentType() {
        return detectedContentType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getParserName() {
        return parserName;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public DocumentIngestionStatus getIngestionStatus() {
        return ingestionStatus;
    }

    public IngestionFailureCode getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getCreatedBySubject() {
        return createdBySubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }
}
