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
@Table(name = "documents")
public class KnowledgeDocument {

    @Id private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status;

    @Column(name = "created_by_subject", nullable = false, length = 255)
    private String createdBySubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected KnowledgeDocument() {}

    public KnowledgeDocument(
            UUID id,
            UUID organizationId,
            UUID workspaceId,
            String title,
            String createdBySubject,
            Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.workspaceId = workspaceId;
        this.title = title;
        this.status = DocumentStatus.ACTIVE;
        this.createdBySubject = createdBySubject;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void archive(Instant archivedAt) {
        if (status != DocumentStatus.ACTIVE) {
            throw new IllegalStateException("Only an active document can be archived");
        }
        status = DocumentStatus.ARCHIVED;
        this.archivedAt = archivedAt;
        updatedAt = archivedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getCreatedBySubject() {
        return createdBySubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
