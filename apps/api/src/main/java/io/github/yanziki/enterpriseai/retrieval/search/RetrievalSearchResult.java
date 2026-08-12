package io.github.yanziki.enterpriseai.retrieval.search;

import java.util.UUID;

public record RetrievalSearchResult(
        UUID chunkId,
        int rank,
        double score,
        Double lexicalScore,
        Double vectorScore,
        RetrievalCitation citation) {}
