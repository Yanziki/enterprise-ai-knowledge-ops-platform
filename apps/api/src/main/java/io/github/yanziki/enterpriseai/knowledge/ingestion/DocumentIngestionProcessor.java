package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.IngestionFailureCode;
import io.github.yanziki.enterpriseai.knowledge.storage.ObjectStorage;
import io.github.yanziki.enterpriseai.knowledge.storage.ObjectStorageException;
import io.github.yanziki.enterpriseai.knowledge.storage.StoredObject;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIngestionProcessor.class);

    private final IngestionLifecycleService lifecycleService;
    private final ObjectStorage objectStorage;
    private final DocumentContentExtractor contentExtractor;

    public DocumentIngestionProcessor(
            IngestionLifecycleService lifecycleService,
            ObjectStorage objectStorage,
            DocumentContentExtractor contentExtractor) {
        this.lifecycleService = lifecycleService;
        this.objectStorage = objectStorage;
        this.contentExtractor = contentExtractor;
    }

    public void process(UUID jobId) {
        JobExecutionContext context = lifecycleService.begin(jobId);
        LOGGER.info(
                "ingestion_started organizationId={} workspaceId={} versionId={} jobId={}",
                context.organizationId(),
                context.workspaceId(),
                context.documentVersionId(),
                context.jobId());
        try {
            ExtractionResult result;
            try (StoredObject storedObject = objectStorage.get(context.objectKey())) {
                result = contentExtractor.extract(storedObject, context.detectedContentType());
            }
            lifecycleService.complete(context, result, Instant.now());
            LOGGER.info(
                    "ingestion_ready organizationId={} workspaceId={} versionId={} jobId={} units={}",
                    context.organizationId(),
                    context.workspaceId(),
                    context.documentVersionId(),
                    context.jobId(),
                    result.textUnits().size());
        } catch (DocumentExtractionException exception) {
            boolean retryable = exception.failureCode() == IngestionFailureCode.STORAGE_FAILURE;
            lifecycleService.fail(
                    context,
                    exception.failureCode(),
                    exception.getMessage(),
                    retryable,
                    Instant.now());
            LOGGER.warn(
                    "ingestion_failed organizationId={} workspaceId={} versionId={} jobId={} code={}",
                    context.organizationId(),
                    context.workspaceId(),
                    context.documentVersionId(),
                    context.jobId(),
                    exception.failureCode(),
                    exception);
        } catch (ObjectStorageException exception) {
            lifecycleService.fail(
                    context,
                    IngestionFailureCode.STORAGE_FAILURE,
                    "The stored document could not be retrieved",
                    true,
                    Instant.now());
            LOGGER.warn(
                    "ingestion_failed organizationId={} workspaceId={} versionId={} jobId={} code={}",
                    context.organizationId(),
                    context.workspaceId(),
                    context.documentVersionId(),
                    context.jobId(),
                    IngestionFailureCode.STORAGE_FAILURE,
                    exception);
        } catch (Exception exception) {
            lifecycleService.fail(
                    context,
                    IngestionFailureCode.INTERNAL_PROCESSING_ERROR,
                    "Document processing failed unexpectedly",
                    true,
                    Instant.now());
            LOGGER.error(
                    "ingestion_failed organizationId={} workspaceId={} versionId={} jobId={} code={}",
                    context.organizationId(),
                    context.workspaceId(),
                    context.documentVersionId(),
                    context.jobId(),
                    IngestionFailureCode.INTERNAL_PROCESSING_ERROR,
                    exception);
        }
    }
}
