package io.github.yanziki.enterpriseai.retrieval.indexing;

import io.github.yanziki.enterpriseai.retrieval.RetrievalProperties;
import io.github.yanziki.enterpriseai.retrieval.chunking.ProvenanceChunker;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingProvider;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingProviderRegistry;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RetrievalIndexReconciler {

    private final JdbcTemplate jdbcTemplate;
    private final ProvenanceChunker chunker;
    private final EmbeddingProviderRegistry providerRegistry;
    private final RetrievalProperties properties;

    public RetrievalIndexReconciler(
            JdbcTemplate jdbcTemplate,
            ProvenanceChunker chunker,
            EmbeddingProviderRegistry providerRegistry,
            RetrievalProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunker = chunker;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
    }

    @Transactional
    public int enqueueMissingReadyVersions() {
        Optional<EmbeddingProvider> provider = providerRegistry.activeProvider();
        String providerId = provider.map(EmbeddingProvider::providerId).orElse(null);
        String modelId = provider.map(EmbeddingProvider::modelId).orElse(null);
        Integer dimension = provider.map(EmbeddingProvider::dimension).orElse(null);
        return jdbcTemplate.update(
                """
                WITH candidates AS (
                    SELECT version.id AS version_id,
                           version.document_id,
                           version.organization_id,
                           version.workspace_id
                    FROM document_versions AS version
                    JOIN documents AS document
                      ON document.id = version.document_id
                     AND document.organization_id = version.organization_id
                     AND document.workspace_id = version.workspace_id
                    WHERE version.ingestion_status = 'READY'
                      AND document.status = 'ACTIVE'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM retrieval_indexes AS existing
                          WHERE existing.document_version_id = version.id
                            AND existing.generation = 1
                      )
                    ORDER BY version.created_at, version.id
                    LIMIT ?
                ), inserted_indexes AS (
                    INSERT INTO retrieval_indexes (
                        id, document_id, document_version_id, organization_id, workspace_id,
                        generation, status, chunker_name, chunker_version, chunk_size,
                        chunk_overlap, embedding_provider, embedding_model, embedding_dimension
                    )
                    SELECT CAST(md5(candidate.version_id::text || ':retrieval-index:1') AS uuid),
                           candidate.document_id,
                           candidate.version_id,
                           candidate.organization_id,
                           candidate.workspace_id,
                           1,
                           'QUEUED',
                           ?,
                           ?,
                           ?,
                           ?,
                           ?,
                           ?,
                           ?
                    FROM candidates AS candidate
                    ON CONFLICT (document_version_id, generation) DO NOTHING
                    RETURNING id, document_version_id, organization_id, workspace_id
                )
                INSERT INTO retrieval_index_jobs (
                    id, retrieval_index_id, document_version_id, organization_id, workspace_id,
                    status
                )
                SELECT CAST(md5(index.id::text || ':retrieval-job') AS uuid),
                       index.id,
                       index.document_version_id,
                       index.organization_id,
                       index.workspace_id,
                       'QUEUED'
                FROM inserted_indexes AS index
                ON CONFLICT (retrieval_index_id) DO NOTHING
                """,
                properties.claimBatchSize(),
                chunker.name(),
                chunker.version(),
                chunker.chunkSize(),
                chunker.overlap(),
                providerId,
                modelId,
                dimension);
    }
}
