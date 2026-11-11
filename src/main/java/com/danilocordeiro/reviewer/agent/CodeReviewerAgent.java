package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.model.AgentFindings;
import com.danilocordeiro.reviewer.model.Finding;
import com.danilocordeiro.reviewer.model.ReviewReport;
import com.danilocordeiro.reviewer.model.ReviewRequest;
import com.danilocordeiro.reviewer.model.Severity;
import com.danilocordeiro.reviewer.model.Verdict;
import com.danilocordeiro.reviewer.tools.SecretScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Orchestrates one review:
 * <ol>
 *   <li>deterministic secret scan,</li>
 *   <li>every specialist on every chunk, in parallel on virtual threads, each call with its own timeout,</li>
 *   <li>sanitize and tag findings, then de-duplicate and sort,</li>
 *   <li>compute the verdict from the findings.</li>
 * </ol>
 * A failing specialist (error, timeout, unparseable output) does not fail the review; it is reported in the summary.
 */
public class CodeReviewerAgent {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewerAgent.class);

    private final SecretScanner secretScanner;
    private final SpecialistRunner runner;
    private final InputChunker chunker;
    private final FindingMerger merger;
    private final VerdictPolicy verdictPolicy;
    private final ExecutorService executor;
    private final Semaphore permits;
    private final Duration timeout;

    public CodeReviewerAgent(SecretScanner secretScanner, SpecialistRunner runner, InputChunker chunker,
                             FindingMerger merger, VerdictPolicy verdictPolicy, ExecutorService executor,
                             int maxConcurrentCalls, Duration timeout) {
        this.secretScanner = secretScanner;
        this.runner = runner;
        this.chunker = chunker;
        this.merger = merger;
        this.verdictPolicy = verdictPolicy;
        this.executor = executor;
        this.permits = new Semaphore(maxConcurrentCalls, true);
        this.timeout = timeout;
    }

    public ReviewReport review(ReviewRequest request) {
        long started = System.nanoTime();
        List<Chunk> chunks = chunker.split(request); // may throw InputTooLargeException before any model call

        List<Finding> all = new ArrayList<>(secretScanner.scan(request.content(), request.isDiff()));

        List<CompletableFuture<Outcome>> calls = new ArrayList<>();
        for (Chunk chunk : chunks) {
            for (Specialist specialist : Specialist.values()) {
                calls.add(CompletableFuture.supplyAsync(() -> runGuarded(specialist, chunk), executor));
            }
        }

        List<String> failures = new ArrayList<>();
        for (CompletableFuture<Outcome> call : calls) {
            Outcome outcome = call.join(); // runGuarded never throws
            if (outcome.error() != null) {
                failures.add(outcome.describeFailure(chunks.size() > 1));
                continue;
            }
            List<Finding> clean = merger.sanitize(outcome.findings(), outcome.specialist().id(),
                    outcome.specialist().defaultCategory(), outcome.chunk().lineCount());
            for (Finding f : clean) {
                all.add(withRequestLine(f, outcome.chunk()));
            }
        }

        List<Finding> findings = merger.merge(all);
        Verdict verdict = verdictPolicy.decide(findings, !failures.isEmpty());
        Map<Severity, Long> counts = counts(findings);
        long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        log.info("review done: verdict={} findings={} chunks={} failures={} durationMs={}",
                verdict, findings.size(), chunks.size(), failures.size(), durationMs);
        return new ReviewReport(verdict, summary(findings, counts, failures), counts, findings, durationMs);
    }

    /**
     * Runs one model call with a timeout that starts when the call actually begins, not when it was queued
     * behind the concurrency limit. Never throws.
     */
    private Outcome runGuarded(Specialist specialist, Chunk chunk) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.failed(specialist, chunk, "interrupted");
        }
        Future<AgentFindings> call = executor.submit(() -> runner.review(specialist, chunk));
        try {
            AgentFindings result = call.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            return Outcome.ok(specialist, chunk, result.findings());
        } catch (TimeoutException e) {
            call.cancel(true);
            log.warn("{} timed out after {}s", specialist.id(), timeout.toSeconds());
            return Outcome.failed(specialist, chunk, "timed out after " + timeout.toSeconds() + "s");
        } catch (ExecutionException e) {
            String reason = classify(e.getCause());
            // Only the exception type is logged: messages from output converters can echo model output,
            // which may quote the reviewed code (and any secret in it).
            log.warn("{} failed: {} ({})", specialist.id(), reason, e.getCause().getClass().getName());
            return Outcome.failed(specialist, chunk, reason);
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            return Outcome.failed(specialist, chunk, "interrupted");
        } finally {
            permits.release();
        }
    }

    static String classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String type = t.getClass().getName();
            if (t instanceof InvalidModelOutputException || type.contains("jackson") || type.contains("Json")) {
                return "returned invalid JSON";
            }
        }
        return "failed (" + error.getClass().getSimpleName() + ")";
    }

    private static Finding withRequestLine(Finding f, Chunk chunk) {
        Integer line = chunk.toRequestLine(f.line());
        return new Finding(f.severity(), f.category(), f.title(), f.description(), line, f.suggestion(), f.reviewer());
    }

    private static Map<Severity, Long> counts(List<Finding> findings) {
        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            counts.put(s, 0L);
        }
        findings.forEach(f -> counts.merge(f.severity(), 1L, Long::sum));
        return counts;
    }

    private static String summary(List<Finding> findings, Map<Severity, Long> counts, List<String> failures) {
        StringBuilder sb = new StringBuilder();
        if (findings.isEmpty()) {
            sb.append("No issues found.");
        } else {
            sb.append(findings.size()).append(findings.size() == 1 ? " finding: " : " findings: ");
            sb.append(Arrays.stream(Severity.values())
                    .filter(s -> counts.get(s) > 0)
                    .map(s -> counts.get(s) + " " + s.name().toLowerCase())
                    .collect(Collectors.joining(", ")));
            sb.append('.');
        }
        if (!failures.isEmpty()) {
            sb.append(" Incomplete review: ").append(String.join("; ", failures)).append('.');
        }
        return sb.toString();
    }

    private record Outcome(Specialist specialist, Chunk chunk, List<Finding> findings, String error) {

        static Outcome ok(Specialist s, Chunk c, List<Finding> findings) {
            return new Outcome(s, c, findings, null);
        }

        static Outcome failed(Specialist s, Chunk c, String error) {
            return new Outcome(s, c, List.of(), error);
        }

        String describeFailure(boolean multiChunk) {
            String where = multiChunk && chunk.fileName() != null ? " on " + chunk.fileName() : "";
            return specialist.id() + where + " " + error;
        }
    }
}
