package io.github.yanziki.enterpriseai.knowledge.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record StoredObject(InputStream content, long contentLength, String contentType)
        implements AutoCloseable {

    public StoredObject {
        Objects.requireNonNull(content, "content");
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
