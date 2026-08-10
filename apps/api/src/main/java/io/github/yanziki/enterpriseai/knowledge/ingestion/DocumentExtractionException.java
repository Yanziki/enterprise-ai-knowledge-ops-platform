package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.IngestionFailureCode;

public class DocumentExtractionException extends RuntimeException {

    private final IngestionFailureCode failureCode;

    public DocumentExtractionException(IngestionFailureCode failureCode, String safeMessage) {
        super(safeMessage);
        this.failureCode = failureCode;
    }

    public DocumentExtractionException(
            IngestionFailureCode failureCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.failureCode = failureCode;
    }

    public IngestionFailureCode failureCode() {
        return failureCode;
    }
}
