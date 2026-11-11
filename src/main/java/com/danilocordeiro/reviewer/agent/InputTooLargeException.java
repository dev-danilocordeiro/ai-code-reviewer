package com.danilocordeiro.reviewer.agent;

/** The request cannot be reviewed within the configured size limits. Mapped to HTTP 413. */
public class InputTooLargeException extends RuntimeException {

    private final int limit;

    public InputTooLargeException(String message, int limit) {
        super(message);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
