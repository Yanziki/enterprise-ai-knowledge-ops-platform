package io.github.yanziki.enterpriseai.answer;

public record LanguageModelRequest(
        String systemPolicy, String userQuestion, String retrievedEvidence, int maxOutputTokens) {}
