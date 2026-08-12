package io.github.yanziki.enterpriseai.retrieval.search;

import java.util.UUID;

record RetrievalCandidate(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        UUID documentVersionId,
        int versionNumber,
        String locatorType,
        String locatorValue,
        int startCharacter,
        int endCharacter,
        String text,
        double componentScore) {}
