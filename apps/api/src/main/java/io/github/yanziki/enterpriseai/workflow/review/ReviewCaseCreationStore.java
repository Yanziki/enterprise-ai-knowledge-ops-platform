package io.github.yanziki.enterpriseai.workflow.review;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewCaseCreationStore {

    private final JdbcTemplate jdbcTemplate;

    public ReviewCaseCreationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AnswerAttemptReference> findAnswerAttempt(
            UUID answerId, UUID organizationId, UUID workspaceId) {
        List<AnswerAttemptReference> rows =
                jdbcTemplate.query(
                        """
                        SELECT id, created_by_subject, status
                        FROM answer_attempts
                        WHERE id = ?
                          AND organization_id = ?
                          AND workspace_id = ?
                        """,
                        (resultSet, rowNumber) ->
                                new AnswerAttemptReference(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getString("created_by_subject"),
                                        resultSet.getString("status")),
                        answerId,
                        organizationId,
                        workspaceId);
        return rows.stream().findFirst();
    }

    public CreationResult createOrFindActive(
            UUID answerId,
            UUID organizationId,
            UUID workspaceId,
            String creatorSubject,
            ReviewReason reason,
            String note,
            Instant createdAt) {
        UUID candidateId = UUID.randomUUID();
        List<UUID> inserted =
                jdbcTemplate.query(
                        """
                        INSERT INTO review_cases (
                            id, answer_attempt_id, organization_id, workspace_id,
                            created_by_subject, reason, status, request_note,
                            created_at, updated_at, version
                        ) VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, 0)
                        ON CONFLICT (answer_attempt_id)
                            WHERE status IN ('OPEN', 'IN_REVIEW')
                        DO NOTHING
                        RETURNING id
                        """,
                        (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                        candidateId,
                        answerId,
                        organizationId,
                        workspaceId,
                        creatorSubject,
                        reason.name(),
                        note,
                        Timestamp.from(createdAt),
                        Timestamp.from(createdAt));
        if (!inserted.isEmpty()) {
            return new CreationResult(inserted.getFirst(), true);
        }
        UUID existing =
                jdbcTemplate.queryForObject(
                        """
                        SELECT id
                        FROM review_cases
                        WHERE answer_attempt_id = ?
                          AND organization_id = ?
                          AND workspace_id = ?
                          AND status IN ('OPEN', 'IN_REVIEW')
                        """,
                        UUID.class,
                        answerId,
                        organizationId,
                        workspaceId);
        return new CreationResult(existing, false);
    }

    public record AnswerAttemptReference(UUID id, String createdBySubject, String status) {}

    public record CreationResult(UUID reviewCaseId, boolean created) {}
}
