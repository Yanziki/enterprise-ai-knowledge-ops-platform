package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.KnowledgeIngestionProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentIngestionWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIngestionWorker.class);

    private final IngestionJobClaimer jobClaimer;
    private final DocumentIngestionProcessor processor;
    private final KnowledgeIngestionProperties properties;

    public DocumentIngestionWorker(
            IngestionJobClaimer jobClaimer,
            DocumentIngestionProcessor processor,
            KnowledgeIngestionProperties properties) {
        this.jobClaimer = jobClaimer;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.ingestion.poll-interval-ms:500}")
    public void poll() {
        try {
            Instant now = Instant.now();
            jobClaimer.recoverStaleClaims(
                    now.minus(properties.staleClaimSeconds(), ChronoUnit.SECONDS), now);
            for (UUID jobId : jobClaimer.claimNextBatch(properties.claimBatchSize(), now)) {
                try {
                    processor.process(jobId);
                } catch (RuntimeException exception) {
                    LOGGER.error("ingestion_worker_error jobId={}", jobId, exception);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("ingestion_poll_unavailable type={}", exception.getClass().getSimpleName());
        }
    }
}
