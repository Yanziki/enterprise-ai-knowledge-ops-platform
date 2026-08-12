package io.github.yanziki.enterpriseai.retrieval.search;

import io.github.yanziki.enterpriseai.retrieval.RetrievalMode;
import java.util.List;

public record RetrievalSearchResponse(
        String query,
        RetrievalMode requestedMode,
        RetrievalMode effectiveMode,
        int topK,
        List<RetrievalSearchResult> results) {}
