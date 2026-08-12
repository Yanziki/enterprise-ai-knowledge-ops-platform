package io.github.yanziki.enterpriseai.retrieval.search;

import io.github.yanziki.enterpriseai.retrieval.RetrievalMode;
import java.util.List;

public record RetrievalCapabilitiesResponse(
        boolean lexicalAvailable,
        boolean vectorAvailable,
        List<RetrievalMode> availableModes,
        RetrievalMode autoMode,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension) {}
