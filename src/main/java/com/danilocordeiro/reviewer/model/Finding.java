package com.danilocordeiro.reviewer.model;

/**
 * A single review finding.
 *
 * @param line 1-based line of the submitted content, or {@code null} when the finding is not tied to a line
 * @param reviewer id of the reviewer that produced it; filled in by the orchestrator, never trusted from the model
 */
public record Finding(
        Severity severity,
        Category category,
        String title,
        String description,
        Integer line,
        String suggestion,
        String reviewer) {

    public Finding withReviewer(String reviewer) {
        return new Finding(severity, category, title, description, line, suggestion, reviewer);
    }
}
