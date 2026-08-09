package io.github.yanziki.enterpriseai.knowledge.storage;

public class UploadLimitExceededException extends RuntimeException {

    public UploadLimitExceededException(long maximumBytes) {
        super("Upload exceeds the maximum of " + maximumBytes + " bytes");
    }
}
