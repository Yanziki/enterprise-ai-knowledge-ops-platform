package io.github.yanziki.enterpriseai.answer;

import java.util.List;

public record StructuredModelOutput(AnswerStatus status, String answer, List<String> citationIds) {}
