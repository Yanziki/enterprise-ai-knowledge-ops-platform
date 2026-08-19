package io.github.yanziki.enterpriseai.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ReviewAuditStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReviewAuditStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void append(
            UUID organizationId,
            UUID workspaceId,
            UUID reviewCaseId,
            ReviewAuditEventType eventType,
            String actorSubject,
            Instant occurredAt,
            Map<String, String> metadata) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_events (
                    id, organization_id, workspace_id, aggregate_type, aggregate_id,
                    event_type, actor_subject, occurred_at, metadata
                ) VALUES (?, ?, ?, 'REVIEW_CASE', ?, ?, ?, ?, CAST(? AS jsonb))
                """,
                UUID.randomUUID(),
                organizationId,
                workspaceId,
                reviewCaseId,
                eventType.name(),
                actorSubject,
                Timestamp.from(occurredAt),
                objectMapper.writeValueAsString(metadata));
    }
}
