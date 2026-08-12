package io.github.yanziki.enterpriseai.retrieval.indexing;

import io.github.yanziki.enterpriseai.retrieval.RetrievalProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.retrieval.worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RetrievalIndexWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrievalIndexWorker.class);
    private final RetrievalIndexReconciler reconciler;
    private final RetrievalIndexJobClaimer jobClaimer;
    private final RetrievalIndexProcessor processor;
    private final RetrievalProperties properties;

    public RetrievalIndexWorker(
            RetrievalIndexReconciler reconciler,
            RetrievalIndexJobClaimer jobClaimer,
            RetrievalIndexProcessor processor,
            RetrievalProperties properties) {
        this.reconciler = reconciler;
        this.jobClaimer = jobClaimer;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.retrieval.poll-interval-ms:750}")
    public void poll() {
        try {
            Instant now = Instant.now();
            jobClaimer.recoverStaleClaims(
                    now.minus(properties.staleClaimSeconds(), ChronoUnit.SECONDS), now);
            reconciler.enqueueMissingReadyVersions();
            for (UUID jobId : jobClaimer.claimNextBatch(properties.claimBatchSize(), now)) {
                try {
                    processor.process(jobId);
                } catch (RuntimeException exception) {
                    LOGGER.error("retrieval_index_worker_error jobId={}", jobId, exception);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "retrieval_index_poll_unavailable type={}",
                    exception.getClass().getSimpleName());
        }
    }
}
