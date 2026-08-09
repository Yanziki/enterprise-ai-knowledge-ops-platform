package io.github.yanziki.enterpriseai.knowledge.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record StagedUpload(Path path, long byteSize, String sha256Hex) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
    }
}
