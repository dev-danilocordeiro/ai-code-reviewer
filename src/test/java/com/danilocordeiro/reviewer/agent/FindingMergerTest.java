package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.model.Category;
import com.danilocordeiro.reviewer.model.Finding;
import com.danilocordeiro.reviewer.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.danilocordeiro.reviewer.model.Category.*;
import static com.danilocordeiro.reviewer.model.Severity.*;
import static org.assertj.core.api.Assertions.assertThat;

class FindingMergerTest {

    private final FindingMerger merger = new FindingMerger(0.6);

    private static Finding f(Severity s, Category c, String title, Integer line, String reviewer) {
        return new Finding(s, c, title, "desc", line, "fix", reviewer);
    }

    @Test
    void sanitizeDropsFindingsWithoutSeverityOrTitle() {
        List<Finding> raw = Arrays.asList(
                f(null, BUG, "No severity", 1, null),
                f(HIGH, BUG, null, 1, null),
                f(HIGH, BUG, "   ", 1, null),
                null,
                f(LOW, BUG, "Kept", 1, null));

        assertThat(merger.sanitize(raw, "x", BUG, 10)).extracting(Finding::title).containsExactly("Kept");
    }

    @Test
    void sanitizeOverwritesReviewerAndFillsDefaults() {
        List<Finding> out = merger.sanitize(
                List.of(f(HIGH, null, " SQL injection ", 3, "spoofed-by-model")), "security-reviewer", SECURITY, 10);

        assertThat(out).singleElement().satisfies(x -> {
            assertThat(x.reviewer()).isEqualTo("security-reviewer");
            assertThat(x.category()).isEqualTo(SECURITY);
            assertThat(x.title()).isEqualTo("SQL injection");
        });
    }

    @Test
    void sanitizeClearsOutOfRangeLines() {
        List<Finding> out = merger.sanitize(
                List.of(f(LOW, BUG, "a", 0, null), f(LOW, BUG, "b", 11, null), f(LOW, BUG, "c", 10, null)),
                "r", BUG, 10);

        assertThat(out).extracting(Finding::line).containsExactly(null, null, 10);
    }

    @Test
    void mergeCollapsesEquivalentTitlesOnSameLineAndCategoryKeepingMostSevere() {
        List<Finding> merged = merger.merge(List.of(
                f(MEDIUM, SECURITY, "SQL injection via string concatenation", 12, "maintainability-reviewer"),
                f(CRITICAL, SECURITY, "Possible SQL Injection", 12, "security-reviewer")));

        assertThat(merged).singleElement().satisfies(x -> {
            assertThat(x.severity()).isEqualTo(CRITICAL);
            assertThat(x.title()).isEqualTo("Possible SQL Injection");
            assertThat(x.reviewer()).isEqualTo("security-reviewer+maintainability-reviewer");
        });
    }

    @Test
    void mergeKeepsFindingsThatDifferInLineCategoryOrTitle() {
        List<Finding> merged = merger.merge(List.of(
                f(HIGH, SECURITY, "SQL injection", 12, "a"),
                f(HIGH, SECURITY, "SQL injection", 13, "a"),
                f(HIGH, BUG, "SQL injection", 12, "a"),
                f(HIGH, SECURITY, "Weak hashing algorithm", 12, "a")));

        assertThat(merged).hasSize(4);
    }

    @Test
    void mergeSortsBySeverityThenLineWithNullLinesLast() {
        List<Finding> merged = merger.merge(List.of(
                f(LOW, BUG, "low", 1, "r"),
                f(HIGH, BUG, "high-no-line", null, "r"),
                f(HIGH, BUG, "high-line-9", 9, "r"),
                f(CRITICAL, SECURITY, "critical", 40, "r"),
                f(INFO, MAINTAINABILITY, "info", 2, "r"),
                f(HIGH, PERFORMANCE, "high-line-2", 2, "r")));

        assertThat(merged).extracting(Finding::title).containsExactly(
                "critical", "high-line-2", "high-line-9", "high-no-line", "low", "info");
    }

    @Test
    void similarityIgnoresCasePunctuationStopwordsAndPlurals() {
        assertThat(FindingMerger.similarity("Unbounded queries", "unbounded query!")).isEqualTo(1.0);
        assertThat(FindingMerger.similarity("N+1 query in loop", "Missing pagination")).isZero();
    }
}
