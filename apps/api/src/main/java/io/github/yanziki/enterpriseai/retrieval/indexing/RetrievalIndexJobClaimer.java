package io.github.yanziki.enterpriseai.retrieval.indexing;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RetrievalIndexJobClaimer {

    static final int MAX_ATTEMPTS = 3;

    private final JdbcTemplate jdbcTemplate;

    public RetrievalIndexJobClaimer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<UUID> claimNextBatch(int batchSize, Instant now) {
        return jdbcTemplate.query(
                """
                WITH candidates AS (
                    SELECT job.id
                    FROM retrieval_index_jobs AS job
                    JOIN retrieval_indexes AS index ON index.id = job.retrieval_index_id
                    WHERE job.status = 'QUEUED'
                      AND index.status = 'QUEUED'
                      AND job.next_attempt_at <= ?
                      AND job.attempt_count < ?
                    ORDER BY job.next_attempt_at, job.created_at
                    FOR UPDATE OF job SKIP LOCKED
                    LIMIT ?
                ), claimed AS (
                    UPDATE retrieval_index_jobs AS job
                    SET status = 'PROCESSING',
                        attempt_count = job.attempt_count + 1,
                        claimed_at = ?,
                        completed_at = NULL,
                        updated_at = ?
                    FROM candidates
                    WHERE job.id = candidates.id
                    RETURNING job.id, job.retrieval_index_id
                ), started AS (
                    UPDATE retrieval_indexes AS index
                    SET status = 'PROCESSING'
                    FROM claimed
                    WHERE index.id = claimed.retrieval_index_id
                    RETURNING index.id
                )
                SELECT claimed.id
                FROM claimed
                JOIN started ON started.id = claimed.retrieval_index_id
                """,
                preparedStatement -> {
                    preparedStatement.setTimestamp(1, Timestamp.from(now));
                    preparedStatement.setInt(2, MAX_ATTEMPTS);
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
                UPDATE retrieval_indexes AS index
                SET status = CASE WHEN job.attempt_count >= ? THEN 'FAILED' ELSE 'QUEUED' END,
                    failure_code = CASE WHEN job.attempt_count >= ?
                        THEN 'INTERNAL_INDEXING_ERROR' ELSE NULL END,
                    failure_message = CASE WHEN job.attempt_count >= ?
                        THEN 'Indexing stopped before completion' ELSE NULL END
                FROM retrieval_index_jobs AS job
                WHERE job.retrieval_index_id = index.id
                  AND job.status = 'PROCESSING'
                  AND job.claimed_at < ?
                """,
                MAX_ATTEMPTS,
                MAX_ATTEMPTS,
                MAX_ATTEMPTS,
                Timestamp.from(staleBefore));
        jdbcTemplate.update(
                """
                UPDATE retrieval_index_jobs
                SET status = CASE WHEN attempt_count >= ? THEN 'FAILED' ELSE 'QUEUED' END,
                    claimed_at = NULL,
                    completed_at = CASE WHEN attempt_count >= ?
                        THEN CAST(? AS timestamptz) ELSE NULL END,
                    next_attempt_at = ?,
                    last_error_code = 'INTERNAL_INDEXING_ERROR',
                    last_error_message = 'Indexing stopped before completion',
                    updated_at = ?
                WHERE status = 'PROCESSING'
                  AND claimed_at < ?
                """,
                MAX_ATTEMPTS,
                MAX_ATTEMPTS,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(staleBefore));
    }
}
