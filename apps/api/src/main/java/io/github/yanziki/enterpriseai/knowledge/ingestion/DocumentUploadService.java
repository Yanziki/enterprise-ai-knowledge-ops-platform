package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.api.KnowledgeApiException;
import io.github.yanziki.enterpriseai.knowledge.storage.DocumentObjectKeyFactory;
import io.github.yanziki.enterpriseai.knowledge.storage.ObjectStorage;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAccessContext;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAuthorizationService;
import io.github.yanziki.enterpriseai.tenant.WorkspaceOperation;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentUploadService.class);

    private final WorkspaceAuthorizationService authorizationService;
    private final UploadValidationService validationService;
    private final DocumentObjectKeyFactory objectKeyFactory;
    private final ObjectStorage objectStorage;
    private final DocumentCommandPersistence commandPersistence;

    public DocumentUploadService(
            WorkspaceAuthorizationService authorizationService,
            UploadValidationService validationService,
            DocumentObjectKeyFactory objectKeyFactory,
            ObjectStorage objectStorage,
            DocumentCommandPersistence commandPersistence) {
        this.authorizationService = authorizationService;
        this.validationService = validationService;
        this.objectKeyFactory = objectKeyFactory;
        this.objectStorage = objectStorage;
        this.commandPersistence = commandPersistence;
    }

    public PersistedUpload createDocument(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            MultipartFile file,
            String requestedTitle) {
        WorkspaceAccessContext context =
                authorizationService.requireAccess(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.UPLOAD_DOCUMENT);
        try (ValidatedUpload upload = validationService.stageAndValidate(file)) {
            UUID documentId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            String objectKey =
                    objectKeyFactory.create(
                            context.organizationId(), context.workspaceId(), documentId, versionId);
            store(upload, objectKey);
            try {
                PersistedUpload persisted =
                        commandPersistence.createDocument(
                                context,
                                documentId,
                                versionId,
                                title(requestedTitle, upload.originalFilename()),
                                upload,
                                objectKey,
                                Instant.now());
                logAccepted(context, persisted);
                return persisted;
            } catch (RuntimeException exception) {
                deleteAfterRollback(objectKey, exception);
                throw exception;
            }
        } catch (KnowledgeApiException exception) {
            throw exception;
        }
    }

    public PersistedUpload createVersion(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID documentId,
            MultipartFile file) {
        WorkspaceAccessContext context =
                authorizationService.requireAccess(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.CREATE_VERSION);
        try (ValidatedUpload upload = validationService.stageAndValidate(file)) {
            UUID versionId = UUID.randomUUID();
            String objectKey =
                    objectKeyFactory.create(
                            context.organizationId(), context.workspaceId(), documentId, versionId);
            store(upload, objectKey);
            try {
                PersistedUpload persisted =
                        commandPersistence.createVersion(
                                context, documentId, versionId, upload, objectKey, Instant.now());
                logAccepted(context, persisted);
                return persisted;
            } catch (RuntimeException exception) {
                deleteAfterRollback(objectKey, exception);
                throw exception;
            }
        } catch (KnowledgeApiException exception) {
            throw exception;
        }
    }

    private void store(ValidatedUpload upload, String objectKey) {
        objectStorage.put(
                objectKey,
                upload.staged().path(),
                upload.staged().byteSize(),
                upload.detectedContentType());
    }

    private String title(String requestedTitle, String filename) {
        String candidate =
                requestedTitle == null || requestedTitle.isBlank() ? filename : requestedTitle;
        String sanitized = candidate.strip();
        if (sanitized.length() > 255 || sanitized.chars().anyMatch(Character::isISOControl)) {
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_TITLE", "Document title is invalid");
        }
        return sanitized;
    }

    private void deleteAfterRollback(String objectKey, RuntimeException original) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
            LOGGER.error("orphaned_document_object_cleanup_failed", cleanupFailure);
        }
    }

    private void logAccepted(WorkspaceAccessContext context, PersistedUpload upload) {
        LOGGER.info(
                "ingestion_accepted organizationId={} workspaceId={} documentId={} versionId={} status={}",
                context.organizationId(),
                context.workspaceId(),
                upload.document().getId(),
                upload.version().getId(),
                upload.version().getIngestionStatus());
    }
}
