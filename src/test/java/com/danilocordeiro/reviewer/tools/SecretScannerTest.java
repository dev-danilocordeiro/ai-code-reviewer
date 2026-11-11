package com.danilocordeiro.reviewer.tools;

import com.danilocordeiro.reviewer.model.Category;
import com.danilocordeiro.reviewer.model.Finding;
import com.danilocordeiro.reviewer.model.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SecretScannerTest {

    // Fake secrets are assembled at runtime so this file never contains a literal that
    // secret-scanning tools (including GitHub push protection) would flag.
    private static final String AWS_KEY = "AKIA" + "Q3EGRZ7XJ2LMN4PB";
    private static final String GITHUB_TOKEN = "ghp" + "_" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8";
    private static final String GITHUB_PAT = "github" + "_pat_" + "11ABCDEFG0123456789_abcdefghijklmnop";
    private static final String PRIVATE_KEY_HEADER = "-----BEGIN " + "RSA PRIVATE KEY-----";
    private static final String PASSWORD = "s3cr3t-" + "Hunter2!";

    private final SecretScanner scanner = new SecretScanner();

    static Stream<Arguments> positives() {
        return Stream.of(
                Arguments.of("aws-access-key", "String key = \"" + AWS_KEY + "\";"),
                Arguments.of("private-key", PRIVATE_KEY_HEADER),
                Arguments.of("private-key", "-----BEGIN " + "OPENSSH PRIVATE KEY-----"),
                Arguments.of("github-token", "token: " + GITHUB_TOKEN),
                Arguments.of("github-token", "auth " + GITHUB_PAT),
                Arguments.of("hardcoded-credential", "String password = \"" + PASSWORD + "\";"),
                Arguments.of("hardcoded-credential", "private static final String API_KEY = \"" + PASSWORD + "\";"),
                Arguments.of("hardcoded-credential", "{\"client_secret\": \"" + PASSWORD + "\"}"),
                Arguments.of("hardcoded-credential", "this.authToken = '" + PASSWORD + "';"),
                Arguments.of("hardcoded-credential-config", "spring.datasource.password=" + PASSWORD),
                Arguments.of("hardcoded-credential-config", "  api-key: " + PASSWORD),
                Arguments.of("hardcoded-credential-config", "export DB_PASSWORD=" + PASSWORD));
    }

    @ParameterizedTest
    @MethodSource("positives")
    void detectsEachRule(String rule, String line) {
        List<Finding> findings = scanner.scan("line one\n" + line + "\nline three", false);

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(f.category()).isEqualTo(Category.SECURITY);
            assertThat(f.line()).isEqualTo(2);
            assertThat(f.reviewer()).isEqualTo(SecretScanner.REVIEWER_ID);
            assertThat(f.title()).contains("(" + rule + ")");
        });
    }

    @ParameterizedTest
    @MethodSource("negatives")
    void ignoresNonSecrets(String line) {
        assertThat(scanner.scan(line, false)).isEmpty();
    }

    static Stream<String> negatives() {
        return Stream.of(
                "String password = request.getParameter(\"password\");",
                "spring.datasource.password=${DB_PASSWORD}",
                "password: ${DB_PASSWORD:}",
                "private static final String TOKEN_HEADER = \"Authorization\";",
                "if (token == null) return;",
                "String pwd = \"\";",
                "AKIA-not-a-key",
                "// rotate the api key every 90 days");
    }

    @Test
    void secretValueNeverLeaksIntoFindingsOrLogs(CapturedOutput output) {
        String content = String.join("\n",
                "aws = \"" + AWS_KEY + "\"",
                "gh = " + GITHUB_TOKEN,
                "String password = \"" + PASSWORD + "\";",
                "db.password=" + PASSWORD);

        List<Finding> findings = scanner.scan(content, false);

        assertThat(findings).hasSize(4);
        String rendered = findings.toString();
        for (String secret : List.of(AWS_KEY, GITHUB_TOKEN, PASSWORD)) {
            assertThat(rendered).doesNotContain(secret);
            assertThat(output.getAll()).doesNotContain(secret);
        }
        assertThat(output.getAll()).contains("4 potential secret(s)");
    }

    @Test
    void diffModeOnlyScansAddedLines() {
        String diff = String.join("\n",
                "diff --git a/App.java b/App.java",
                "--- a/App.java",
                "+++ b/App.java",
                "@@ -1,3 +1,3 @@",
                "-String password = \"" + PASSWORD + "\";",
                " unchanged",
                "+String apiKey = \"" + PASSWORD + "\";");

        assertThat(scanner.scan(diff, true))
                .singleElement()
                .extracting(Finding::line)
                .isEqualTo(7);
    }

    @Test
    void reportsEveryAffectedLine() {
        String content = "a = \"" + AWS_KEY + "\"\nnothing\nb = \"" + AWS_KEY + "\"";
        assertThat(scanner.scan(content, false)).extracting(Finding::line).containsExactly(1, 3);
    }
}
