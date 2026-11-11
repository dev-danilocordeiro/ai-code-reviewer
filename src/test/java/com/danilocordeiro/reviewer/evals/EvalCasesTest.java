package com.danilocordeiro.reviewer.evals;

import com.danilocordeiro.reviewer.tools.SecretScanner;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the eval set well-formed: every case loads and every expected snippet exists in its source. */
class EvalCasesTest {

    @Test
    void allCasesLoad() throws Exception {
        List<EvalRunner.Case> cases = EvalRunner.load(Path.of("evals/cases"));

        assertThat(cases).hasSize(25);
        assertThat(cases.stream().filter(EvalRunner.Case::clean)).hasSize(7);
        assertThat(cases.stream().flatMap(c -> c.issues().stream()))
                .allSatisfy(i -> assertThat(i.keywords()).as(i.label()).isNotEmpty());
    }

    @Test
    void cleanCasesDoNotTripTheSecretScanner() throws Exception {
        SecretScanner scanner = new SecretScanner();
        for (EvalRunner.Case c : EvalRunner.load(Path.of("evals/cases"))) {
            if (c.clean()) {
                assertThat(scanner.scan(c.content(), c.diff())).as(c.id()).isEmpty();
            }
        }
    }
}
