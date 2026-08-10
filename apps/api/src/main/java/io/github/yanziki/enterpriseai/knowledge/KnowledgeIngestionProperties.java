package io.github.yanziki.enterpriseai.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.ingestion")
public record KnowledgeIngestionProperties(
        @Min(1) long maxOriginalBytes,
        @Min(1) int maxExtractedCharacters,
        @Min(1) int maxPdfPages,
        @Min(100) long pollIntervalMs,
        @Min(1) @Max(32) int claimBatchSize,
        @Min(1) long retryDelaySeconds,
        @Min(30) long staleClaimSeconds) {}
