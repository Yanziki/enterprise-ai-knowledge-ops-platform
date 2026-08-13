package io.github.yanziki.enterpriseai.answer;

import io.github.yanziki.enterpriseai.retrieval.RetrievalMode;

public record AnswerRequest(String question, RetrievalMode retrievalMode, Integer retrievalTopK) {}
