package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.DocumentVersion;
import io.github.yanziki.enterpriseai.knowledge.KnowledgeDocument;

public record PersistedUpload(KnowledgeDocument document, DocumentVersion version) {}
