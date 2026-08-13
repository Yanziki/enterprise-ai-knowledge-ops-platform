package io.github.yanziki.enterpriseai.answer;

import io.github.yanziki.enterpriseai.retrieval.search.RetrievalSearchResult;

public record GroundedEvidence(String citationId, String content, RetrievalSearchResult source) {}
