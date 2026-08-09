package io.github.yanziki.enterpriseai.knowledge.api;

import io.github.yanziki.enterpriseai.knowledge.storage.ObjectStorageException;
import io.github.yanziki.enterpriseai.knowledge.storage.UploadLimitExceededException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class KnowledgeExceptionHandler {

    @ExceptionHandler(KnowledgeApiException.class)
    ResponseEntity<ErrorResponse> handleKnowledgeError(KnowledgeApiException exception) {
        return response(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, UploadLimitExceededException.class})
    ResponseEntity<ErrorResponse> handleOversizedUpload(RuntimeException exception) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "The document exceeds the configured upload limit");
    }

    @ExceptionHandler(ObjectStorageException.class)
    ResponseEntity<ErrorResponse> handleStorageFailure(ObjectStorageException exception) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_FAILURE",
                "Document storage is temporarily unavailable");
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String safeMessage) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), code, safeMessage));
    }

    record ErrorResponse(Instant timestamp, int status, String code, String message) {}
}
