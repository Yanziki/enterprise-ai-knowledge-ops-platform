package io.github.yanziki.enterpriseai.retrieval.indexing;

import io.github.yanziki.enterpriseai.retrieval.RetrievalFailureCode;
import io.github.yanziki.enterpriseai.retrieval.RetrievalProperties;
import io.github.yanziki.enterpriseai.retrieval.chunking.ProvenanceChunker;
import io.github.yanziki.enterpriseai.retrieval.chunking.RetrievalChunkDraft;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingProvider;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingProviderRegistry;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingValidation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetrievalIndexProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrievalIndexProcessor.class);
    private final RetrievalIndexLifecycleService lifecycleService;
    private final ProvenanceChunker chunker;
    private final EmbeddingProviderRegistry providerRegistry;
    private final RetrievalProperties properties;

    public RetrievalIndexProcessor(
            RetrievalIndexLifecycleService lifecycleService,
            ProvenanceChunker chunker,
            EmbeddingProviderRegistry providerRegistry,
            RetrievalProperties properties) {
        this.lifecycleService = lifecycleService;
        this.chunker = chunker;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
    }

    public void process(UUID jobId) {
        RetrievalIndexWorkContext context = lifecycleService.begin(jobId);
        long startedAt = System.nanoTime();
        try {
            List<RetrievalChunkDraft> chunks =
                    chunker.chunk(
                            context.retrievalIndexId(),
                            lifecycleService.loadSourceTextUnits(context));
            List<float[]> embeddings = embed(context, chunks);
            lifecycleService.complete(context, chunks, embeddings, Instant.now());
            LOGGER.info(
                    "retrieval_index_ready organizationId={} workspaceId={} documentId={} versionId={} indexId={} generation={} jobId={} status=READY chunks={} embeddingProvider={} embeddingModel={} durationMs={}",
                    context.organizationId(),
                    context.workspaceId(),
                    context.documentId(),
                    context.documentVersionId(),
                    context.retrievalIndexId(),
                    context.generation(),
                    context.jobId(),
                    chunks.size(),
                    context.embeddingProvider(),
                    context.embeddingModel(),
                    elapsedMillis(startedAt));
        } catch (RetrievalIndexingException exception) {
            lifecycleService.fail(
                    context,
                    exception.failureCode(),
                    exception.getMessage(),
                    exception.retryable(),
                    Instant.now());
            LOGGER.warn(
                    "retrieval_index_failed organizationId={} workspaceId={} documentId={} versionId={} indexId={} generation={} jobId={} status=FAILED code={} durationMs={}",
                    context.organizationId(),
                    context.workspaceId(),
                    context.documentId(),
                    context.documentVersionId(),
                    context.retrievalIndexId(),
                    context.generation(),
                    context.jobId(),
                    exception.failureCode(),
                    elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            lifecycleService.fail(
                    context,
                    RetrievalFailureCode.INTERNAL_INDEXING_ERROR,
                    "The document could not be indexed",
                    true,
                    Instant.now());
            LOGGER.warn(
                    "retrieval_index_failed organizationId={} workspaceId={} documentId={} versionId={} indexId={} generation={} jobId={} status=FAILED code={} durationMs={}",
                    context.organizationId(),
                    context.workspaceId(),
                    context.documentId(),
                    context.documentVersionId(),
                    context.retrievalIndexId(),
                    context.generation(),
                    context.jobId(),
                    RetrievalFailureCode.INTERNAL_INDEXING_ERROR,
                    elapsedMillis(startedAt),
                    exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private List<float[]> embed(
            RetrievalIndexWorkContext context, List<RetrievalChunkDraft> chunks) {
        if (!context.embeddingsEnabled()) {
            return List.of();
        }
        EmbeddingProvider provider =
                providerRegistry
                        .activeProvider()
                        .filter(
                                candidate ->
                                        candidate.providerId().equals(context.embeddingProvider())
                                                && candidate
                                                        .modelId()
                                                        .equals(context.embeddingModel())
                                                && candidate.dimension()
                                                        == context.embeddingDimension())
                        .orElseThrow(
                                () ->
                                        new RetrievalIndexingException(
                                                RetrievalFailureCode.EMBEDDING_PROVIDER_UNAVAILABLE,
                                                "The configured embedding provider is unavailable",
                                                true));
        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += properties.embeddingBatchSize()) {
            int end = Math.min(start + properties.embeddingBatchSize(), chunks.size());
            List<String> texts =
                    chunks.subList(start, end).stream().map(RetrievalChunkDraft::text).toList();
            List<float[]> batch = provider.embedDocuments(texts);
            EmbeddingValidation.validateBatch(batch, texts.size(), provider.dimension());
            embeddings.addAll(batch);
        }
        return List.copyOf(embeddings);
    }
}
