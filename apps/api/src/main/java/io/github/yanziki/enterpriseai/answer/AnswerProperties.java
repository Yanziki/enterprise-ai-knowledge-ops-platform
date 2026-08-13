package io.github.yanziki.enterpriseai.answer;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.answer")
public record AnswerProperties(
        String provider,
        int maxQuestionCharacters,
        int maxEvidenceChunks,
        int maxEvidenceCharacters,
        int maxContextCharacters,
        int maxAnswerCharacters,
        int maxOutputTokens,
        int timeoutMillis,
        OpenAiProperties openai) {

    public AnswerProperties {
        if (provider == null
                || !provider.matches("none|deterministic-smoke|openai-compatible")
                || maxQuestionCharacters < 1
                || maxEvidenceChunks < 1
                || maxEvidenceCharacters < 64
                || maxContextCharacters < maxEvidenceCharacters
                || maxAnswerCharacters < 1
                || maxOutputTokens < 1
                || timeoutMillis < 100) {
            throw new IllegalArgumentException("Invalid answer-generation configuration");
        }
        if (openai == null) {
            openai = new OpenAiProperties(URI.create("https://api.openai.com/v1/"), "", "");
        }
    }

    public Duration timeout() {
        return Duration.ofMillis(timeoutMillis);
    }

    public record OpenAiProperties(URI baseUrl, String model, String apiKey) {
        public OpenAiProperties {
            if (baseUrl == null || !baseUrl.isAbsolute()) {
                throw new IllegalArgumentException(
                        "The OpenAI-compatible base URL must be absolute");
            }
            model = model == null ? "" : model.strip();
            apiKey = apiKey == null ? "" : apiKey;
        }
    }
}
