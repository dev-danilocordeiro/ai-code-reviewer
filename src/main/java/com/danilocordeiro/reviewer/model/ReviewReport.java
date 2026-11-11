package com.danilocordeiro.reviewer.model;

import java.util.Map;
import java.util.List;

public record ReviewReport(
        Verdict verdict,
        String summary,
        Map<Severity, Long> counts,
        List<Finding> findings,
        long durationMs) {
}
