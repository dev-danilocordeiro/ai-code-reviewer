package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.model.Category;
import com.danilocordeiro.reviewer.model.Finding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Cleans, de-duplicates and orders findings coming from several reviewers. */
public class FindingMerger {

    static final Comparator<Finding> ORDER = Comparator
            .comparing(Finding::severity)
            .thenComparing(Finding::line, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Finding::category)
            .thenComparing(Finding::title);

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "of", "in", "on", "at", "to", "for", "and", "or", "is", "are", "be",
            "with", "via", "from", "by", "this", "that", "it", "its", "possible", "potential");

    private final double titleSimilarity;

    public FindingMerger(double titleSimilarity) {
        this.titleSimilarity = titleSimilarity;
    }

    /**
     * Drops findings without severity or title, tags the reviewer, defaults a missing category to the
     * reviewer's own, and clears line numbers that point outside the submitted content.
     */
    public List<Finding> sanitize(List<Finding> raw, String reviewer, Category defaultCategory, int lineCount) {
        List<Finding> out = new ArrayList<>();
        for (Finding f : raw) {
            if (f == null || f.severity() == null || f.title() == null || f.title().isBlank()) {
                continue;
            }
            Integer line = f.line() != null && f.line() >= 1 && f.line() <= lineCount ? f.line() : null;
            Category category = f.category() != null ? f.category() : defaultCategory;
            out.add(new Finding(f.severity(), category, f.title().strip(), f.description(), line,
                    f.suggestion(), reviewer));
        }
        return out;
    }

    /**
     * Merges duplicates (same line, equivalent title) keeping the most severe, then sorts. Category is
     * ignored on purpose: specialists often file the same problem under different categories
     * (an SQL injection as SECURITY and as BUG), and reporting it twice is noise.
     */
    public List<Finding> merge(List<Finding> findings) {
        List<Finding> sorted = findings.stream().sorted(ORDER).toList();
        List<Finding> kept = new ArrayList<>();
        for (Finding candidate : sorted) {
            int dup = indexOfDuplicate(kept, candidate);
            if (dup < 0) {
                kept.add(candidate);
            } else {
                // kept.get(dup) is at least as severe because input is sorted by severity
                Finding winner = kept.get(dup);
                kept.set(dup, winner.withReviewer(joinReviewers(winner.reviewer(), candidate.reviewer())));
            }
        }
        kept.sort(ORDER);
        return List.copyOf(kept);
    }

    private int indexOfDuplicate(List<Finding> kept, Finding candidate) {
        for (int i = 0; i < kept.size(); i++) {
            Finding k = kept.get(i);
            if (Objects.equals(k.line(), candidate.line())
                    && similarity(k.title(), candidate.title()) >= titleSimilarity) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Overlap coefficient on normalized title tokens: |A ∩ B| / min(|A|, |B|). Unlike Jaccard it treats
     * "SQL injection" and "SQL injection via string concatenation" as the same finding.
     */
    static double similarity(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return ta.equals(tb) ? 1.0 : 0.0;
        }
        Set<String> common = new HashSet<>(ta);
        common.retainAll(tb);
        return (double) common.size() / Math.min(ta.size(), tb.size());
    }

    private static Set<String> tokens(String title) {
        return Arrays.stream(title.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+"))
                .filter(t -> t.length() > 1 && !STOPWORDS.contains(t))
                .map(FindingMerger::singular)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String singular(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static String joinReviewers(String a, String b) {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(Arrays.asList(a.split("\\+")));
        all.addAll(Arrays.asList(b.split("\\+")));
        return String.join("+", all);
    }
}
