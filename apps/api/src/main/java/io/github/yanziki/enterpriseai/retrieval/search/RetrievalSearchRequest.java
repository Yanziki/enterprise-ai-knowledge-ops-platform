package io.github.yanziki.enterpriseai.retrieval.search;

import io.github.yanziki.enterpriseai.retrieval.RetrievalMode;

public record RetrievalSearchRequest(String query, RetrievalMode mode, Integer topK) {}
