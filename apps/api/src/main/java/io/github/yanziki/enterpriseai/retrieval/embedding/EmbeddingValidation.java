package io.github.yanziki.enterpriseai.retrieval.embedding;

import io.github.yanziki.enterpriseai.retrieval.RetrievalFailureCode;
import io.github.yanziki.enterpriseai.retrieval.indexing.RetrievalIndexingException;
import java.util.List;

public final class EmbeddingValidation {

    private EmbeddingValidation() {}

    public static void validateBatch(List<float[]> vectors, int expectedCount, int dimension) {
        if (vectors.size() != expectedCount) {
            throw invalid("The embedding provider returned an unexpected vector count");
        }
        for (float[] vector : vectors) {
            validate(vector, dimension);
        }
    }

    public static void validate(float[] vector, int dimension) {
        if (vector == null || vector.length != dimension) {
            throw new RetrievalIndexingException(
                    RetrievalFailureCode.EMBEDDING_DIMENSION_MISMATCH,
                    "The embedding provider returned an incompatible dimension",
                    false);
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw invalid("The embedding provider returned an invalid vector");
            }
        }
    }

    private static RetrievalIndexingException invalid(String message) {
        return new RetrievalIndexingException(
                RetrievalFailureCode.EMBEDDING_RESPONSE_INVALID, message, false);
    }
}
