package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionJob;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IngestionJobClaimer {

    private final JdbcTemplate jdbcTemplate;

    public IngestionJobClaimer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<UUID> claimNextBatch(int batchSize, Instant now) {
        return jdbcTemplate.query(
                """
                WITH candidates AS (
                    SELECT id
                    FROM document_ingestion_jobs
                    WHERE status = 'QUEUED'
                      AND next_attempt_at <= ?
                      AND attempt_count < ?
                    ORDER BY next_attempt_at, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE document_ingestion_jobs AS job
                SET status = 'PROCESSING',
                    attempt_count = job.attempt_count + 1,
                    claimed_at = ?,
                    completed_at = NULL,
                    updated_at = ?
                FROM candidates
                WHERE job.id = candidates.id
                RETURNING job.id
                """,
                preparedStatement -> {
                    preparedStatement.setTimestamp(1, Timestamp.from(now));
                    preparedStatement.setInt(2, DocumentIngestionJob.MAX_ATTEMPTS);
                    preparedStatement.setInt(3, batchSize);
                    preparedStatement.setTimestamp(4, Timestamp.from(now));
                    preparedStatement.setTimestamp(5, Timestamp.from(now));
                },
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
    }

    @Transactional
    public void recoverStaleClaims(Instant staleBefore, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE document_versions AS version
                SET ingestion_status = 'FAILED',
                    failure_code = 'INTERNAL_PROCESSING_ERROR',
                    failure_message = 'Processing stopped before completion',
                    ready_at = NULL
                FROM document_ingestion_jobs AS job
                WHERE job.document_version_id = version.id
                  AND job.status = 'PROCESSING'
                  AND job.claimed_at < ?
                  AND job.attempt_count >= ?
                """,
                Timestamp.from(staleBefore),
                DocumentIngestionJob.MAX_ATTEMPTS);
        jdbcTemplate.update(
                """
                UPDATE document_ingestion_jobs
                SET status = 'FAILED',
                    completed_at = ?,
                    last_error_code = 'INTERNAL_PROCESSING_ERROR',
                    last_error_message = 'Processing stopped before completion',
                    updated_at = ?
                WHERE status = 'PROCESSING'
                  AND claimed_at < ?
                  AND attempt_count >= ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(staleBefore),
                DocumentIngestionJob.MAX_ATTEMPTS);
        jdbcTemplate.update(
                """
                UPDATE document_versions AS version
                SET ingestion_status = 'QUEUED',
                    failure_code = NULL,
                    failure_message = NULL,
                    ready_at = NULL
                FROM document_ingestion_jobs AS job
                WHERE job.document_version_id = version.id
                  AND job.status = 'PROCESSING'
                  AND job.claimed_at < ?
                  AND job.attempt_count < ?
                """,
                Timestamp.from(staleBefore),
                DocumentIngestionJob.MAX_ATTEMPTS);
        jdbcTemplate.update(
                """
                UPDATE document_ingestion_jobs
                SET status = 'QUEUED',
                    claimed_at = NULL,
                    next_attempt_at = ?,
                    last_error_code = 'INTERNAL_PROCESSING_ERROR',
                    last_error_message = 'Processing stopped before completion',
                    updated_at = ?
                WHERE status = 'PROCESSING'
                  AND claimed_at < ?
                  AND attempt_count < ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(staleBefore),
                DocumentIngestionJob.MAX_ATTEMPTS);
    }
}
