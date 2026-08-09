package io.github.yanziki.enterpriseai.knowledge.ingestion;

import java.util.UUID;

public record JobExecutionContext(
        UUID jobId,
        UUID documentVersionId,
        UUID organizationId,
        UUID workspaceId,
        String objectKey,
        String detectedContentType) {}
