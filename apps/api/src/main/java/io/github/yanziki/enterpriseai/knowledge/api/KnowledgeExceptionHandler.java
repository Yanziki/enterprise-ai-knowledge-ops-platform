package io.github.yanziki.enterpriseai.knowledge.api;

import io.github.yanziki.enterpriseai.answer.CitationValidationException;
import io.github.yanziki.enterpriseai.answer.LanguageModelException;
import io.github.yanziki.enterpriseai.knowledge.storage.ObjectStorageException;
import io.github.yanziki.enterpriseai.knowledge.storage.UploadLimitExceededException;
import io.github.yanziki.enterpriseai.workflow.review.ReviewCaseConflictException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class KnowledgeExceptionHandler {

    @ExceptionHandler(KnowledgeApiException.class)
    ResponseEntity<ErrorResponse> handleKnowledgeError(KnowledgeApiException exception) {
        return response(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleInvalidJson(HttpMessageNotReadableException exception) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request body is malformed or contains unsupported fields");
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

    @ExceptionHandler(CitationValidationException.class)
    ResponseEntity<ErrorResponse> handleInvalidModelOutput(CitationValidationException exception) {
        return response(
                HttpStatus.BAD_GATEWAY,
                "MODEL_OUTPUT_INVALID",
                "The language model response failed grounded citation validation");
    }

    @ExceptionHandler(LanguageModelException.class)
    ResponseEntity<ErrorResponse> handleLanguageModelFailure(LanguageModelException exception) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ANSWER_PROVIDER_FAILURE",
                "Answer generation is temporarily unavailable");
    }

    @ExceptionHandler(ReviewCaseConflictException.class)
    ResponseEntity<ErrorResponse> handleReviewConflict(ReviewCaseConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticConflict(
            ObjectOptimisticLockingFailureException exception) {
        return response(
                HttpStatus.CONFLICT,
                "REVIEW_CASE_CONFLICT",
                "The review case changed while the request was being processed");
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String safeMessage) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), code, safeMessage));
    }

    record ErrorResponse(Instant timestamp, int status, String code, String message) {}
}
