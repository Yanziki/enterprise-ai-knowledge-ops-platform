package io.github.yanziki.enterpriseai.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yanziki.enterpriseai.retrieval.chunking.ParagraphWhitespaceChunker;
import io.github.yanziki.enterpriseai.retrieval.chunking.RetrievalChunkDraft;
import io.github.yanziki.enterpriseai.retrieval.chunking.SourceTextUnit;
import io.github.yanziki.enterpriseai.retrieval.embedding.DeterministicSmokeEmbeddingProvider;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingValidation;
import io.github.yanziki.enterpriseai.retrieval.indexing.RetrievalIndexingException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicRetrievalComponentsTest {

    private static final RetrievalProperties PROPERTIES =
            new RetrievalProperties(100, 20, 10, 750, 4, 2, 300, 2000, 20, 50, 32, "none");

    @Test
    void chunkingIsDeterministicAndPreservesSourceBoundaries() {
        ParagraphWhitespaceChunker chunker = new ParagraphWhitespaceChunker(PROPERTIES);
        UUID indexId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        String text = "alpha ".repeat(30) + "\n\n" + "omega ".repeat(20);
        SourceTextUnit unit = new SourceTextUnit(unitId, 1, "PAGE", "2", text);

        List<RetrievalChunkDraft> first = chunker.chunk(indexId, List.of(unit));
        List<RetrievalChunkDraft> second = chunker.chunk(indexId, List.of(unit));

        assertThat(first).isEqualTo(second).hasSizeGreaterThan(1);
        assertThat(first)
                .allSatisfy(
                        chunk -> {
                            assertThat(chunk.sourceTextUnitId()).isEqualTo(unitId);
                            assertThat(chunk.locatorType()).isEqualTo("PAGE");
                            assertThat(chunk.locatorValue()).isEqualTo("2");
                            assertThat(text.substring(chunk.startCharacter(), chunk.endCharacter()))
                                    .isEqualTo(chunk.text());
                        });
    }

    @Test
    void smokeEmbeddingsAreDeterministicFiniteAndNormalized() {
        DeterministicSmokeEmbeddingProvider provider = new DeterministicSmokeEmbeddingProvider();

        float[] first = provider.embedQuery("expense approval policy");
        float[] second = provider.embedQuery("expense approval policy");

        assertThat(first).containsExactly(second).hasSize(64);
        EmbeddingValidation.validate(first, 64);
        double norm = Math.sqrt(java.util.Arrays.stream(toDouble(first)).map(v -> v * v).sum());
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void embeddingValidationRejectsWrongDimensionsAndNonFiniteValues() {
        assertThatThrownBy(() -> EmbeddingValidation.validate(new float[2], 3))
                .isInstanceOf(RetrievalIndexingException.class)
                .hasMessageContaining("incompatible dimension");
        assertThatThrownBy(() -> EmbeddingValidation.validate(new float[] {Float.NaN, 1.0f}, 2))
                .isInstanceOf(RetrievalIndexingException.class)
                .hasMessageContaining("invalid vector");
    }

    private static double[] toDouble(float[] values) {
        double[] converted = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            converted[index] = values[index];
        }
        return converted;
    }
}
