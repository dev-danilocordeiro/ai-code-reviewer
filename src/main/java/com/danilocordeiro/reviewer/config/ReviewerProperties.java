package com.danilocordeiro.reviewer.config;

import com.danilocordeiro.reviewer.model.Severity;
import com.danilocordeiro.reviewer.model.Verdict;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param maxInputChars          largest single unit (file, or one file of a diff) sent to the specialists
 * @param maxChunks              upper bound on files reviewed from one diff, to cap cost and latency
 * @param timeoutSeconds         per-specialist, per-chunk time budget, counted from when the call starts
 * @param maxConcurrentCalls     model calls in flight at once across all requests (local models serialize anyway)
 * @param nativeStructuredOutput ask the provider for schema-constrained JSON when it supports it
 * @param titleSimilarity        token overlap (0..1) above which two findings on the same line are duplicates
 */
@Validated
@ConfigurationProperties(prefix = "reviewer")
public record ReviewerProperties(
        @DefaultValue("24000") @Min(1000) int maxInputChars,
        @DefaultValue("20") @Min(1) int maxChunks,
        @DefaultValue("180") @Min(1) int timeoutSeconds,
        @DefaultValue("3") @Min(1) int maxConcurrentCalls,
        @DefaultValue("true") boolean nativeStructuredOutput,
        @DefaultValue("0.6") @DecimalMin("0.0") @DecimalMax("1.0") double titleSimilarity,
        @DefaultValue @Valid @NotNull VerdictRule verdict) {

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * @param requestChangesAt     any finding at or above this severity yields {@code REQUEST_CHANGES}
     * @param commentAt            otherwise, any finding at or above this severity yields {@code COMMENT}
     * @param onReviewerFailure    lowest verdict returned when a specialist failed, so a broken review never reads as a clean approval
     */
    public record VerdictRule(
            @DefaultValue("HIGH") @NotNull Severity requestChangesAt,
            @DefaultValue("INFO") @NotNull Severity commentAt,
            @DefaultValue("COMMENT") @NotNull Verdict onReviewerFailure) {
    }
}
