package io.github.yanziki.enterpriseai.workflow.review;

import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.CreateReviewCaseRequest;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.DismissReviewCaseRequest;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ResolveReviewCaseRequest;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewAuditEventResponse;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewCaseDetailResponse;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewCasePageResponse;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationSlug}/workspaces/{workspaceSlug}")
public class ReviewCaseController {

    private final ReviewCaseApplicationService reviewService;

    public ReviewCaseController(ReviewCaseApplicationService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/answers/{answerId}/review-case")
    ResponseEntity<ReviewCaseDetailResponse> create(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID answerId,
            @RequestBody(required = false) CreateReviewCaseRequest request,
            JwtAuthenticationToken authentication) {
        var created =
                reviewService.create(
                        authentication, organizationSlug, workspaceSlug, answerId, request);
        if (!created.created()) {
            return ResponseEntity.ok(created.detail());
        }
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/organizations/"
                                        + organizationSlug
                                        + "/workspaces/"
                                        + workspaceSlug
                                        + "/review-cases/"
                                        + created.detail().id()))
                .body(created.detail());
    }

    @GetMapping("/review-cases")
    ReviewCasePageResponse list(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @RequestParam(required = false) ReviewCaseStatus status,
            @RequestParam(required = false) ReviewReason reason,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(defaultValue = "false") boolean createdByMe,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken authentication) {
        return reviewService.list(
                authentication,
                organizationSlug,
                workspaceSlug,
                status,
                reason,
                assignedToMe,
                unassigned,
                createdByMe,
                page,
                size);
    }

    @GetMapping("/review-cases/{reviewCaseId}")
    ReviewCaseDetailResponse detail(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID reviewCaseId,
            JwtAuthenticationToken authentication) {
        return reviewService.detail(authentication, organizationSlug, workspaceSlug, reviewCaseId);
    }

    @GetMapping("/review-cases/{reviewCaseId}/audit-events")
    List<ReviewAuditEventResponse> auditEvents(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID reviewCaseId,
            JwtAuthenticationToken authentication) {
        return reviewService.auditEvents(
                authentication, organizationSlug, workspaceSlug, reviewCaseId);
    }

    @PostMapping("/review-cases/{reviewCaseId}/claim")
    ReviewCaseDetailResponse claim(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID reviewCaseId,
            JwtAuthenticationToken authentication) {
        return reviewService.claim(authentication, organizationSlug, workspaceSlug, reviewCaseId);
    }

    @PostMapping("/review-cases/{reviewCaseId}/resolve")
    ReviewCaseDetailResponse resolve(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID reviewCaseId,
            @RequestBody(required = false) ResolveReviewCaseRequest request,
            JwtAuthenticationToken authentication) {
        return reviewService.resolve(
                authentication, organizationSlug, workspaceSlug, reviewCaseId, request);
    }

    @PostMapping("/review-cases/{reviewCaseId}/dismiss")
    ReviewCaseDetailResponse dismiss(
            @PathVariable String organizationSlug,
            @PathVariable String workspaceSlug,
            @PathVariable UUID reviewCaseId,
            @RequestBody(required = false) DismissReviewCaseRequest request,
            JwtAuthenticationToken authentication) {
        return reviewService.dismiss(
                authentication, organizationSlug, workspaceSlug, reviewCaseId, request);
    }
}
