package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.config.ReviewerProperties.VerdictRule;
import com.danilocordeiro.reviewer.model.Category;
import com.danilocordeiro.reviewer.model.Finding;
import com.danilocordeiro.reviewer.model.Severity;
import com.danilocordeiro.reviewer.model.Verdict;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictPolicyTest {

    private final VerdictPolicy defaults =
            new VerdictPolicy(new VerdictRule(Severity.HIGH, Severity.INFO, Verdict.COMMENT));

    private static List<Finding> at(Severity... severities) {
        return java.util.Arrays.stream(severities)
                .map(s -> new Finding(s, Category.BUG, "t", null, null, null, "r"))
                .toList();
    }

    @ParameterizedTest
    @CsvSource({
            "CRITICAL, REQUEST_CHANGES",
            "HIGH,     REQUEST_CHANGES",
            "MEDIUM,   COMMENT",
            "LOW,      COMMENT",
            "INFO,     COMMENT"})
    void defaultRule(Severity worst, Verdict expected) {
        assertThat(defaults.decide(at(Severity.INFO, worst), false)).isEqualTo(expected);
    }

    @Test
    void approvesWhenThereAreNoFindings() {
        assertThat(defaults.decide(List.of(), false)).isEqualTo(Verdict.APPROVE);
    }

    @Test
    void thresholdsAreConfigurable() {
        VerdictPolicy strict = new VerdictPolicy(new VerdictRule(Severity.MEDIUM, Severity.LOW, Verdict.COMMENT));

        assertThat(strict.decide(at(Severity.MEDIUM), false)).isEqualTo(Verdict.REQUEST_CHANGES);
        assertThat(strict.decide(at(Severity.LOW), false)).isEqualTo(Verdict.COMMENT);
        assertThat(strict.decide(at(Severity.INFO), false)).isEqualTo(Verdict.APPROVE);
    }

    @Test
    void reviewerFailureNeverReadsAsCleanApproval() {
        assertThat(defaults.decide(List.of(), true)).isEqualTo(Verdict.COMMENT);
        assertThat(defaults.decide(at(Severity.HIGH), true)).isEqualTo(Verdict.REQUEST_CHANGES);
    }

    @Test
    void reviewerFailurePolicyIsConfigurable() {
        VerdictPolicy failClosed = new VerdictPolicy(new VerdictRule(Severity.HIGH, Severity.INFO, Verdict.REQUEST_CHANGES));
        VerdictPolicy failOpen = new VerdictPolicy(new VerdictRule(Severity.HIGH, Severity.INFO, Verdict.APPROVE));

        assertThat(failClosed.decide(List.of(), true)).isEqualTo(Verdict.REQUEST_CHANGES);
        assertThat(failOpen.decide(List.of(), true)).isEqualTo(Verdict.APPROVE);
    }
}
