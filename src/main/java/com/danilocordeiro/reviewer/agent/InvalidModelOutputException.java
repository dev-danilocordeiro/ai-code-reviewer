package com.danilocordeiro.reviewer.agent;

/** The model answered, but not with something that maps to {@code AgentFindings}. */
public class InvalidModelOutputException extends RuntimeException {

    public InvalidModelOutputException(String message) {
        super(message);
    }
}
