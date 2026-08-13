package io.github.yanziki.enterpriseai.answer;

public interface LanguageModelProvider {

    String providerId();

    String modelId();

    LanguageModelCompletion generate(LanguageModelRequest request);
}
