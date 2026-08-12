package io.github.yanziki.enterpriseai.retrieval.indexing;

import java.util.UUID;

public record RetrievalIndexWorkContext(
        UUID jobId,
        UUID retrievalIndexId,
        UUID documentId,
        UUID documentVersionId,
        UUID organizationId,
        UUID workspaceId,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension) {

    public boolean embeddingsEnabled() {
        return embeddingProvider != null;
    }
}
