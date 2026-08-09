package io.github.yanziki.enterpriseai.knowledge.api;

import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionJob;
import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionJobRepository;
import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionStatus;
import io.github.yanziki.enterpriseai.knowledge.DocumentStatus;
import io.github.yanziki.enterpriseai.knowledge.DocumentTextUnitRepository;
import io.github.yanziki.enterpriseai.knowledge.DocumentVersion;
import io.github.yanziki.enterpriseai.knowledge.DocumentVersionRepository;
import io.github.yanziki.enterpriseai.knowledge.IngestionJobStatus;
import io.github.yanziki.enterpriseai.knowledge.KnowledgeDocument;
import io.github.yanziki.enterpriseai.knowledge.KnowledgeDocumentRepository;
import io.github.yanziki.enterpriseai.knowledge.ingestion.DocumentUploadService;
import io.github.yanziki.enterpriseai.knowledge.ingestion.PersistedUpload;
import io.github.yanziki.enterpriseai.knowledge.storage.ObjectStorage;
import io.github.yanziki.enterpriseai.knowledge.storage.StoredObject;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAccessContext;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAuthorizationService;
import io.github.yanziki.enterpriseai.tenant.WorkspaceOperation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentApplicationService {

    private final WorkspaceAuthorizationService authorizationService;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentIngestionJobRepository jobRepository;
    private final DocumentTextUnitRepository textUnitRepository;
    private final DocumentUploadService uploadService;
    private final ObjectStorage objectStorage;

    public DocumentApplicationService(
            WorkspaceAuthorizationService authorizationService,
            KnowledgeDocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentIngestionJobRepository jobRepository,
            DocumentTextUnitRepository textUnitRepository,
            DocumentUploadService uploadService,
            ObjectStorage objectStorage) {
        this.authorizationService = authorizationService;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.jobRepository = jobRepository;
        this.textUnitRepository = textUnitRepository;
        this.uploadService = uploadService;
        this.objectStorage = objectStorage;
    }

    UploadAcceptedResponse createDocument(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            MultipartFile file,
            String title) {
        return accepted(
                uploadService.createDocument(
                        authentication, organizationSlug, workspaceSlug, file, title));
    }

    UploadAcceptedResponse createVersion(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID documentId,
            MultipartFile file) {
        return accepted(
                uploadService.createVersion(
                        authentication, organizationSlug, workspaceSlug, documentId, file));
    }

    @Transactional(readOnly = true)
    DocumentPageResponse list(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            DocumentStatus status,
            int page,
            int size) {
        WorkspaceAccessContext context =
                access(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.READ_METADATA);
        Page<KnowledgeDocument> documents =
                documentRepository.findAllByOrganizationIdAndWorkspaceIdAndStatus(
                        context.organizationId(),
                        context.workspaceId(),
                        status,
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "createdAt")));
        List<DocumentSummaryResponse> content =
                documents.getContent().stream()
                        .map(document -> summary(document, latestVersion(document.getId())))
                        .toList();
        return new DocumentPageResponse(
                content,
                documents.getNumber(),
                documents.getSize(),
                documents.getTotalElements(),
                documents.getTotalPages());
    }

    @Transactional(readOnly = true)
    DocumentDetailResponse detail(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID documentId) {
        WorkspaceAccessContext context =
                access(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.READ_METADATA);
        KnowledgeDocument document = findDocument(context, documentId);
        return detail(
                context,
                document,
                versionRepository.findAllByDocumentIdOrderByVersionNumberDesc(documentId));
    }

    @Transactional(readOnly = true)
    List<DocumentVersionResponse> versions(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID documentId) {
        WorkspaceAccessContext context =
                access(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.READ_METADATA);
        findDocument(context, documentId);
        return versionRepository.findAllByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .map(this::version)
                .toList();
    }

    @Transactional(readOnly = true)
    DocumentVersionResponse version(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID documentId,
            UUID versionId) {
        WorkspaceAccessContext context =
                access(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.READ_METADATA);
        findDocument(context, documentId);
        return version(findVersion(context, documentId, versionId));
    }

    @Transactional(readOnly = true)
    AuthorizedDownload download(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID documentId,
            UUID versionId) {
        WorkspaceAccessContext context =
                access(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.DOWNLOAD_ORIGINAL);
        findDocument(context, documentId);
        DocumentVersion version = findVersion(context, documentId, versionId);
        StoredObject storedObject = objectStorage.get(version.getObjectKey());
        return new AuthorizedDownload(
                storedObject, version.getOriginalFilename(), version.getDetectedContentType());
    }

    @Transactional
    DocumentDetailResponse archive(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID documentId) {
        WorkspaceAccessContext context =
                access(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.ARCHIVE_DOCUMENT);
        KnowledgeDocument document =
                documentRepository
                        .findScopedForUpdate(
                                documentId, context.organizationId(), context.workspaceId())
                        .orElseThrow(DocumentApplicationService::notFound);
        try {
            document.archive(Instant.now());
        } catch (IllegalStateException exception) {
            throw new KnowledgeApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_LIFECYCLE_TRANSITION",
                    "The document cannot be archived from its current state");
        }
        return detail(
                context,
                document,
                versionRepository.findAllByDocumentIdOrderByVersionNumberDesc(documentId));
    }

    @Transactional
    DocumentVersionResponse retry(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID documentId,
            UUID versionId) {
        WorkspaceAccessContext context =
                access(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.RETRY_INGESTION);
        findDocument(context, documentId);
        DocumentVersion version = findVersion(context, documentId, versionId);
        DocumentIngestionJob job =
                jobRepository
                        .findByDocumentVersionId(versionId)
                        .orElseThrow(DocumentApplicationService::notFound);
        if (version.getIngestionStatus() != DocumentIngestionStatus.FAILED
                || job.getStatus() != IngestionJobStatus.FAILED) {
            throw new KnowledgeApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_LIFECYCLE_TRANSITION",
                    "Only a failed ingestion can be retried");
        }
        try {
            job.retry(Instant.now());
            version.queue();
        } catch (IllegalStateException exception) {
            throw new KnowledgeApiException(
                    HttpStatus.CONFLICT,
                    "RETRY_LIMIT_EXCEEDED",
                    "The ingestion retry limit has been reached");
        }
        return version(version);
    }

    private WorkspaceAccessContext access(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            WorkspaceOperation operation) {
        return authorizationService.requireAccess(
                authentication, organizationSlug, workspaceSlug, operation);
    }

    private KnowledgeDocument findDocument(WorkspaceAccessContext context, UUID documentId) {
        return documentRepository
                .findByIdAndOrganizationIdAndWorkspaceId(
                        documentId, context.organizationId(), context.workspaceId())
                .orElseThrow(DocumentApplicationService::notFound);
    }

    private DocumentVersion findVersion(
            WorkspaceAccessContext context, UUID documentId, UUID versionId) {
        return versionRepository
                .findByIdAndDocumentIdAndOrganizationIdAndWorkspaceId(
                        versionId, documentId, context.organizationId(), context.workspaceId())
                .orElseThrow(DocumentApplicationService::notFound);
    }

    private DocumentVersion latestVersion(UUID documentId) {
        return versionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .orElseThrow(DocumentApplicationService::notFound);
    }

    private DocumentSummaryResponse summary(
            KnowledgeDocument document, DocumentVersion latestVersion) {
        return new DocumentSummaryResponse(
                document.getId(),
                document.getTitle(),
                document.getStatus(),
                document.getCreatedBySubject(),
                document.getCreatedAt(),
                document.getArchivedAt(),
                version(latestVersion));
    }

    private DocumentDetailResponse detail(
            WorkspaceAccessContext context,
            KnowledgeDocument document,
            List<DocumentVersion> versions) {
        return new DocumentDetailResponse(
                document.getId(),
                document.getOrganizationId(),
                document.getWorkspaceId(),
                context.organizationSlug(),
                context.workspaceSlug(),
                document.getTitle(),
                document.getStatus(),
                document.getCreatedBySubject(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getArchivedAt(),
                versions.stream().map(this::version).toList());
    }

    private DocumentVersionResponse version(DocumentVersion version) {
        return new DocumentVersionResponse(
                version.getId(),
                version.getDocumentId(),
                version.getVersionNumber(),
                version.getOriginalFilename(),
                version.getDeclaredContentType(),
                version.getDetectedContentType(),
                version.getByteSize(),
                version.getSha256Hex(),
                version.getParserName(),
                version.getParserVersion(),
                version.getIngestionStatus(),
                version.getFailureCode(),
                version.getFailureMessage(),
                version.getCreatedBySubject(),
                version.getCreatedAt(),
                version.getReadyAt(),
                textUnitRepository.countByDocumentVersionId(version.getId()));
    }

    private UploadAcceptedResponse accepted(PersistedUpload upload) {
        DocumentVersion version = upload.version();
        return new UploadAcceptedResponse(
                upload.document().getId(),
                version.getId(),
                version.getVersionNumber(),
                version.getIngestionStatus(),
                version.getSha256Hex(),
                version.getByteSize());
    }

    private static KnowledgeApiException notFound() {
        return new KnowledgeApiException(
                HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document was not found");
    }
}
