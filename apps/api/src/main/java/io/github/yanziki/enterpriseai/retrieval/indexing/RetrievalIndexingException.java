package io.github.yanziki.enterpriseai.retrieval.indexing;

import io.github.yanziki.enterpriseai.retrieval.RetrievalFailureCode;

public class RetrievalIndexingException extends RuntimeException {

    private final RetrievalFailureCode failureCode;
    private final boolean retryable;

    public RetrievalIndexingException(
            RetrievalFailureCode failureCode, String safeMessage, boolean retryable) {
        super(safeMessage);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public RetrievalFailureCode failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
