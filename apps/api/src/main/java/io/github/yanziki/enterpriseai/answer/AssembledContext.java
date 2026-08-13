package io.github.yanziki.enterpriseai.answer;

import java.util.List;

public record AssembledContext(String promptEvidence, List<GroundedEvidence> evidence) {
    public int characters() {
        return promptEvidence.length();
    }
}
