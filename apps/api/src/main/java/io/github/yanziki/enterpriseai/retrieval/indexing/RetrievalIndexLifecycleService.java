package io.github.yanziki.enterpriseai.retrieval.indexing;

import io.github.yanziki.enterpriseai.retrieval.RetrievalFailureCode;
import io.github.yanziki.enterpriseai.retrieval.RetrievalProperties;
import io.github.yanziki.enterpriseai.retrieval.chunking.RetrievalChunkDraft;
import io.github.yanziki.enterpriseai.retrieval.chunking.SourceTextUnit;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetrievalIndexLifecycleService {

    private final JdbcTemplate jdbcTemplate;
    private final RetrievalProperties properties;

    public RetrievalIndexLifecycleService(
            JdbcTemplate jdbcTemplate, RetrievalProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RetrievalIndexWorkContext begin(UUID jobId) {
        List<RetrievalIndexWorkContext> contexts =
                jdbcTemplate.query(
                        """
                        SELECT job.id AS job_id,
                               index.id AS index_id,
                               index.document_id,
                               index.document_version_id,
                               index.organization_id,
                               index.workspace_id,
                               index.generation,
                               index.embedding_provider,
                               index.embedding_model,
                               index.embedding_dimension
                        FROM retrieval_index_jobs AS job
                        JOIN retrieval_indexes AS index ON index.id = job.retrieval_index_id
                        JOIN document_versions AS version
                          ON version.id = index.document_version_id
                         AND version.document_id = index.document_id
                         AND version.organization_id = index.organization_id
                         AND version.workspace_id = index.workspace_id
                        WHERE job.id = ?
                          AND job.status = 'PROCESSING'
                          AND index.status = 'PROCESSING'
                          AND version.ingestion_status = 'READY'
                        """,
                        (resultSet, rowNumber) ->
                                new RetrievalIndexWorkContext(
                                        resultSet.getObject("job_id", UUID.class),
                                        resultSet.getObject("index_id", UUID.class),
                                        resultSet.getObject("document_id", UUID.class),
                                        resultSet.getObject("document_version_id", UUID.class),
                                        resultSet.getObject("organization_id", UUID.class),
                                        resultSet.getObject("workspace_id", UUID.class),
                                        resultSet.getInt("generation"),
                                        resultSet.getString("embedding_provider"),
                                        resultSet.getString("embedding_model"),
                                        resultSet.getObject("embedding_dimension", Integer.class)),
                        jobId);
        if (contexts.size() != 1) {
            throw new RetrievalIndexingException(
                    RetrievalFailureCode.INTERNAL_INDEXING_ERROR,
                    "The retrieval job is no longer eligible for indexing",
                    false);
        }
        return contexts.getFirst();
    }

    @Transactional(readOnly = true)
    public List<SourceTextUnit> loadSourceTextUnits(RetrievalIndexWorkContext context) {
        return jdbcTemplate.query(
                """
                SELECT id, ordinal, locator_type, locator_value, text_content
                FROM document_text_units
                WHERE document_version_id = ?
                  AND organization_id = ?
                  AND workspace_id = ?
                ORDER BY ordinal
                """,
                (resultSet, rowNumber) ->
                        new SourceTextUnit(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getInt("ordinal"),
                                resultSet.getString("locator_type"),
                                resultSet.getString("locator_value"),
                                resultSet.getString("text_content")),
                context.documentVersionId(),
                context.organizationId(),
                context.workspaceId());
    }

    @Transactional
    public void complete(
            RetrievalIndexWorkContext context,
            List<RetrievalChunkDraft> chunks,
            List<float[]> embeddings,
            Instant completedAt) {
        requireProcessing(context);
        jdbcTemplate.update(
                "DELETE FROM retrieval_chunks WHERE retrieval_index_id = ?",
                context.retrievalIndexId());
        for (int offset = 0; offset < chunks.size(); offset++) {
            RetrievalChunkDraft chunk = chunks.get(offset);
            float[] embedding = embeddings.isEmpty() ? null : embeddings.get(offset);
            insertChunk(context, chunk, embedding);
        }
        jdbcTemplate.update(
                """
                UPDATE retrieval_indexes AS older
                SET status = 'SUPERSEDED',
                    ready_at = NULL,
                    superseded_at = ?
                WHERE older.document_id = ?
                  AND older.organization_id = ?
                  AND older.workspace_id = ?
                  AND older.id <> ?
                  AND older.status = 'READY'
                """,
                Timestamp.from(completedAt),
                context.documentId(),
                context.organizationId(),
                context.workspaceId(),
                context.retrievalIndexId());
        int indexUpdates =
                jdbcTemplate.update(
                        """
                        UPDATE retrieval_indexes
                        SET status = 'READY',
                            ready_at = ?,
                            superseded_at = NULL,
                            failure_code = NULL,
                            failure_message = NULL
                        WHERE id = ? AND status = 'PROCESSING'
                        """,
                        Timestamp.from(completedAt),
                        context.retrievalIndexId());
        int jobUpdates =
                jdbcTemplate.update(
                        """
                        UPDATE retrieval_index_jobs
                        SET status = 'COMPLETED',
                            claimed_at = NULL,
                            completed_at = ?,
                            last_error_code = NULL,
                            last_error_message = NULL,
                            updated_at = ?
                        WHERE id = ? AND status = 'PROCESSING'
                        """,
                        Timestamp.from(completedAt),
                        Timestamp.from(completedAt),
                        context.jobId());
        if (indexUpdates != 1 || jobUpdates != 1) {
            throw new IllegalStateException("Retrieval index completion lost its claimed state");
        }
    }

    @Transactional
    public void fail(
            RetrievalIndexWorkContext context,
            RetrievalFailureCode failureCode,
            String safeMessage,
            boolean retryable,
            Instant failedAt) {
        Integer attemptCount =
                jdbcTemplate.queryForObject(
                        "SELECT attempt_count FROM retrieval_index_jobs WHERE id = ?",
                        Integer.class,
                        context.jobId());
        boolean reschedule = retryable && attemptCount < RetrievalIndexJobClaimer.MAX_ATTEMPTS;
        if (reschedule) {
            jdbcTemplate.update(
                    """
                    UPDATE retrieval_indexes
                    SET status = 'QUEUED', failure_code = NULL, failure_message = NULL
                    WHERE id = ?
                    """,
                    context.retrievalIndexId());
            jdbcTemplate.update(
                    """
                    UPDATE retrieval_index_jobs
                    SET status = 'QUEUED',
                        claimed_at = NULL,
                        next_attempt_at = ?,
                        last_error_code = ?,
                        last_error_message = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(failedAt.plusSeconds(properties.retryDelaySeconds())),
                    failureCode.name(),
                    safeMessage,
                    Timestamp.from(failedAt),
                    context.jobId());
        } else {
            jdbcTemplate.update(
                    """
                    UPDATE retrieval_indexes
                    SET status = 'FAILED', failure_code = ?, failure_message = ?
                    WHERE id = ?
                    """,
                    failureCode.name(),
                    safeMessage,
                    context.retrievalIndexId());
            jdbcTemplate.update(
                    """
                    UPDATE retrieval_index_jobs
                    SET status = 'FAILED',
                        claimed_at = NULL,
                        completed_at = ?,
                        last_error_code = ?,
                        last_error_message = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(failedAt),
                    failureCode.name(),
                    safeMessage,
                    Timestamp.from(failedAt),
                    context.jobId());
        }
    }

    private void requireProcessing(RetrievalIndexWorkContext context) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM retrieval_index_jobs AS job
                        JOIN retrieval_indexes AS index ON index.id = job.retrieval_index_id
                        WHERE job.id = ?
                          AND index.id = ?
                          AND job.status = 'PROCESSING'
                          AND index.status = 'PROCESSING'
                        """,
                        Integer.class,
                        context.jobId(),
                        context.retrievalIndexId());
        if (count == null || count != 1) {
            throw new IllegalStateException("Retrieval index is not processing");
        }
    }

    private void insertChunk(
            RetrievalIndexWorkContext context, RetrievalChunkDraft chunk, float[] embedding) {
        jdbcTemplate.update(
                connection -> {
                    var statement =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO retrieval_chunks (
                                        id, retrieval_index_id, document_id, document_version_id,
                                        organization_id, workspace_id, source_text_unit_id, ordinal,
                                        locator_type, locator_value, start_character, end_character,
                                        text_content, character_count, embedding, embedding_provider,
                                        embedding_model, embedding_dimension
                                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                        CAST(? AS vector), ?, ?, ?)
                                    """);
                    statement.setObject(1, chunk.id());
                    statement.setObject(2, context.retrievalIndexId());
                    statement.setObject(3, context.documentId());
                    statement.setObject(4, context.documentVersionId());
                    statement.setObject(5, context.organizationId());
                    statement.setObject(6, context.workspaceId());
                    statement.setObject(7, chunk.sourceTextUnitId());
                    statement.setInt(8, chunk.ordinal());
                    statement.setString(9, chunk.locatorType());
                    statement.setString(10, chunk.locatorValue());
                    statement.setInt(11, chunk.startCharacter());
                    statement.setInt(12, chunk.endCharacter());
                    statement.setString(13, chunk.text());
                    statement.setInt(14, chunk.endCharacter() - chunk.startCharacter());
                    if (embedding == null) {
                        statement.setNull(15, Types.VARCHAR);
                        statement.setNull(16, Types.VARCHAR);
                        statement.setNull(17, Types.VARCHAR);
                        statement.setNull(18, Types.INTEGER);
                    } else {
                        statement.setString(15, serialize(embedding));
                        statement.setString(16, context.embeddingProvider());
                        statement.setString(17, context.embeddingModel());
                        statement.setInt(18, context.embeddingDimension());
                    }
                    return statement;
                });
    }

    private String serialize(float[] embedding) {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(Float.toString(embedding[index]));
        }
        return value.append(']').toString();
    }
}
