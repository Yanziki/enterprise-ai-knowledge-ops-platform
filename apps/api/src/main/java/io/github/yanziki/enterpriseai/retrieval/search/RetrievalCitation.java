package io.github.yanziki.enterpriseai.retrieval.search;

import java.util.UUID;

public record RetrievalCitation(
        UUID documentId,
        String documentTitle,
        UUID documentVersionId,
        int versionNumber,
        String locatorType,
        String locatorValue,
        int startCharacter,
        int endCharacter,
        String snippet) {}
