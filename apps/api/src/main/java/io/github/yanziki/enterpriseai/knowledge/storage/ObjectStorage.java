package io.github.yanziki.enterpriseai.knowledge.storage;

import java.nio.file.Path;

public interface ObjectStorage {

    void put(String objectKey, Path source, long contentLength, String contentType);

    StoredObject get(String objectKey);

    void delete(String objectKey);
}
