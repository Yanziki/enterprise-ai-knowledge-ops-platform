package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionJob;
import io.github.yanziki.enterpriseai.knowledge.DocumentIngestionJobRepository;
import io.github.yanziki.enterpriseai.knowledge.DocumentTextUnit;
import io.github.yanziki.enterpriseai.knowledge.DocumentTextUnitRepository;
import io.github.yanziki.enterpriseai.knowledge.DocumentVersion;
import io.github.yanziki.enterpriseai.knowledge.DocumentVersionRepository;
import io.github.yanziki.enterpriseai.knowledge.IngestionFailureCode;
import io.github.yanziki.enterpriseai.knowledge.IngestionJobStatus;
import io.github.yanziki.enterpriseai.knowledge.KnowledgeIngestionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionLifecycleService {

    private final DocumentIngestionJobRepository jobRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentTextUnitRepository textUnitRepository;
    private final KnowledgeIngestionProperties properties;

    public IngestionLifecycleService(
            DocumentIngestionJobRepository jobRepository,
            DocumentVersionRepository versionRepository,
            DocumentTextUnitRepository textUnitRepository,
            KnowledgeIngestionProperties properties) {
        this.jobRepository = jobRepository;
        this.versionRepository = versionRepository;
        this.textUnitRepository = textUnitRepository;
        this.properties = properties;
    }

    @Transactional
    public JobExecutionContext begin(UUID jobId) {
        DocumentIngestionJob job = requiredJob(jobId);
        if (job.getStatus() != IngestionJobStatus.PROCESSING) {
            throw new IllegalStateException("Only a claimed job can begin processing");
        }
        DocumentVersion version = requiredVersion(job.getDocumentVersionId());
        version.startProcessing();
        return new JobExecutionContext(
                job.getId(),
                version.getId(),
                version.getOrganizationId(),
                version.getWorkspaceId(),
                version.getObjectKey(),
                version.getDetectedContentType());
    }

    @Transactional
    public void complete(
            JobExecutionContext context, ExtractionResult result, Instant completedAt) {
        DocumentIngestionJob job = requiredJob(context.jobId());
        DocumentVersion version = requiredVersion(context.documentVersionId());
        textUnitRepository.deleteAllByDocumentVersionId(version.getId());
        List<DocumentTextUnit> units =
                result.textUnits().stream()
                        .map(
                                extracted ->
                                        new DocumentTextUnit(
                                                UUID.randomUUID(),
                                                version.getId(),
                                                version.getOrganizationId(),
                                                version.getWorkspaceId(),
                                                extracted.ordinal(),
                                                extracted.locatorType(),
                                                extracted.locatorValue(),
                                                extracted.text(),
                                                completedAt))
                        .toList();
        textUnitRepository.saveAll(units);
        version.markReady(result.parserName(), result.parserVersion(), completedAt);
        job.complete(completedAt);
    }

    @Transactional
    public void fail(
            JobExecutionContext context,
            IngestionFailureCode failureCode,
            String safeMessage,
            boolean retryable,
            Instant failedAt) {
        DocumentIngestionJob job = requiredJob(context.jobId());
        DocumentVersion version = requiredVersion(context.documentVersionId());
        if (retryable && job.getAttemptCount() < DocumentIngestionJob.MAX_ATTEMPTS) {
            job.reschedule(
                    failureCode,
                    safeMessage,
                    failedAt,
                    Duration.ofSeconds(properties.retryDelaySeconds()));
            version.reschedule();
        } else {
            job.fail(failureCode, safeMessage, failedAt);
            version.markFailed(failureCode, safeMessage);
        }
    }

    private DocumentIngestionJob requiredJob(UUID jobId) {
        return jobRepository
                .findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Ingestion job was not found"));
    }

    private DocumentVersion requiredVersion(UUID versionId) {
        return versionRepository
                .findById(versionId)
                .orElseThrow(() -> new IllegalStateException("Document version was not found"));
    }
}
