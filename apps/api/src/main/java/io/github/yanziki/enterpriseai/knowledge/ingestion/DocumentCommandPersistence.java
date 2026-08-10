package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionJob;
import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionJobRepository;
import io.github.yanziki.enterpriseai.knowledge.DocumentStatus;
import io.github.yanziki.enterpriseai.knowledge.DocumentVersion;
import io.github.yanziki.enterpriseai.knowledge.DocumentVersionRepository;
import io.github.yanziki.enterpriseai.knowledge.KnowledgeDocument;
import io.github.yanziki.enterpriseai.knowledge.KnowledgeDocumentRepository;
import io.github.yanziki.enterpriseai.knowledge.api.KnowledgeApiException;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAccessContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCommandPersistence {

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentIngestionJobRepository jobRepository;

    public DocumentCommandPersistence(
            KnowledgeDocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentIngestionJobRepository jobRepository) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public PersistedUpload createDocument(
            WorkspaceAccessContext context,
            UUID documentId,
            UUID versionId,
            String title,
            ValidatedUpload upload,
            String objectKey,
            Instant createdAt) {
        KnowledgeDocument document =
                new KnowledgeDocument(
                        documentId,
                        context.organizationId(),
                        context.workspaceId(),
                        title,
                        context.authenticatedUser().subject(),
                        createdAt);
        DocumentVersion version =
                createVersionEntity(
                        context, documentId, versionId, 1, upload, objectKey, createdAt);
        documentRepository.save(document);
        version.queue();
        versionRepository.save(version);
        enqueue(context, version, createdAt);
        return new PersistedUpload(document, version);
    }

    @Transactional
    public PersistedUpload createVersion(
            WorkspaceAccessContext context,
            UUID documentId,
            UUID versionId,
            ValidatedUpload upload,
            String objectKey,
            Instant createdAt) {
        KnowledgeDocument document =
                documentRepository
                        .findScopedForUpdate(
                                documentId, context.organizationId(), context.workspaceId())
                        .orElseThrow(DocumentCommandPersistence::notFound);
        if (document.getStatus() != DocumentStatus.ACTIVE) {
            throw new KnowledgeApiException(
                    HttpStatus.CONFLICT,
                    "DOCUMENT_ARCHIVED",
                    "An archived document cannot receive a new version");
        }
        DocumentVersion current =
                versionRepository
                        .findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                        .orElseThrow(DocumentCommandPersistence::notFound);
        if (current.getSha256Hex().equals(upload.staged().sha256Hex())) {
            throw new KnowledgeApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_CURRENT_VERSION",
                    "The current document version already has the same content");
        }
        DocumentVersion version =
                createVersionEntity(
                        context,
                        documentId,
                        versionId,
                        current.getVersionNumber() + 1,
                        upload,
                        objectKey,
                        createdAt);
        version.queue();
        versionRepository.save(version);
        enqueue(context, version, createdAt);
        return new PersistedUpload(document, version);
    }

    private DocumentVersion createVersionEntity(
            WorkspaceAccessContext context,
            UUID documentId,
            UUID versionId,
            int versionNumber,
            ValidatedUpload upload,
            String objectKey,
            Instant createdAt) {
        return new DocumentVersion(
                versionId,
                documentId,
                context.organizationId(),
                context.workspaceId(),
                versionNumber,
                upload.originalFilename(),
                upload.declaredContentType(),
                upload.detectedContentType(),
                upload.staged().byteSize(),
                upload.staged().sha256Hex(),
                objectKey,
                context.authenticatedUser().subject(),
                createdAt);
    }

    private void enqueue(
            WorkspaceAccessContext context, DocumentVersion version, Instant createdAt) {
        jobRepository.save(
                new DocumentIngestionJob(
                        UUID.randomUUID(),
                        version.getId(),
                        context.organizationId(),
                        context.workspaceId(),
                        createdAt));
    }

    private static KnowledgeApiException notFound() {
        return new KnowledgeApiException(
                HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document was not found");
    }
}
