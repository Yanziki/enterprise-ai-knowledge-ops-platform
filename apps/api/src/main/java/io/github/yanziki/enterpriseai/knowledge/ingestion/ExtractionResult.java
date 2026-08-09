package io.github.yanziki.enterpriseai.knowledge.ingestion;

import java.util.List;

public record ExtractionResult(
        String parserName, String parserVersion, List<ExtractedTextUnit> textUnits) {

    public ExtractionResult {
        textUnits = List.copyOf(textUnits);
    }
}
