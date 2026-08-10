package io.github.yanziki.enterpriseai.knowledge.api;

import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionStatus;
import io.github.yanziki.enterpriseai.knowledge.DocumentStatus;
import io.github.yanziki.enterpriseai.knowledge.IngestionFailureCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record DocumentPageResponse(
        List<DocumentSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}

record DocumentSummaryResponse(
        UUID id,
        String title,
        DocumentStatus status,
        String createdBySubject,
        Instant createdAt,
        Instant archivedAt,
        DocumentVersionResponse latestVersion) {}

record DocumentDetailResponse(
        UUID id,
        UUID organizationId,
        UUID workspaceId,
        String organizationSlug,
        String workspaceSlug,
        String title,
        DocumentStatus status,
        String createdBySubject,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        List<DocumentVersionResponse> versions) {}

record DocumentVersionResponse(
        UUID id,
        UUID documentId,
        int versionNumber,
        String originalFilename,
        String declaredContentType,
        String detectedContentType,
        long byteSize,
        String sha256Hex,
        String parserName,
        String parserVersion,
        DocumentIngestionStatus ingestionStatus,
        IngestionFailureCode failureCode,
        String failureMessage,
        String createdBySubject,
        Instant createdAt,
        Instant readyAt,
        long textUnitCount) {}

record UploadAcceptedResponse(
        UUID documentId,
        UUID versionId,
        int versionNumber,
        DocumentIngestionStatus ingestionStatus,
        String sha256Hex,
        long byteSize) {}
