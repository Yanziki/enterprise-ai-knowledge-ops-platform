package io.github.yanziki.enterpriseai.answer;

import io.github.yanziki.enterpriseai.retrieval.search.RetrievalSearchResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GroundedContextAssembler {

    private final AnswerProperties properties;
    private final ObjectMapper objectMapper;

    public GroundedContextAssembler(AnswerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AssembledContext assemble(List<RetrievalSearchResult> results) {
        List<GroundedEvidence> evidence = new ArrayList<>();
        List<PromptEvidence> promptItems = new ArrayList<>();
        int limit = Math.min(results.size(), properties.maxEvidenceChunks());
        for (int index = 0; index < limit; index++) {
            RetrievalSearchResult result = results.get(index);
            String citationId = "C" + (index + 1);
            String content =
                    truncate(result.citation().snippet(), properties.maxEvidenceCharacters());
            var citation = result.citation();
            PromptEvidence item =
                    new PromptEvidence(
                            citationId,
                            citation.documentTitle(),
                            citation.versionNumber(),
                            citation.locatorType(),
                            citation.locatorValue(),
                            citation.startCharacter(),
                            citation.endCharacter(),
                            content);
            List<PromptEvidence> candidate = new ArrayList<>(promptItems);
            candidate.add(item);
            String serialized = objectMapper.writeValueAsString(candidate);
            if (serialized.length() > properties.maxContextCharacters()) {
                break;
            }
            promptItems.add(item);
            evidence.add(new GroundedEvidence(citationId, content, result));
        }
        return new AssembledContext(
                objectMapper.writeValueAsString(promptItems), List.copyOf(evidence));
    }

    private String truncate(String value, int limit) {
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit - 1).stripTrailing() + "…";
    }

    private record PromptEvidence(
            String citationId,
            String title,
            int versionNumber,
            String locatorType,
            String locatorValue,
            int startCharacter,
            int endCharacter,
            String content) {}
}
