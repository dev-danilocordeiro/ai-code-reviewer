package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.model.ReviewRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a request into chunks no larger than {@code maxChars}.
 *
 * <ul>
 *   <li>Input within the limit is one chunk.</li>
 *   <li>A diff over the limit is split at {@code diff --git} boundaries, and consecutive file sections are
 *       packed together while they fit.</li>
 *   <li>A single file over the limit, a diff section over the limit, or more than {@code maxChunks} chunks
 *       is rejected. Cutting a file in the middle would hide context from the reviewers.</li>
 * </ul>
 */
public class InputChunker {

    private static final Pattern FILE_HEADER = Pattern.compile("^diff --git a/(\\S+) b/(\\S+)");

    private final int maxChars;
    private final int maxChunks;

    public InputChunker(int maxChars, int maxChunks) {
        this.maxChars = maxChars;
        this.maxChunks = maxChunks;
    }

    public List<Chunk> split(ReviewRequest request) {
        String content = request.content();
        if (content.length() <= maxChars) {
            return List.of(new Chunk(request.fileName(), content, 1, request.isDiff()));
        }
        if (!request.isDiff()) {
            throw new InputTooLargeException("Content has %d characters; the limit for a single file is %d."
                    .formatted(content.length(), maxChars), maxChars);
        }
        List<Chunk> chunks = pack(sections(content));
        if (chunks.size() > maxChunks) {
            throw new InputTooLargeException("Diff needs %d chunks of up to %d characters; the limit is %d chunks."
                    .formatted(chunks.size(), maxChars, maxChunks), maxChars);
        }
        return chunks;
    }

    /** One section per {@code diff --git} header; anything before the first header is its own section. */
    private List<Chunk> sections(String content) {
        String[] lines = content.split("\\R", -1);
        List<Chunk> sections = new ArrayList<>();
        String currentName = null;
        int start = 0;
        for (int i = 0; i <= lines.length; i++) {
            Matcher m = i < lines.length ? FILE_HEADER.matcher(lines[i]) : null;
            boolean header = m != null && m.find();
            if ((header || i == lines.length) && i > start) {
                sections.add(section(currentName, String.join("\n", List.of(lines).subList(start, i)), start + 1));
                start = i;
            }
            if (header) {
                currentName = m.group(2);
            }
        }
        return sections;
    }

    private Chunk section(String name, String text, int firstLine) {
        if (text.length() > maxChars) {
            throw new InputTooLargeException("Diff section for %s has %d characters; the limit per file is %d."
                    .formatted(name == null ? "(preamble)" : name, text.length(), maxChars), maxChars);
        }
        return new Chunk(name, text, firstLine, true);
    }

    private List<Chunk> pack(List<Chunk> sections) {
        List<Chunk> packed = new ArrayList<>();
        Chunk acc = null;
        for (Chunk s : sections) {
            if (acc == null) {
                acc = s;
            } else if (acc.content().length() + 1 + s.content().length() <= maxChars) {
                acc = new Chunk(joinNames(acc.fileName(), s.fileName()), acc.content() + "\n" + s.content(),
                        acc.firstLine(), true);
            } else {
                packed.add(acc);
                acc = s;
            }
        }
        if (acc != null) {
            packed.add(acc);
        }
        return packed;
    }

    private static String joinNames(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + ", " + b;
    }
}
