package io.github.yanziki.enterpriseai.answer;

public class CitationValidationException extends RuntimeException {

    private final String failureClass;

    public CitationValidationException(String failureClass, String safeMessage) {
        super(safeMessage);
        this.failureClass = failureClass;
    }

    public String failureClass() {
        return failureClass;
    }
}
