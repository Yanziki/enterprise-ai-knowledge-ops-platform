package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.storage.StagedUpload;

public record ValidatedUpload(
        String originalFilename,
        String declaredContentType,
        String detectedContentType,
        StagedUpload staged)
        implements AutoCloseable {

    @Override
    public void close() {
        staged.close();
    }
}
