package io.github.yanziki.enterpriseai.retrieval;

import java.net.URI;
import java.time.Duration;
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
        String embeddingMode,
        OpenAiEmbeddingProperties openai) {

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
                || embeddingBatchSize < 1
                || embeddingMode == null
                || !embeddingMode.matches("none|deterministic-smoke|openai-compatible")) {
            throw new IllegalArgumentException("Invalid retrieval configuration");
        }
        if (openai == null) {
            openai =
                    new OpenAiEmbeddingProperties(
                            URI.create("https://api.openai.com/v1/"), "", "", 0, false, 30000);
        }
    }

    public record OpenAiEmbeddingProperties(
            URI baseUrl,
            String model,
            String apiKey,
            int dimension,
            boolean sendDimensions,
            int timeoutMillis) {

        public OpenAiEmbeddingProperties {
            if (baseUrl == null || !baseUrl.isAbsolute()) {
                throw new IllegalArgumentException(
                        "The OpenAI-compatible embedding base URL must be absolute");
            }
            if (dimension < 0 || dimension > 16000 || timeoutMillis < 100) {
                throw new IllegalArgumentException("Invalid embedding provider configuration");
            }
            model = model == null ? "" : model.strip();
            apiKey = apiKey == null ? "" : apiKey;
        }

        public Duration timeout() {
            return Duration.ofMillis(timeoutMillis);
        }
    }
}
