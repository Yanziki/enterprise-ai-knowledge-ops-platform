package io.github.yanziki.enterpriseai.answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class StructuredAnswerValidator {

    private final ObjectMapper objectMapper;
    private final AnswerProperties properties;

    public StructuredAnswerValidator(ObjectMapper objectMapper, AnswerProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ValidatedAnswer validate(String content, AssembledContext context) {
        StructuredModelOutput output;
        try {
            output = objectMapper.readValue(content, StructuredModelOutput.class);
        } catch (RuntimeException exception) {
            throw new CitationValidationException(
                    "MALFORMED_OUTPUT", "The language model returned invalid structured output");
        }
        if (output.status() == null || output.citationIds() == null) {
            throw new CitationValidationException(
                    "MALFORMED_OUTPUT", "The language model returned incomplete structured output");
        }
        if (output.status() == AnswerStatus.INSUFFICIENT_EVIDENCE) {
            if (!output.citationIds().isEmpty()) {
                throw new CitationValidationException(
                        "ABSTENTION_WITH_CITATIONS",
                        "An insufficient-evidence response cannot include citations");
            }
            return new ValidatedAnswer(
                    output.status(),
                    "The available authorized evidence is insufficient to answer this question.",
                    List.of());
        }
        if (output.answer() == null
                || output.answer().isBlank()
                || output.citationIds().isEmpty()) {
            throw new CitationValidationException(
                    "UNGROUNDED_ANSWER", "An answered response must contain evidence citations");
        }

        Map<String, GroundedEvidence> byAlias = new HashMap<>();
        context.evidence().forEach(item -> byAlias.put(item.citationId(), item));
        Set<String> seen = new HashSet<>();
        List<GroundedEvidence> cited = new ArrayList<>();
        for (String citationId : output.citationIds()) {
            if (citationId == null || !citationId.matches("C[1-9][0-9]*")) {
                throw new CitationValidationException(
                        "MALFORMED_CITATION",
                        "The language model returned an invalid citation alias");
            }
            if (!seen.add(citationId)) {
                throw new CitationValidationException(
                        "DUPLICATE_CITATION", "The language model returned duplicate citations");
            }
            GroundedEvidence evidence = byAlias.get(citationId);
            if (evidence == null) {
                throw new CitationValidationException(
                        "UNKNOWN_CITATION",
                        "The language model cited evidence outside this request");
            }
            cited.add(evidence);
        }
        return new ValidatedAnswer(output.status(), bounded(output.answer()), List.copyOf(cited));
    }

    private String bounded(String answer) {
        String normalized = answer.strip();
        if (normalized.length() > properties.maxAnswerCharacters()) {
            throw new CitationValidationException(
                    "ANSWER_TOO_LARGE", "The language model answer exceeded the configured limit");
        }
        return normalized;
    }

    public record ValidatedAnswer(
            AnswerStatus status, String answer, List<GroundedEvidence> evidence) {}
}
