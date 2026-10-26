package com.meridian.ai;

/** Any reason a model result is unavailable. Callers fall back to deterministic code. */
public class AiException extends RuntimeException {

    public enum Reason { DISABLED, TIMEOUT, UNAVAILABLE, INVALID_OUTPUT, CANCELLED }

    private final Reason reason;

    public AiException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** Plain-language explanation shown next to fallback results. */
    public String userMessage() {
        return switch (reason) {
            case DISABLED -> "AI is turned off, so this was built with rules.";
            case TIMEOUT -> "The AI model took too long, so this was built with rules.";
            case UNAVAILABLE -> "The AI model could not be reached, so this was built with rules.";
            case INVALID_OUTPUT -> "The AI model's answer failed validation, so this was built with rules.";
            case CANCELLED -> "The request was cancelled.";
        };
    }
}
