package io.github.yanziki.enterpriseai.workflow.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "review_cases")
public class ReviewCase {

    @Id private UUID id;

    @Column(name = "answer_attempt_id", nullable = false)
    private UUID answerAttemptId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "created_by_subject", nullable = false, length = 255)
    private String createdBySubject;

    @Column(name = "assigned_to_subject", length = 255)
    private String assignedToSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ReviewReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewCaseStatus status;

    @Column(name = "request_note", length = 1000)
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private ReviewResolution resolution;

    @Column(name = "reviewer_note", length = 2000)
    private String reviewerNote;

    @Column(name = "resolved_by_subject", length = 255)
    private String resolvedBySubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ReviewCase() {}

    public ReviewCase(
            UUID id,
            UUID answerAttemptId,
            UUID organizationId,
            UUID workspaceId,
            String createdBySubject,
            ReviewReason reason,
            String requestNote,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.answerAttemptId = Objects.requireNonNull(answerAttemptId);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.createdBySubject = Objects.requireNonNull(createdBySubject);
        this.reason = Objects.requireNonNull(reason);
        this.requestNote = requestNote;
        this.status = ReviewCaseStatus.OPEN;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    public void claim(String reviewerSubject, Instant claimedAt) {
        if (status != ReviewCaseStatus.OPEN || assignedToSubject != null) {
            throw new ReviewCaseConflictException(
                    "REVIEW_CASE_ALREADY_CLAIMED",
                    "The review case is no longer available to claim");
        }
        assignedToSubject = reviewerSubject;
        status = ReviewCaseStatus.IN_REVIEW;
        updatedAt = claimedAt;
    }

    public void resolve(
            String reviewerSubject,
            ReviewResolution nextResolution,
            String nextReviewerNote,
            Instant completedAt) {
        requireAssignedInReview(reviewerSubject);
        status = ReviewCaseStatus.RESOLVED;
        resolution = Objects.requireNonNull(nextResolution);
        reviewerNote = nextReviewerNote;
        resolvedBySubject = reviewerSubject;
        resolvedAt = completedAt;
        updatedAt = completedAt;
    }

    public void dismiss(String reviewerSubject, String nextReviewerNote, Instant completedAt) {
        if (status == ReviewCaseStatus.IN_REVIEW) {
            requireAssignedInReview(reviewerSubject);
        } else if (status != ReviewCaseStatus.OPEN) {
            throw invalidTransition();
        }
        status = ReviewCaseStatus.DISMISSED;
        reviewerNote = nextReviewerNote;
        resolvedBySubject = reviewerSubject;
        resolvedAt = completedAt;
        updatedAt = completedAt;
    }

    private void requireAssignedInReview(String reviewerSubject) {
        if (status != ReviewCaseStatus.IN_REVIEW) {
            throw invalidTransition();
        }
        if (!Objects.equals(assignedToSubject, reviewerSubject)) {
            throw new ReviewCaseConflictException(
                    "REVIEW_CASE_NOT_ASSIGNEE",
                    "Only the assigned reviewer can complete this review case");
        }
    }

    private ReviewCaseConflictException invalidTransition() {
        return new ReviewCaseConflictException(
                "INVALID_REVIEW_TRANSITION",
                "The requested review lifecycle transition is invalid");
    }

    public UUID getId() {
        return id;
    }

    public UUID getAnswerAttemptId() {
        return answerAttemptId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getCreatedBySubject() {
        return createdBySubject;
    }

    public String getAssignedToSubject() {
        return assignedToSubject;
    }

    public ReviewReason getReason() {
        return reason;
    }

    public ReviewCaseStatus getStatus() {
        return status;
    }

    public String getRequestNote() {
        return requestNote;
    }

    public ReviewResolution getResolution() {
        return resolution;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public String getResolvedBySubject() {
        return resolvedBySubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }
}
