package com.danilocordeiro.reviewer.tools;

import com.danilocordeiro.reviewer.model.Category;
import com.danilocordeiro.reviewer.model.Finding;
import com.danilocordeiro.reviewer.model.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Regex-based secret detector that runs before any model call.
 *
 * <p>Findings carry the rule and the line only. The matched value is never copied into a finding,
 * an exception, or a log statement.
 */
@Component
public class SecretScanner {

    public static final String REVIEWER_ID = "secret-scanner";

    private static final Logger log = LoggerFactory.getLogger(SecretScanner.class);

    private static final String CREDENTIAL_KEY =
            "[A-Za-z0-9_.-]*(?i:password|passwd|pwd|secret|api[_-]?key|access[_-]?token|auth[_-]?token|token)";

    public static final List<SecretRule> DEFAULT_RULES = List.of(
            SecretRule.of("aws-access-key", "AWS access key ID",
                    "\\b(?:AKIA|ASIA|ABIA|ACCA)[0-9A-Z]{16}\\b"),
            SecretRule.of("private-key", "private key block",
                    "-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY(?: BLOCK)?-----"),
            SecretRule.of("github-token", "GitHub token",
                    "\\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{22,255})\\b"),
            // Code: password = "literal", apiKey: 'literal', "token": "literal"
            SecretRule.of("hardcoded-credential", "hardcoded credential",
                    "\\b" + CREDENTIAL_KEY + "[\"']?\\s*(?::=|[:=])\\s*([\"'])(?!\\$\\{)[^\"'\\s]{6,}\\1"),
            // Config: password=literal or secret: literal (properties / YAML / .env), excluding ${placeholders}
            SecretRule.of("hardcoded-credential-config", "hardcoded credential in configuration",
                    "^\\s*(?:export\\s+)?" + CREDENTIAL_KEY + "\\s*[:=]\\s*(?![\"'$<{(\\[])[^\\s#]{6,}\\s*$"));

    private final List<SecretRule> rules;

    public SecretScanner() {
        this(DEFAULT_RULES);
    }

    public SecretScanner(List<SecretRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * Scans {@code content}. For diffs only added lines ({@code +}, excluding the {@code +++} header) are checked,
     * since removed lines are on their way out of the code base.
     */
    public List<Finding> scan(String content, boolean isDiff) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isDiff && (!line.startsWith("+") || line.startsWith("+++"))) {
                continue;
            }
            for (SecretRule rule : rules) {
                if (rule.pattern().matcher(line).find()) {
                    findings.add(toFinding(rule, i + 1));
                    break; // one finding per line is enough; the value is never reported anyway
                }
            }
        }
        if (!findings.isEmpty()) {
            log.warn("secret-scanner: {} potential secret(s) at lines {} (rules: {})",
                    findings.size(),
                    findings.stream().map(Finding::line).toList(),
                    findings.stream().map(Finding::title).distinct().toList());
        }
        return findings;
    }

    private static Finding toFinding(SecretRule rule, int line) {
        return new Finding(
                Severity.CRITICAL,
                Category.SECURITY,
                "Possible " + rule.description() + " committed (" + rule.name() + ")",
                "A value matching the " + rule.name() + " rule appears on this line. "
                        + "Anything pushed to a repository must be treated as leaked, even after it is removed.",
                line,
                "Remove the value from the code, rotate the credential, and load it at runtime from an "
                        + "environment variable or a secret manager.",
                REVIEWER_ID);
    }
}
