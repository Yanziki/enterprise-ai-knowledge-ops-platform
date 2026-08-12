package io.github.yanziki.enterpriseai.retrieval.embedding;

import java.util.List;

public interface EmbeddingProvider {

    String providerId();

    String modelId();

    int dimension();

    List<float[]> embedDocuments(List<String> texts);

    float[] embedQuery(String query);
}
