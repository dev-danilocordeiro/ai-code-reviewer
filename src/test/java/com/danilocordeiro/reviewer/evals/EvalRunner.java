package com.danilocordeiro.reviewer.evals;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

/**
 * Runs every case in {@code evals/cases} against a running ai-code-reviewer and scores it.
 *
 * <pre>
 * evals/run.sh --label ollama-qwen2.5-coder-7b [--base-url http://localhost:8080] [--only 01,18]
 * </pre>
 *
 * Scoring, per planted issue: detected when a finding has an accepted category and its line is within
 * {@code tolerance} (default 2) of the line containing the case's {@code at} snippet. Each finding can match
 * one issue. Every finding that matches no planted issue counts as a false positive, including on clean cases.
 */
public final class EvalRunner {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> MEDIUM_OR_WORSE = Set.of("CRITICAL", "HIGH", "MEDIUM");

    record Issue(String label, Set<String> categories, int line, int tolerance) {
    }

    record Case(String id, String fileName, boolean diff, String content, List<Issue> issues) {
        boolean clean() {
            return issues.isEmpty();
        }
    }

    record Result(Case c, int status, String verdict, boolean incomplete, int detected, List<String> missed,
                  int falsePositives, int falsePositivesMediumPlus, long latencyMs, long serverMs, JsonNode report) {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        String label = Objects.requireNonNull(opts.get("label"), "--label is required (e.g. ollama-qwen2.5-coder-7b)");
        String baseUrl = opts.getOrDefault("base-url", "http://localhost:8080");
        Path casesDir = Path.of(opts.getOrDefault("cases", "evals/cases"));
        Path outDir = Path.of(opts.getOrDefault("out", "evals/results"));
        Set<String> only = opts.containsKey("only") ? Set.of(opts.get("only").split(",")) : Set.of();

        List<Case> cases = load(casesDir).stream()
                .filter(c -> only.isEmpty() || only.stream().anyMatch(o -> c.id().startsWith(o)))
                .toList();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        List<Result> results = new ArrayList<>();
        for (Case c : cases) {
            System.err.printf("[%s] %s ... ", label, c.id());
            Result r = run(http, baseUrl, c);
            System.err.printf("%s, %d/%d detected, %d FP, %d ms%n", r.verdict(), r.detected(), c.issues().size(),
                    r.falsePositives(), r.latencyMs());
            results.add(r);
        }

        String markdown = render(label, baseUrl, results);
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve(label + ".md"), markdown);
        ArrayNode raw = JSON.createArrayNode();
        for (Result r : results) {
            ObjectNode n = raw.addObject();
            n.put("case", r.c().id());
            n.put("status", r.status());
            n.put("latencyMs", r.latencyMs());
            n.set("report", r.report());
        }
        Files.writeString(outDir.resolve(label + ".json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(raw));
        System.out.println(markdown);
    }

    private static Result run(HttpClient http, String baseUrl, Case c) throws IOException, InterruptedException {
        ObjectNode body = JSON.createObjectNode();
        body.put("fileName", c.fileName());
        body.put("content", c.content());
        body.put("isDiff", c.diff());
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/review"))
                .timeout(Duration.ofMinutes(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        long started = System.nanoTime();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        JsonNode report = JSON.readTree(response.body());
        if (response.statusCode() != 200) {
            return new Result(c, response.statusCode(), "ERROR", true, 0,
                    c.issues().stream().map(Issue::label).toList(), 0, 0, latencyMs, -1, report);
        }

        List<JsonNode> findings = new ArrayList<>();
        report.get("findings").forEach(findings::add);
        boolean[] used = new boolean[findings.size()];
        int detected = 0;
        List<String> missed = new ArrayList<>();
        for (Issue issue : c.issues()) {
            int match = -1;
            for (int i = 0; i < findings.size() && match < 0; i++) {
                JsonNode f = findings.get(i);
                JsonNode line = f.get("line");
                if (!used[i] && issue.categories().contains(f.get("category").asString())
                        && line != null && !line.isNull()
                        && Math.abs(line.asInt() - issue.line()) <= issue.tolerance()) {
                    match = i;
                }
            }
            if (match >= 0) {
                used[match] = true;
                detected++;
            } else {
                missed.add(issue.label());
            }
        }
        int fp = 0;
        int fpMediumPlus = 0;
        for (int i = 0; i < findings.size(); i++) {
            if (!used[i]) {
                fp++;
                if (MEDIUM_OR_WORSE.contains(findings.get(i).get("severity").asString())) {
                    fpMediumPlus++;
                }
            }
        }
        String summary = report.get("summary").asString();
        return new Result(c, 200, report.get("verdict").asString(), summary.contains("Incomplete review"),
                detected, missed, fp, fpMediumPlus, latencyMs, report.get("durationMs").asLong(), report);
    }

    private static String render(String label, String baseUrl, List<Result> results) {
        StringBuilder md = new StringBuilder();
        md.append("# Eval results: ").append(label).append("\n\n");
        md.append("Run at ").append(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS))
                .append(" against ").append(baseUrl).append(".\n\n");

        int planted = results.stream().mapToInt(r -> r.c().issues().size()).sum();
        int detected = results.stream().mapToInt(Result::detected).sum();
        List<Result> clean = results.stream().filter(r -> r.c().clean()).toList();
        long cleanApproved = clean.stream().filter(r -> "APPROVE".equals(r.verdict())).count();
        long cleanNoFindings = clean.stream().filter(r -> r.falsePositives() == 0 && r.status() == 200).count();
        int fp = results.stream().mapToInt(Result::falsePositives).sum();
        int fpMediumPlus = results.stream().mapToInt(Result::falsePositivesMediumPlus).sum();
        long incomplete = results.stream().filter(Result::incomplete).count();
        List<Result> buggy = results.stream().filter(r -> !r.c().clean()).toList();
        long buggyBlocked = buggy.stream().filter(r -> "REQUEST_CHANGES".equals(r.verdict())).count();
        long[] latencies = results.stream().mapToLong(Result::latencyMs).sorted().toArray();

        md.append("| Metric | Value |\n|---|---|\n");
        md.append("| Cases | %d (%d with planted issues, %d clean) |\n".formatted(results.size(), buggy.size(), clean.size()));
        md.append("| Planted issues detected | %d / %d (%s) |\n".formatted(detected, planted, pct(detected, planted)));
        md.append("| Cases with planted issues that got REQUEST_CHANGES | %d / %d |\n".formatted(buggyBlocked, buggy.size()));
        md.append("| Clean cases approved | %d / %d |\n".formatted(cleanApproved, clean.size()));
        md.append("| Clean cases with zero findings | %d / %d |\n".formatted(cleanNoFindings, clean.size()));
        md.append("| False positives (all severities) | %d (%.1f per case) |\n".formatted(fp, (double) fp / results.size()));
        md.append("| False positives (MEDIUM or worse) | %d |\n".formatted(fpMediumPlus));
        md.append("| Incomplete reviews (a specialist failed) | %d |\n".formatted(incomplete));
        md.append("| Latency p50 / p95 / max | %s / %s / %s |\n\n".formatted(
                seconds(percentile(latencies, 50)), seconds(percentile(latencies, 95)),
                seconds(latencies.length == 0 ? 0 : latencies[latencies.length - 1])));

        md.append("| Case | Verdict | Detected | Missed | FP | Latency |\n|---|---|---|---|---|---|\n");
        for (Result r : results) {
            md.append("| %s | %s%s | %d/%d | %s | %d | %s |\n".formatted(
                    r.c().id(), r.verdict(), r.incomplete() ? " (incomplete)" : "",
                    r.detected(), r.c().issues().size(),
                    r.missed().isEmpty() ? "-" : String.join("; ", r.missed()),
                    r.falsePositives(), seconds(r.latencyMs())));
        }
        return md.toString();
    }

    static List<Case> load(Path dir) throws IOException {
        List<Case> cases = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path expectedFile : files.filter(p -> p.toString().endsWith(".expected.json")).sorted().toList()) {
                String id = expectedFile.getFileName().toString().replace(".expected.json", "");
                Path source = Files.list(dir)
                        .filter(p -> p.getFileName().toString().startsWith(id + ".")
                                && !p.getFileName().toString().endsWith(".expected.json"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("no source file for " + id));
                String content = Files.readString(source);
                JsonNode expected = JSON.readTree(expectedFile.toFile());
                List<Issue> issues = new ArrayList<>();
                for (JsonNode i : expected.path("issues")) {
                    Set<String> categories = new HashSet<>();
                    i.get("categories").forEach(n -> categories.add(n.asString()));
                    issues.add(new Issue(i.get("label").asString(), categories,
                            lineOf(content, i.get("at").asString(), id), i.path("tolerance").asInt(2)));
                }
                cases.add(new Case(id, expected.get("fileName").asString(),
                        expected.path("isDiff").asBoolean(false), content, issues));
            }
        }
        return cases;
    }

    private static int lineOf(String content, String snippet, String caseId) {
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(snippet)) {
                return i + 1;
            }
        }
        throw new IllegalStateException(caseId + ": snippet not found: " + snippet);
    }

    private static long percentile(long[] sorted, int p) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    private static String seconds(long ms) {
        return "%.1f s".formatted(ms / 1000.0);
    }

    private static String pct(int a, int b) {
        return b == 0 ? "n/a" : "%.0f%%".formatted(100.0 * a / b);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            opts.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return opts;
    }
}
