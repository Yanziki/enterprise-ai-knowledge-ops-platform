package io.github.yanziki.enterpriseai.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.retrieval")
public record RetrievalProperties(
        int chunkSize,
        int chunkOverlap,
        int maxChunksPerVersion,
        int pollIntervalMs,
        int claimBatchSize,
        int retryDelaySeconds,
        int staleClaimSeconds,
        int maxQueryCharacters,
        int maxTopK,
        int candidateLimit,
        int embeddingBatchSize,
        String embeddingMode) {

    public RetrievalProperties {
        if (chunkSize < 100
                || chunkOverlap < 0
                || chunkOverlap >= chunkSize
                || maxChunksPerVersion < 1
                || pollIntervalMs < 100
                || claimBatchSize < 1
                || retryDelaySeconds < 0
                || staleClaimSeconds < 10
                || maxQueryCharacters < 1
                || maxTopK < 1
                || candidateLimit < maxTopK
                || embeddingBatchSize < 1) {
            throw new IllegalArgumentException("Invalid retrieval configuration");
        }
    }
}
