package io.github.yanziki.enterpriseai.workflow.review;

import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewAuditEventResponse;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewCaseDetailResponse;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewCasePageResponse;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewCaseSummaryResponse;
import static io.github.yanziki.enterpriseai.workflow.review.ReviewApiModels.ReviewEvidenceResponse;

import io.github.yanziki.enterpriseai.answer.AnswerStatus;
import io.github.yanziki.enterpriseai.retrieval.RetrievalMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ReviewCaseQueryRepository {

    private static final String SUMMARY_SELECT =
            """
            SELECT review_case.id,
                   review_case.answer_attempt_id,
                   review_case.reason,
                   review_case.status,
                   LEFT(answer_attempt.question, 160) AS question_preview,
                   review_case.created_by_subject,
                   creator.display_name AS creator_display_name,
                   review_case.assigned_to_subject,
                   assignee.display_name AS assignee_display_name,
                   review_case.created_at,
                   review_case.updated_at,
                   review_case.version
            FROM review_cases review_case
            JOIN answer_attempts answer_attempt
              ON answer_attempt.id = review_case.answer_attempt_id
             AND answer_attempt.organization_id = review_case.organization_id
             AND answer_attempt.workspace_id = review_case.workspace_id
            JOIN user_profiles creator
              ON creator.identity_subject = review_case.created_by_subject
            LEFT JOIN user_profiles assignee
              ON assignee.identity_subject = review_case.assigned_to_subject
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReviewCaseQueryRepository(
            NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ReviewCasePageResponse list(
            UUID organizationId,
            UUID workspaceId,
            String currentSubject,
            boolean restrictToCreator,
            ReviewCaseStatus status,
            ReviewReason reason,
            boolean assignedToMe,
            boolean unassigned,
            boolean createdByMe,
            int page,
            int size) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("workspaceId", workspaceId)
                        .addValue("currentSubject", currentSubject)
                        .addValue("size", size)
                        .addValue("offset", page * size);
        List<String> conditions =
                new ArrayList<>(
                        List.of(
                                "review_case.organization_id = :organizationId",
                                "review_case.workspace_id = :workspaceId"));
        if (restrictToCreator || createdByMe) {
            conditions.add("review_case.created_by_subject = :currentSubject");
        }
        if (assignedToMe) {
            conditions.add("review_case.assigned_to_subject = :currentSubject");
        }
        if (unassigned) {
            conditions.add("review_case.assigned_to_subject IS NULL");
        }
        if (status != null) {
            conditions.add("review_case.status = :status");
            parameters.addValue("status", status.name());
        }
        if (reason != null) {
            conditions.add("review_case.reason = :reason");
            parameters.addValue("reason", reason.name());
        }
        String where = " WHERE " + String.join(" AND ", conditions);
        Long total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM review_cases review_case" + where,
                        parameters,
                        Long.class);
        List<ReviewCaseSummaryResponse> content =
                jdbcTemplate.query(
                        SUMMARY_SELECT
                                + where
                                + " ORDER BY review_case.created_at DESC, review_case.id DESC"
                                + " LIMIT :size OFFSET :offset",
                        parameters,
                        this::mapSummary);
        long totalElements = total == null ? 0 : total;
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new ReviewCasePageResponse(content, page, size, totalElements, totalPages);
    }

    public Optional<ReviewCaseDetailResponse> detail(
            UUID organizationId, UUID workspaceId, UUID reviewCaseId) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("workspaceId", workspaceId)
                        .addValue("reviewCaseId", reviewCaseId);
        List<ReviewCaseDetailRow> rows =
                jdbcTemplate.query(
                        """
                        SELECT review_case.id,
                               review_case.answer_attempt_id,
                               review_case.reason,
                               review_case.status,
                               review_case.request_note,
                               answer_attempt.question,
                               answer_attempt.status AS answer_status,
                               answer_attempt.answer_text,
                               answer_attempt.requested_retrieval_mode,
                               answer_attempt.effective_retrieval_mode,
                               answer_attempt.retrieved_chunk_count,
                               answer_attempt.context_characters,
                               review_case.created_by_subject,
                               creator.display_name AS creator_display_name,
                               review_case.assigned_to_subject,
                               assignee.display_name AS assignee_display_name,
                               review_case.resolution,
                               review_case.reviewer_note,
                               review_case.resolved_by_subject,
                               resolver.display_name AS resolver_display_name,
                               review_case.created_at,
                               review_case.updated_at,
                               review_case.resolved_at,
                               review_case.version
                        FROM review_cases review_case
                        JOIN answer_attempts answer_attempt
                          ON answer_attempt.id = review_case.answer_attempt_id
                         AND answer_attempt.organization_id = review_case.organization_id
                         AND answer_attempt.workspace_id = review_case.workspace_id
                        JOIN user_profiles creator
                          ON creator.identity_subject = review_case.created_by_subject
                        LEFT JOIN user_profiles assignee
                          ON assignee.identity_subject = review_case.assigned_to_subject
                        LEFT JOIN user_profiles resolver
                          ON resolver.identity_subject = review_case.resolved_by_subject
                        WHERE review_case.id = :reviewCaseId
                          AND review_case.organization_id = :organizationId
                          AND review_case.workspace_id = :workspaceId
                        """,
                        parameters,
                        this::mapDetailRow);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ReviewCaseDetailRow row = rows.getFirst();
        return Optional.of(
                new ReviewCaseDetailResponse(
                        row.id(),
                        row.answerId(),
                        row.reason(),
                        row.status(),
                        row.requestNote(),
                        row.question(),
                        row.answerStatus(),
                        row.answer(),
                        row.requestedMode(),
                        row.effectiveMode(),
                        row.retrievedChunkCount(),
                        row.contextCharacters(),
                        row.createdBySubject(),
                        row.createdByDisplayName(),
                        row.assignedToSubject(),
                        row.assignedToDisplayName(),
                        row.resolution(),
                        row.reviewerNote(),
                        row.resolvedBySubject(),
                        row.resolvedByDisplayName(),
                        row.createdAt(),
                        row.updatedAt(),
                        row.resolvedAt(),
                        row.version(),
                        evidence(organizationId, workspaceId, row.answerId()),
                        auditEvents(organizationId, workspaceId, row.id())));
    }

    public List<ReviewAuditEventResponse> auditEvents(
            UUID organizationId, UUID workspaceId, UUID reviewCaseId) {
        return jdbcTemplate.query(
                """
                SELECT event.id,
                       event.event_type,
                       event.actor_subject,
                       actor.display_name AS actor_display_name,
                       event.occurred_at,
                       event.metadata::text AS metadata
                FROM audit_events event
                JOIN user_profiles actor
                  ON actor.identity_subject = event.actor_subject
                WHERE event.aggregate_type = 'REVIEW_CASE'
                  AND event.aggregate_id = :reviewCaseId
                  AND event.organization_id = :organizationId
                  AND event.workspace_id = :workspaceId
                ORDER BY event.occurred_at, event.id
                """,
                new MapSqlParameterSource()
                        .addValue("reviewCaseId", reviewCaseId)
                        .addValue("organizationId", organizationId)
                        .addValue("workspaceId", workspaceId),
                (resultSet, rowNumber) ->
                        new ReviewAuditEventResponse(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("event_type"),
                                resultSet.getString("actor_subject"),
                                resultSet.getString("actor_display_name"),
                                resultSet.getTimestamp("occurred_at").toInstant(),
                                metadata(resultSet.getString("metadata"))));
    }

    private List<ReviewEvidenceResponse> evidence(
            UUID organizationId, UUID workspaceId, UUID answerId) {
        return jdbcTemplate.query(
                """
                SELECT citation_alias, rank, cited, retrieval_chunk_id, document_id,
                       document_title, document_version_id, version_number, locator_type,
                       locator_value, start_character, end_character, excerpt
                FROM answer_attempt_evidence
                WHERE answer_attempt_id = :answerId
                  AND organization_id = :organizationId
                  AND workspace_id = :workspaceId
                ORDER BY rank
                """,
                new MapSqlParameterSource()
                        .addValue("answerId", answerId)
                        .addValue("organizationId", organizationId)
                        .addValue("workspaceId", workspaceId),
                (resultSet, rowNumber) ->
                        new ReviewEvidenceResponse(
                                resultSet.getString("citation_alias"),
                                resultSet.getInt("rank"),
                                resultSet.getBoolean("cited"),
                                resultSet.getObject("retrieval_chunk_id", UUID.class),
                                resultSet.getObject("document_id", UUID.class),
                                resultSet.getString("document_title"),
                                resultSet.getObject("document_version_id", UUID.class),
                                resultSet.getInt("version_number"),
                                resultSet.getString("locator_type"),
                                resultSet.getString("locator_value"),
                                resultSet.getInt("start_character"),
                                resultSet.getInt("end_character"),
                                resultSet.getString("excerpt")));
    }

    private ReviewCaseSummaryResponse mapSummary(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ReviewCaseSummaryResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("answer_attempt_id", UUID.class),
                ReviewReason.valueOf(resultSet.getString("reason")),
                ReviewCaseStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("question_preview"),
                resultSet.getString("created_by_subject"),
                resultSet.getString("creator_display_name"),
                resultSet.getString("assigned_to_subject"),
                resultSet.getString("assignee_display_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }

    private ReviewCaseDetailRow mapDetailRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ReviewCaseDetailRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("answer_attempt_id", UUID.class),
                ReviewReason.valueOf(resultSet.getString("reason")),
                ReviewCaseStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("request_note"),
                resultSet.getString("question"),
                AnswerStatus.valueOf(resultSet.getString("answer_status")),
                resultSet.getString("answer_text"),
                RetrievalMode.valueOf(resultSet.getString("requested_retrieval_mode")),
                RetrievalMode.valueOf(resultSet.getString("effective_retrieval_mode")),
                resultSet.getInt("retrieved_chunk_count"),
                resultSet.getInt("context_characters"),
                resultSet.getString("created_by_subject"),
                resultSet.getString("creator_display_name"),
                resultSet.getString("assigned_to_subject"),
                resultSet.getString("assignee_display_name"),
                enumOrNull(ReviewResolution.class, resultSet.getString("resolution")),
                resultSet.getString("reviewer_note"),
                resultSet.getString("resolved_by_subject"),
                resultSet.getString("resolver_display_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                instantOrNull(resultSet, "resolved_at"),
                resultSet.getLong("version"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(String json) {
        return objectMapper.readValue(json, Map.class);
    }

    private Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
        var timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private <T extends Enum<T>> T enumOrNull(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record ReviewCaseDetailRow(
            UUID id,
            UUID answerId,
            ReviewReason reason,
            ReviewCaseStatus status,
            String requestNote,
            String question,
            AnswerStatus answerStatus,
            String answer,
            RetrievalMode requestedMode,
            RetrievalMode effectiveMode,
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
            long version) {}
}
