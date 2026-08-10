package io.github.yanziki.enterpriseai.knowledge.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class BoundedUploadStager {

    private static final int BUFFER_SIZE = 16 * 1024;

    public StagedUpload stage(InputStream input, long maximumBytes) throws IOException {
        Path stagedPath = Files.createTempFile("enterprise-ai-upload-", ".bin");
        MessageDigest digest = sha256Digest();
        long byteSize = 0;

        try (OutputStream output = Files.newOutputStream(stagedPath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                byteSize += bytesRead;
                if (byteSize > maximumBytes) {
                    throw new UploadLimitExceededException(maximumBytes);
                }
                digest.update(buffer, 0, bytesRead);
                output.write(buffer, 0, bytesRead);
            }
            return new StagedUpload(
                    stagedPath, byteSize, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(stagedPath);
            throw exception;
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }
}
