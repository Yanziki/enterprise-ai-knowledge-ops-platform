package io.github.yanziki.enterpriseai.workflow.review;

import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.CreateReviewCaseRequest;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.DismissReviewCaseRequest;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ResolveReviewCaseRequest;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewAuditEventResponse;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewCaseDetailResponse;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewCasePageResponse;

import io.github.yanziki.enterpriseai.audit.ReviewAuditEventType;
import io.github.yanziki.enterpriseai.audit.ReviewAuditStore;
import io.github.yanziki.enterpriseai.knowledge.api.KnowledgeApiException;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAccessContext;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAccessRole;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAuthorizationService;
import io.github.yanziki.enterpriseai.tenant.WorkspaceOperation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewCaseApplicationService {

    private final WorkspaceAuthorizationService authorizationService;
    private final ReviewCaseCreationStore creationStore;
    private final ReviewCaseRepository reviewCaseRepository;
    private final ReviewCaseQueryRepository queryRepository;
    private final ReviewAuditStore auditStore;

    public ReviewCaseApplicationService(
            WorkspaceAuthorizationService authorizationService,
            ReviewCaseCreationStore creationStore,
            ReviewCaseRepository reviewCaseRepository,
            ReviewCaseQueryRepository queryRepository,
            ReviewAuditStore auditStore) {
        this.authorizationService = authorizationService;
        this.creationStore = creationStore;
        this.reviewCaseRepository = reviewCaseRepository;
        this.queryRepository = queryRepository;
        this.auditStore = auditStore;
    }

    @Transactional
    public CreatedReviewCase create(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID answerId,
            CreateReviewCaseRequest request) {
        WorkspaceAccessContext scope =
                authorizationService.requireAccess(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.CREATE_REVIEW_CASE);
        ReviewReason reason =
                request == null || request.reason() == null
                        ? ReviewReason.REVIEW_REQUESTED
                        : request.reason();
        String note = boundedOptional(request == null ? null : request.note(), 1000, "note");
        var answer =
                creationStore
                        .findAnswerAttempt(answerId, scope.organizationId(), scope.workspaceId())
                        .orElseThrow(this::notFound);
        if (!canManage(scope)
                && !answer.createdBySubject().equals(scope.authenticatedUser().subject())) {
            throw notFound();
        }

        Instant now = Instant.now();
        var result =
                creationStore.createOrFindActive(
                        answerId,
                        scope.organizationId(),
                        scope.workspaceId(),
                        scope.authenticatedUser().subject(),
                        reason,
                        note,
                        now);
        if (result.created()) {
            auditStore.append(
                    scope.organizationId(),
                    scope.workspaceId(),
                    result.reviewCaseId(),
                    ReviewAuditEventType.REVIEW_CASE_CREATED,
                    scope.authenticatedUser().subject(),
                    now,
                    Map.of("reason", reason.name()));
        }
        return new CreatedReviewCase(
                authorizedDetail(scope, result.reviewCaseId()), result.created());
    }

    @Transactional(readOnly = true)
    public ReviewCasePageResponse list(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            ReviewCaseStatus status,
            ReviewReason reason,
            boolean assignedToMe,
            boolean unassigned,
            boolean createdByMe,
            int page,
            int size) {
        WorkspaceAccessContext scope =
                authorizationService.requireAccess(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.VIEW_REVIEW_CASES);
        if (page < 0 || size < 1 || size > 100) {
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE",
                    "Review pagination is outside the supported range");
        }
        return queryRepository.list(
                scope.organizationId(),
                scope.workspaceId(),
                scope.authenticatedUser().subject(),
                !canManage(scope),
                status,
                reason,
                assignedToMe,
                unassigned,
                createdByMe,
                page,
                size);
    }

    @Transactional(readOnly = true)
    public ReviewCaseDetailResponse detail(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID reviewCaseId) {
        WorkspaceAccessContext scope =
                authorizationService.requireAccess(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.VIEW_REVIEW_CASES);
        return authorizedDetail(scope, reviewCaseId);
    }

    @Transactional(readOnly = true)
    public List<ReviewAuditEventResponse> auditEvents(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID reviewCaseId) {
        WorkspaceAccessContext scope =
                authorizationService.requireAccess(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.VIEW_REVIEW_CASES);
        authorizedDetail(scope, reviewCaseId);
        return queryRepository.auditEvents(
                scope.organizationId(), scope.workspaceId(), reviewCaseId);
    }

    @Transactional
    public ReviewCaseDetailResponse claim(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID reviewCaseId) {
        WorkspaceAccessContext scope = manageScope(authentication, organizationSlug, workspaceSlug);
        ReviewCase reviewCase = locked(scope, reviewCaseId);
        Instant now = Instant.now();
        reviewCase.claim(scope.authenticatedUser().subject(), now);
        reviewCaseRepository.saveAndFlush(reviewCase);
        auditStore.append(
                scope.organizationId(),
                scope.workspaceId(),
                reviewCaseId,
                ReviewAuditEventType.REVIEW_CASE_CLAIMED,
                scope.authenticatedUser().subject(),
                now,
                Map.of());
        return authorizedDetail(scope, reviewCaseId);
    }

    @Transactional
    public ReviewCaseDetailResponse resolve(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID reviewCaseId,
            ResolveReviewCaseRequest request) {
        WorkspaceAccessContext scope = manageScope(authentication, organizationSlug, workspaceSlug);
        if (request == null || request.resolution() == null) {
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RESOLUTION",
                    "A review resolution is required");
        }
        ReviewCase reviewCase = locked(scope, reviewCaseId);
        requireCurrentVersion(reviewCase, request.version());
        requireAssignee(reviewCase, scope.authenticatedUser().subject());
        String note = boundedOptional(request.reviewerNote(), 2000, "reviewer note");
        Instant now = Instant.now();
        reviewCase.resolve(scope.authenticatedUser().subject(), request.resolution(), note, now);
        reviewCaseRepository.saveAndFlush(reviewCase);
        auditStore.append(
                scope.organizationId(),
                scope.workspaceId(),
                reviewCaseId,
                ReviewAuditEventType.REVIEW_CASE_RESOLVED,
                scope.authenticatedUser().subject(),
                now,
                Map.of("resolution", request.resolution().name()));
        return authorizedDetail(scope, reviewCaseId);
    }

    @Transactional
    public ReviewCaseDetailResponse dismiss(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            UUID reviewCaseId,
            DismissReviewCaseRequest request) {
        WorkspaceAccessContext scope = manageScope(authentication, organizationSlug, workspaceSlug);
        ReviewCase reviewCase = locked(scope, reviewCaseId);
        requireCurrentVersion(reviewCase, request == null ? null : request.version());
        if (reviewCase.getStatus() == ReviewCaseStatus.IN_REVIEW) {
            requireAssignee(reviewCase, scope.authenticatedUser().subject());
        }
        String note =
                boundedOptional(
                        request == null ? null : request.reviewerNote(), 2000, "reviewer note");
        Instant now = Instant.now();
        reviewCase.dismiss(scope.authenticatedUser().subject(), note, now);
        reviewCaseRepository.saveAndFlush(reviewCase);
        auditStore.append(
                scope.organizationId(),
                scope.workspaceId(),
                reviewCaseId,
                ReviewAuditEventType.REVIEW_CASE_DISMISSED,
                scope.authenticatedUser().subject(),
                now,
                Map.of());
        return authorizedDetail(scope, reviewCaseId);
    }

    private WorkspaceAccessContext manageScope(
            JwtAuthenticationToken authentication, String organizationSlug, String workspaceSlug) {
        return authorizationService.requireAccess(
                authentication,
                organizationSlug,
                workspaceSlug,
                WorkspaceOperation.MANAGE_REVIEW_CASES);
    }

    private ReviewCase locked(WorkspaceAccessContext scope, UUID reviewCaseId) {
        return reviewCaseRepository
                .findScopedForUpdate(reviewCaseId, scope.organizationId(), scope.workspaceId())
                .orElseThrow(this::notFound);
    }

    private ReviewCaseDetailResponse authorizedDetail(
            WorkspaceAccessContext scope, UUID reviewCaseId) {
        ReviewCaseDetailResponse detail =
                queryRepository
                        .detail(scope.organizationId(), scope.workspaceId(), reviewCaseId)
                        .orElseThrow(this::notFound);
        if (!canManage(scope)
                && !detail.createdBySubject().equals(scope.authenticatedUser().subject())) {
            throw notFound();
        }
        return detail;
    }

    private boolean canManage(WorkspaceAccessContext scope) {
        return scope.accessRole() == WorkspaceAccessRole.PLATFORM_ADMIN
                || scope.accessRole() == WorkspaceAccessRole.TENANT_ADMIN;
    }

    private void requireAssignee(ReviewCase reviewCase, String currentSubject) {
        if (!currentSubject.equals(reviewCase.getAssignedToSubject())) {
            throw new AccessDeniedException("Only the assigned reviewer can complete this case");
        }
    }

    private void requireCurrentVersion(ReviewCase reviewCase, Long requestedVersion) {
        if (requestedVersion != null && requestedVersion != reviewCase.getVersion()) {
            throw new ReviewCaseConflictException(
                    "REVIEW_CASE_CONFLICT", "The review case has changed; refresh and try again");
        }
    }

    private String boundedOptional(String value, int limit, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > limit) {
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEW_INPUT",
                    "The " + fieldName + " exceeds the configured length limit");
        }
        return normalized;
    }

    private KnowledgeApiException notFound() {
        return new KnowledgeApiException(
                HttpStatus.NOT_FOUND, "REVIEW_CASE_NOT_FOUND", "The review case was not found");
    }

    public record CreatedReviewCase(ReviewCaseDetailResponse detail, boolean created) {}
}
