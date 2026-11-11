package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.model.ReviewRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputChunkerTest {

    private static String section(String file, int lines) {
        StringBuilder sb = new StringBuilder("diff --git a/" + file + " b/" + file + "\n@@ -0,0 +1 @@");
        for (int i = 0; i < lines; i++) {
            sb.append("\n+line ").append(i);
        }
        return sb.toString();
    }

    @Test
    void contentWithinLimitIsASingleChunk() {
        List<Chunk> chunks = new InputChunker(100, 5).split(new ReviewRequest("A.java", "class A {}", false));

        assertThat(chunks).singleElement().satisfies(c -> {
            assertThat(c.firstLine()).isEqualTo(1);
            assertThat(c.fileName()).isEqualTo("A.java");
        });
    }

    @Test
    void oversizedSingleFileIsRejected() {
        assertThatThrownBy(() -> new InputChunker(10, 5).split(new ReviewRequest("A.java", "x".repeat(11), false)))
                .isInstanceOf(InputTooLargeException.class)
                .hasMessageContaining("11 characters");
    }

    @Test
    void largeDiffIsSplitAtFileBoundariesAndSmallSectionsArePacked() {
        String a = section("A.java", 3);
        String b = section("B.java", 3);
        String c = section("C.java", 30);
        String diff = a + "\n" + b + "\n" + c;
        int max = a.length() + b.length() + 1;

        List<Chunk> chunks = new InputChunker(Math.max(max, c.length()), 5).split(new ReviewRequest(null, diff, true));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).fileName()).isEqualTo("A.java, B.java");
        assertThat(chunks.get(0).content()).isEqualTo(a + "\n" + b);
        assertThat(chunks.get(1).fileName()).isEqualTo("C.java");
        assertThat(chunks.get(1).firstLine()).isEqualTo(a.lines().count() + b.lines().count() + 1);
        assertThat(chunks.get(1).content()).isEqualTo(c);
    }

    @Test
    void chunkLinesMapBackToRequestLines() {
        Chunk chunk = new Chunk("B.java", "x\ny", 11, true);
        assertThat(chunk.toRequestLine(1)).isEqualTo(11);
        assertThat(chunk.toRequestLine(2)).isEqualTo(12);
        assertThat(chunk.toRequestLine(null)).isNull();
    }

    @Test
    void oversizedDiffSectionIsRejectedWithItsFileName() {
        String diff = section("Small.java", 1) + "\n" + section("Huge.java", 100);

        assertThatThrownBy(() -> new InputChunker(200, 5).split(new ReviewRequest(null, diff, true)))
                .isInstanceOf(InputTooLargeException.class)
                .hasMessageContaining("Huge.java");
    }

    @Test
    void tooManyChunksIsRejected() {
        String diff = String.join("\n", section("A.java", 20), section("B.java", 20), section("C.java", 20));
        int max = section("A.java", 20).length() + 5;

        assertThatThrownBy(() -> new InputChunker(max, 2).split(new ReviewRequest(null, diff, true)))
                .isInstanceOf(InputTooLargeException.class)
                .hasMessageContaining("3 chunks");
    }
}
