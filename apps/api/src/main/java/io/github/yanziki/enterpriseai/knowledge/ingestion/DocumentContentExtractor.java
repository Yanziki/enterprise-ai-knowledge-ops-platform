package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.storage.StoredObject;

public interface DocumentContentExtractor {

    ExtractionResult extract(StoredObject storedObject, String detectedContentType);
}
