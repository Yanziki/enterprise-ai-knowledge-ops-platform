package io.github.yanziki.enterpriseai.workflow.review;

import io.github.yanziki.enterpriseai.answer.AnswerStatus;
import io.github.yanziki.enterpriseai.retrieval.RetrievalMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReviewApiModels {

    private ReviewApiModels() {}

    public record CreateReviewCaseRequest(ReviewReason reason, String note) {}

    public record ResolveReviewCaseRequest(
            ReviewResolution resolution, String reviewerNote, Long version) {}

    public record DismissReviewCaseRequest(String reviewerNote, Long version) {}

    public record ReviewCasePageResponse(
            List<ReviewCaseSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {}

    public record ReviewCaseSummaryResponse(
            UUID id,
            UUID answerId,
            ReviewReason reason,
            ReviewCaseStatus status,
            String questionPreview,
            String createdBySubject,
            String createdByDisplayName,
            String assignedToSubject,
            String assignedToDisplayName,
            Instant createdAt,
            Instant updatedAt,
            long version) {}

    public record ReviewCaseDetailResponse(
            UUID id,
            UUID answerId,
            ReviewReason reason,
            ReviewCaseStatus status,
            String requestNote,
            String question,
            AnswerStatus answerStatus,
            String answer,
            RetrievalMode requestedRetrievalMode,
            RetrievalMode effectiveRetrievalMode,
            int retrievedChunkCount,
            int contextCharacters,
            String createdBySubject,
            String createdByDisplayName,
            String assignedToSubject,
            String assignedToDisplayName,
            ReviewResolution resolution,
            String reviewerNote,
            String resolvedBySubject,
            String resolvedByDisplayName,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long version,
            List<ReviewEvidenceResponse> evidence,
            List<ReviewAuditEventResponse> auditEvents) {}

    public record ReviewEvidenceResponse(
            String citationId,
            int rank,
            boolean cited,
            UUID chunkId,
            UUID documentId,
            String documentTitle,
            UUID documentVersionId,
            int versionNumber,
            String locatorType,
            String locatorValue,
            int startCharacter,
            int endCharacter,
            String excerpt) {}

    public record ReviewAuditEventResponse(
            UUID id,
            String eventType,
            String actorSubject,
            String actorDisplayName,
            Instant occurredAt,
            Map<String, Object> metadata) {}
}
