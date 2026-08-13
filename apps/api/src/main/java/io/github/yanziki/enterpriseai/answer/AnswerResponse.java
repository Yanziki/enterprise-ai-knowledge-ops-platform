package io.github.yanziki.enterpriseai.answer;

import io.github.yanziki.enterpriseai.retrieval.RetrievalMode;
import java.util.List;
import java.util.UUID;

public record AnswerResponse(
        UUID requestId,
        AnswerStatus status,
        String answer,
        RetrievalMode requestedRetrievalMode,
        RetrievalMode effectiveRetrievalMode,
        int retrievedChunkCount,
        int contextCharacters,
        String provider,
        String model,
        List<AnswerCitation> citations) {}
