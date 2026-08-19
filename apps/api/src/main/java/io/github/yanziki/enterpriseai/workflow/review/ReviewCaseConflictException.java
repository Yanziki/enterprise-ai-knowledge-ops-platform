package io.github.yanziki.enterpriseai.workflow.review;

public class ReviewCaseConflictException extends RuntimeException {

    private final String code;

    public ReviewCaseConflictException(String code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
