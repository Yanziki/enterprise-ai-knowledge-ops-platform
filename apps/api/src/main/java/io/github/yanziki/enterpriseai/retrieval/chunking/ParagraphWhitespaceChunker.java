package io.github.yanziki.enterpriseai.retrieval.chunking;

import io.github.yanziki.enterpriseai.retrieval.RetrievalFailureCode;
import io.github.yanziki.enterpriseai.retrieval.RetrievalProperties;
import io.github.yanziki.enterpriseai.retrieval.indexing.RetrievalIndexingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ParagraphWhitespaceChunker implements ProvenanceChunker {

    private static final String NAME = "paragraph-whitespace";
    private static final String VERSION = "1";
    private final RetrievalProperties properties;

    public ParagraphWhitespaceChunker(RetrievalProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public int chunkSize() {
        return properties.chunkSize();
    }

    @Override
    public int overlap() {
        return properties.chunkOverlap();
    }

    @Override
    public List<RetrievalChunkDraft> chunk(UUID retrievalIndexId, List<SourceTextUnit> textUnits) {
        List<RetrievalChunkDraft> chunks = new ArrayList<>();
        int ordinal = 1;
        for (SourceTextUnit unit : textUnits) {
            int start = skipWhitespace(unit.text(), 0);
            while (start < unit.text().length()) {
                int upper = Math.min(start + properties.chunkSize(), unit.text().length());
                int end = chooseBoundary(unit.text(), start, upper);
                int trimmedEnd = trimTrailingWhitespace(unit.text(), start, end);
                if (trimmedEnd > start) {
                    if (chunks.size() >= properties.maxChunksPerVersion()) {
                        throw new RetrievalIndexingException(
                                RetrievalFailureCode.CHUNK_LIMIT_EXCEEDED,
                                "The document exceeds the configured chunk limit",
                                false);
                    }
                    String text = unit.text().substring(start, trimmedEnd);
                    chunks.add(
                            new RetrievalChunkDraft(
                                    deterministicId(
                                            retrievalIndexId,
                                            unit.id(),
                                            ordinal,
                                            start,
                                            trimmedEnd),
                                    unit.id(),
                                    ordinal++,
                                    unit.locatorType(),
                                    unit.locatorValue(),
                                    start,
                                    trimmedEnd,
                                    text));
                }
                if (end >= unit.text().length()) {
                    break;
                }
                int next = Math.max(start + 1, end - properties.chunkOverlap());
                start = skipWhitespace(unit.text(), next);
            }
        }
        if (chunks.isEmpty()) {
            throw new RetrievalIndexingException(
                    RetrievalFailureCode.CHUNKING_FAILURE,
                    "The document produced no retrievable chunks",
                    false);
        }
        return List.copyOf(chunks);
    }

    private int chooseBoundary(String text, int start, int upper) {
        if (upper == text.length()) {
            return upper;
        }
        int minimum = Math.min(start + properties.chunkSize() / 2, upper);
        int paragraph = text.lastIndexOf("\n\n", upper - 1);
        if (paragraph >= minimum) {
            return paragraph + 2;
        }
        int newline = text.lastIndexOf('\n', upper - 1);
        if (newline >= minimum) {
            return newline + 1;
        }
        for (int index = upper - 1; index >= minimum; index--) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index + 1;
            }
        }
        return upper;
    }

    private int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private int trimTrailingWhitespace(String text, int start, int end) {
        int index = end;
        while (index > start && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private UUID deterministicId(
            UUID indexId, UUID unitId, int ordinal, int startCharacter, int endCharacter) {
        String identity =
                indexId + ":" + unitId + ":" + ordinal + ":" + startCharacter + ":" + endCharacter;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
}
