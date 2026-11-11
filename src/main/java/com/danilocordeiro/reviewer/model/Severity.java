package com.danilocordeiro.reviewer.model;

/** Ordered from most to least severe; {@link #ordinal()} is used for sorting. */
public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW, INFO;

    public boolean isAtLeast(Severity other) {
        return this.ordinal() <= other.ordinal();
    }
}
