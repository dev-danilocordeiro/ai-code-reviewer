package com.danilocordeiro.reviewer.agent;

/**
 * A unit of review sent to every specialist.
 *
 * @param fileName  display name (may list several files when small diff sections are packed together)
 * @param content   text of this chunk
 * @param firstLine 1-based line of the original request content where this chunk starts
 * @param diff      whether the content is a unified diff
 */
public record Chunk(String fileName, String content, int firstLine, boolean diff) {

    public int lineCount() {
        return content.split("\\R", -1).length;
    }

    /** Maps a 1-based line inside this chunk to the 1-based line of the original request. */
    public Integer toRequestLine(Integer chunkLine) {
        return chunkLine == null ? null : firstLine + chunkLine - 1;
    }
}
