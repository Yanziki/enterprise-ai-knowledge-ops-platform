package io.github.yanziki.enterpriseai.retrieval.chunking;

import java.util.List;
import java.util.UUID;

public interface ProvenanceChunker {

    String name();

    String version();

    int chunkSize();

    int overlap();

    List<RetrievalChunkDraft> chunk(UUID retrievalIndexId, List<SourceTextUnit> textUnits);
}
