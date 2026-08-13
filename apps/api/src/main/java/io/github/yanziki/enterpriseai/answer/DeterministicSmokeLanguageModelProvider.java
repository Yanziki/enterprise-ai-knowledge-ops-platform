package io.github.yanziki.enterpriseai.answer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile({"test", "local", "container"})
@ConditionalOnProperty(name = "app.answer.provider", havingValue = "deterministic-smoke")
public class DeterministicSmokeLanguageModelProvider implements LanguageModelProvider {

    public static final String PROVIDER = "deterministic-smoke";
    public static final String MODEL = "extractive-citation-v1-test-only";
    private final ObjectMapper objectMapper;

    public DeterministicSmokeLanguageModelProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }

    @Override
    public String modelId() {
        return MODEL;
    }

    @Override
    public LanguageModelCompletion generate(LanguageModelRequest request) {
        tools.jackson.databind.JsonNode items = objectMapper.readTree(request.retrievedEvidence());
        java.util.List<String> answers = new java.util.ArrayList<>();
        java.util.List<String> citationIds = new java.util.ArrayList<>();
        for (tools.jackson.databind.JsonNode item : items) {
            String content = item.path("content").asText();
            if (overlapsQuestion(request.userQuestion(), content)) {
                answers.add(content);
                citationIds.add(item.path("citationId").asText());
            }
        }
        if (answers.isEmpty()) {
            return completion(
                    new StructuredModelOutput(
                            AnswerStatus.INSUFFICIENT_EVIDENCE,
                            "The available authorized evidence is insufficient to answer this question.",
                            java.util.List.of()));
        }
        return completion(
                new StructuredModelOutput(
                        AnswerStatus.ANSWERED,
                        String.join(" ", answers),
                        java.util.List.copyOf(citationIds)));
    }

    private LanguageModelCompletion completion(StructuredModelOutput output) {
        return new LanguageModelCompletion(objectMapper.writeValueAsString(output), null, null);
    }

    private boolean overlapsQuestion(String question, String evidence) {
        java.util.Set<String> questionTerms = meaningfulTerms(question);
        java.util.Set<String> evidenceTerms = meaningfulTerms(evidence);
        questionTerms.retainAll(evidenceTerms);
        if (!questionTerms.isEmpty()) {
            return true;
        }
        String normalizedQuestion = question.strip();
        return normalizedQuestion.length() <= 64
                && evidence.toLowerCase(java.util.Locale.ROOT)
                        .contains(normalizedQuestion.toLowerCase(java.util.Locale.ROOT));
    }

    private java.util.Set<String> meaningfulTerms(String value) {
        java.util.Set<String> terms = new java.util.HashSet<>();
        for (String term : value.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (term.length() >= 4) {
                terms.add(term);
            }
        }
        return terms;
    }
}
