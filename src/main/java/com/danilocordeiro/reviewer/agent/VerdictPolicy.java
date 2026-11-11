package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.config.ReviewerProperties.VerdictRule;
import com.danilocordeiro.reviewer.model.Finding;
import com.danilocordeiro.reviewer.model.Verdict;

import java.util.Collection;

/**
 * Turns findings into a verdict. The verdict is computed here, from data, and never taken from a model,
 * so text inside the reviewed code cannot talk its way into an approval.
 */
public class VerdictPolicy {

    private final VerdictRule rule;

    public VerdictPolicy(VerdictRule rule) {
        this.rule = rule;
    }

    public Verdict decide(Collection<Finding> findings, boolean anyReviewerFailed) {
        Verdict fromFindings = fromFindings(findings);
        if (anyReviewerFailed && fromFindings.ordinal() < rule.onReviewerFailure().ordinal()) {
            return rule.onReviewerFailure();
        }
        return fromFindings;
    }

    private Verdict fromFindings(Collection<Finding> findings) {
        if (findings.stream().anyMatch(f -> f.severity().isAtLeast(rule.requestChangesAt()))) {
            return Verdict.REQUEST_CHANGES;
        }
        if (findings.stream().anyMatch(f -> f.severity().isAtLeast(rule.commentAt()))) {
            return Verdict.COMMENT;
        }
        return Verdict.APPROVE;
    }
}
