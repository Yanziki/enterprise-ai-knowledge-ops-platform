package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.KnowledgeIngestionProperties;
import io.github.yanziki.enterpriseai.knowledge.api.KnowledgeApiException;
import io.github.yanziki.enterpriseai.knowledge.storage.BoundedUploadStager;
import io.github.yanziki.enterpriseai.knowledge.storage.StagedUpload;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadValidationService {

    private static final Map<String, Set<String>> DECLARED_TYPES =
            Map.of(
                    "pdf", Set.of("application/pdf"),
                    "txt", Set.of("text/plain"),
                    "md", Set.of("text/markdown", "text/plain", "text/x-markdown"),
                    "markdown", Set.of("text/markdown", "text/plain", "text/x-markdown"));
    private static final Set<String> TEXT_DETECTED_TYPES =
            Set.of("text/plain", "text/markdown", "text/x-markdown");

    private final BoundedUploadStager uploadStager;
    private final KnowledgeIngestionProperties properties;
    private final Tika tika = new Tika();

    public UploadValidationService(
            BoundedUploadStager uploadStager, KnowledgeIngestionProperties properties) {
        this.uploadStager = uploadStager;
        this.properties = properties;
    }

    public ValidatedUpload stageAndValidate(MultipartFile file) {
        String filename = validateFilename(file.getOriginalFilename());
        String extension = extension(filename);
        String declaredType = canonicalContentType(file.getContentType());
        if (!DECLARED_TYPES.get(extension).contains(declaredType)) {
            throw new KnowledgeApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "CONTENT_TYPE_MISMATCH",
                    "The declared content type does not match the file extension");
        }

        StagedUpload staged = null;
        try {
            staged = uploadStager.stage(file.getInputStream(), properties.maxOriginalBytes());
            if (staged.byteSize() == 0) {
                throw new KnowledgeApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "EMPTY_DOCUMENT",
                        "The uploaded document is empty");
            }
            String detectedType;
            try (InputStream input = Files.newInputStream(staged.path())) {
                detectedType = tika.detect(input, filename).toLowerCase(Locale.ROOT);
            }
            validateDetectedType(extension, detectedType);
            return new ValidatedUpload(filename, declaredType, detectedType, staged);
        } catch (IOException exception) {
            closeQuietly(staged);
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST,
                    "UPLOAD_READ_FAILURE",
                    "The uploaded document could not be read");
        } catch (RuntimeException exception) {
            closeQuietly(staged);
            throw exception;
        }
    }

    private String validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255) {
            throw unsupported("A supported filename is required");
        }
        String trimmed = filename.trim();
        if (trimmed.contains("/")
                || trimmed.contains("\\")
                || trimmed.equals("..")
                || trimmed.startsWith("../")
                || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSAFE_FILENAME",
                    "The filename contains unsafe path characters");
        }
        extension(trimmed);
        return trimmed;
    }

    private String extension(String filename) {
        int separator = filename.lastIndexOf('.');
        if (separator <= 0 || separator == filename.length() - 1) {
            throw unsupported("Only PDF, TXT, and Markdown files are supported");
        }
        String extension = filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!DECLARED_TYPES.containsKey(extension)) {
            throw unsupported("Only PDF, TXT, and Markdown files are supported");
        }
        return extension;
    }

    private String canonicalContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void validateDetectedType(String extension, String detectedType) {
        boolean valid =
                extension.equals("pdf")
                        ? detectedType.equals("application/pdf")
                        : TEXT_DETECTED_TYPES.contains(detectedType);
        if (!valid) {
            throw new KnowledgeApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "CONTENT_TYPE_MISMATCH",
                    "The detected document type does not match the filename");
        }
    }

    private KnowledgeApiException unsupported(String message) {
        return new KnowledgeApiException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", message);
    }

    private void closeQuietly(StagedUpload staged) {
        if (staged != null) {
            staged.close();
        }
    }
}
