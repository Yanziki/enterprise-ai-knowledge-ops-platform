package io.github.yanziki.enterpriseai.answer;

import java.util.UUID;

public record AnswerCitation(
        String citationId,
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        UUID documentVersionId,
        int versionNumber,
        String locatorType,
        String locatorValue,
        int startCharacter,
        int endCharacter,
        String snippet) {}
