package io.github.yanziki.enterpriseai.retrieval.search;

import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RetrievalSearchRepository {

    private static final String AUTHORIZED_CURRENT_INDEX =
            """
            FROM retrieval_chunks AS chunk
            JOIN retrieval_indexes AS retrieval_index
              ON retrieval_index.id = chunk.retrieval_index_id
             AND retrieval_index.document_version_id = chunk.document_version_id
             AND retrieval_index.organization_id = chunk.organization_id
             AND retrieval_index.workspace_id = chunk.workspace_id
            JOIN document_versions AS version
              ON version.id = chunk.document_version_id
             AND version.document_id = chunk.document_id
             AND version.organization_id = chunk.organization_id
             AND version.workspace_id = chunk.workspace_id
            JOIN documents AS document
              ON document.id = chunk.document_id
             AND document.organization_id = chunk.organization_id
             AND document.workspace_id = chunk.workspace_id
            WHERE chunk.organization_id = ?
              AND chunk.workspace_id = ?
              AND document.status = 'ACTIVE'
              AND version.ingestion_status = 'READY'
              AND retrieval_index.status = 'READY'
            """;

    private final JdbcTemplate jdbcTemplate;

    RetrievalSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<RetrievalCandidate> lexical(
            UUID organizationId, UUID workspaceId, String query, int limit) {
        String authorizedScope =
                AUTHORIZED_CURRENT_INDEX.replace(
                        "WHERE chunk.organization_id = ?",
                        "CROSS JOIN (SELECT websearch_to_tsquery('simple', ?) AS query)"
                                + " AS search WHERE chunk.organization_id = ?");
        String sql =
                ("""
                SELECT chunk.id AS chunk_id,
                       chunk.document_id,
                       document.title,
                       chunk.document_version_id,
                       version.version_number,
                       chunk.locator_type,
                       chunk.locator_value,
                       chunk.start_character,
                       chunk.end_character,
                       chunk.text_content,
                       ts_rank_cd(chunk.lexical_document, search.query) AS component_score
                """
                        + authorizedScope
                        + """
                          AND chunk.lexical_document @@ search.query
                        ORDER BY component_score DESC, chunk.id
                        LIMIT ?
                        """);
        return jdbcTemplate.query(sql, this::candidate, query, organizationId, workspaceId, limit);
    }

    List<RetrievalCandidate> vector(
            UUID organizationId,
            UUID workspaceId,
            float[] queryVector,
            EmbeddingProvider provider,
            int limit) {
        String vector = serialize(queryVector);
        return jdbcTemplate.query(
                """
                SELECT chunk.id AS chunk_id,
                       chunk.document_id,
                       document.title,
                       chunk.document_version_id,
                       version.version_number,
                       chunk.locator_type,
                       chunk.locator_value,
                       chunk.start_character,
                       chunk.end_character,
                       chunk.text_content,
                       1 - (chunk.embedding <=> CAST(? AS vector)) AS component_score
                """
                        + AUTHORIZED_CURRENT_INDEX
                        + """
                          AND chunk.embedding IS NOT NULL
                          AND chunk.embedding_provider = ?
                          AND chunk.embedding_model = ?
                          AND chunk.embedding_dimension = ?
                        ORDER BY chunk.embedding <=> CAST(? AS vector), chunk.id
                        LIMIT ?
                        """,
                this::candidate,
                vector,
                organizationId,
                workspaceId,
                provider.providerId(),
                provider.modelId(),
                provider.dimension(),
                vector,
                limit);
    }

    boolean hasCompatibleVectors(
            UUID organizationId, UUID workspaceId, EmbeddingProvider provider) {
        Boolean present =
                jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 "
                                + AUTHORIZED_CURRENT_INDEX
                                + " AND chunk.embedding IS NOT NULL"
                                + " AND chunk.embedding_provider = ?"
                                + " AND chunk.embedding_model = ?"
                                + " AND chunk.embedding_dimension = ?)",
                        Boolean.class,
                        organizationId,
                        workspaceId,
                        provider.providerId(),
                        provider.modelId(),
                        provider.dimension());
        return Boolean.TRUE.equals(present);
    }

    private RetrievalCandidate candidate(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new RetrievalCandidate(
                resultSet.getObject("chunk_id", UUID.class),
                resultSet.getObject("document_id", UUID.class),
                resultSet.getString("title"),
                resultSet.getObject("document_version_id", UUID.class),
                resultSet.getInt("version_number"),
                resultSet.getString("locator_type"),
                resultSet.getString("locator_value"),
                resultSet.getInt("start_character"),
                resultSet.getInt("end_character"),
                resultSet.getString("text_content"),
                resultSet.getDouble("component_score"));
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
