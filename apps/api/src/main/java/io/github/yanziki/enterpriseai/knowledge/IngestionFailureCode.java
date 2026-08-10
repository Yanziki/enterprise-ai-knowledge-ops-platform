package io.github.yanziki.enterpriseai.knowledge;

public enum IngestionFailureCode {
    UNSUPPORTED_MEDIA_TYPE,
    FILE_TOO_LARGE,
    CONTENT_TYPE_MISMATCH,
    EMPTY_DOCUMENT,
    PARSER_FAILURE,
    TEXT_LIMIT_EXCEEDED,
    STORAGE_FAILURE,
    INTERNAL_PROCESSING_ERROR
}
