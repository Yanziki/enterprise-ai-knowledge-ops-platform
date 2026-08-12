package io.github.yanziki.enterpriseai.retrieval.chunking;

import java.util.UUID;

public record RetrievalChunkDraft(
        UUID id,
        UUID sourceTextUnitId,
        int ordinal,
        String locatorType,
        String locatorValue,
        int startCharacter,
        int endCharacter,
        String text) {}
