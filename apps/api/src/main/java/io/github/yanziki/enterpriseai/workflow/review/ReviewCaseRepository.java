package io.github.yanziki.enterpriseai.workflow.review;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewCaseRepository extends JpaRepository<ReviewCase, UUID> {

    @Query(
            """
            SELECT reviewCase
            FROM ReviewCase reviewCase
            WHERE reviewCase.answerAttemptId = :answerAttemptId
              AND reviewCase.organizationId = :organizationId
              AND reviewCase.workspaceId = :workspaceId
              AND reviewCase.status IN (
                  io.github.yanziki.enterpriseai.workflow.review.ReviewCaseStatus.OPEN,
                  io.github.yanziki.enterpriseai.workflow.review.ReviewCaseStatus.IN_REVIEW
              )
            """)
    Optional<ReviewCase> findActiveByAnswerAttempt(
            @Param("answerAttemptId") UUID answerAttemptId,
            @Param("organizationId") UUID organizationId,
            @Param("workspaceId") UUID workspaceId);

    Optional<ReviewCase> findByIdAndOrganizationIdAndWorkspaceId(
            UUID id, UUID organizationId, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT reviewCase
            FROM ReviewCase reviewCase
            WHERE reviewCase.id = :id
              AND reviewCase.organizationId = :organizationId
              AND reviewCase.workspaceId = :workspaceId
            """)
    Optional<ReviewCase> findScopedForUpdate(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("workspaceId") UUID workspaceId);
}
