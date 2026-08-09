package io.github.yanziki.enterpriseai.knowledge.api;

import org.springframework.http.HttpStatus;

public class KnowledgeApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public KnowledgeApiException(HttpStatus status, String code, String safeMessage) {
        super(safeMessage);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
