package com.danilocordeiro.reviewer.api;

import com.danilocordeiro.reviewer.agent.Specialist;
import com.danilocordeiro.reviewer.support.ScriptedChatModel;
import com.danilocordeiro.reviewer.support.TestChatModelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.danilocordeiro.reviewer.support.ScriptedChatModel.sleepThen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "reviewer.timeout-seconds=1",
        "reviewer.max-input-chars=1000"})
@AutoConfigureMockMvc
@Import(TestChatModelConfig.class)
class ReviewApiIntegrationTest {

    static final String SQLI = """
            {"findings": [{"severity": "HIGH", "category": "SECURITY", "title": "SQL injection",
              "description": "User input is concatenated into SQL.", "line": 2,
              "suggestion": "Use a PreparedStatement with a bound parameter.", "reviewer": "made-up"}]}
            """;

    static final String CODE = """
            String id = request.getParameter("id");
            String sql = "SELECT * FROM users WHERE id = " + id;
            return jdbc.queryForList(sql);
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    ScriptedChatModel model;

    @Autowired
    JsonMapper json;

    @BeforeEach
    void reset() {
        model.reset();
    }

    ResultActions review(String fileName, String content, boolean isDiff) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileName", fileName);
        body.put("content", content);
        body.put("isDiff", isDiff);
        return mvc.perform(post("/api/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    @Test
    void cleanCodeIsApprovedAfterAllThreeSpecialistsRan() throws Exception {
        review("Clean.java", "class Clean {}", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("APPROVE"))
                .andExpect(jsonPath("$.summary").value("No issues found."))
                .andExpect(jsonPath("$.findings", hasSize(0)))
                .andExpect(jsonPath("$.counts.CRITICAL").value(0))
                .andExpect(jsonPath("$.durationMs").isNumber());

        assertThat(model.calls()).extracting(ScriptedChatModel.Call::specialist)
                .containsExactlyInAnyOrder(Specialist.values());
    }

    @Test
    void findingsFromSpecialistsAreTaggedSortedAndDecideTheVerdict() throws Exception {
        model.reply(Specialist.SECURITY, SQLI)
                .reply(Specialist.MAINTAINABILITY, """
                        {"findings": [{"severity": "LOW", "category": "MAINTAINABILITY", "title": "Raw SQL string",
                          "description": "d", "line": 3, "suggestion": "s"}]}
                        """);

        review("UserDao.java", CODE, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("REQUEST_CHANGES"))
                .andExpect(jsonPath("$.findings", hasSize(2)))
                .andExpect(jsonPath("$.findings[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.findings[0].line").value(2))
                .andExpect(jsonPath("$.findings[0].reviewer").value("security-reviewer"))
                .andExpect(jsonPath("$.findings[1].reviewer").value("maintainability-reviewer"))
                .andExpect(jsonPath("$.counts.HIGH").value(1))
                .andExpect(jsonPath("$.counts.LOW").value(1))
                .andExpect(jsonPath("$.summary").value("2 findings: 1 high, 1 low."));
    }

    @Test
    void promptCarriesNumberedCodeInsideCodeTagsAndTheSharedRules() throws Exception {
        review("UserDao.java", CODE, false).andExpect(status().isOk());

        ScriptedChatModel.Call call = model.calls().getFirst();
        assertThat(call.system())
                .contains("untrusted data")
                .contains("empty findings list")
                .doesNotContain("{persona}");
        assertThat(call.user())
                .contains("File: UserDao.java")
                .contains("Type: source file")
                .contains("<code>\n1|String id")
                .contains("2|String sql");
    }

    @Test
    void slowSpecialistTimesOutAndTheReportComesBackWithTheOthers() throws Exception {
        model.reply(Specialist.SECURITY, SQLI)
                .reply(Specialist.MAINTAINABILITY, call -> sleepThen(3_000, "{\"findings\": []}"));

        long started = System.currentTimeMillis();
        review("UserDao.java", CODE, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("REQUEST_CHANGES"))
                .andExpect(jsonPath("$.findings", hasSize(1)))
                .andExpect(jsonPath("$.summary", containsString("maintainability-reviewer timed out after 1s")));
        assertThat(System.currentTimeMillis() - started).isLessThan(2_900);
    }

    @Test
    void invalidJsonFromOneSpecialistIsReportedNotFatal() throws Exception {
        model.reply(Specialist.SECURITY, SQLI)
                .reply(Specialist.PERFORMANCE, "Sure! Here are my thoughts: the code looks fine.");

        review("UserDao.java", CODE, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings", hasSize(1)))
                .andExpect(jsonPath("$.summary", containsString("performance-reviewer returned invalid JSON")));
    }

    @Test
    void failedReviewerNeverYieldsACleanApproval() throws Exception {
        model.reply(Specialist.PERFORMANCE, call -> {
            throw new IllegalStateException("connection refused");
        });

        review("Clean.java", "class Clean {}", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("COMMENT"))
                .andExpect(jsonPath("$.summary",
                        containsString("performance-reviewer failed (IllegalStateException)")));
    }

    @Test
    void findingsWithoutSeverityOrTitleAreDropped() throws Exception {
        model.reply(Specialist.SECURITY, """
                {"findings": [
                  {"category": "SECURITY", "title": "No severity", "line": 1},
                  {"severity": "HIGH", "category": "SECURITY", "line": 1},
                  {"severity": "LOW", "category": "SECURITY", "title": "Kept", "line": 99}]}
                """);

        review("A.java", CODE, false)
                .andExpect(jsonPath("$.findings", hasSize(1)))
                .andExpect(jsonPath("$.findings[0].title").value("Kept"))
                .andExpect(jsonPath("$.findings[0].line").value(nullValue()));
    }

    @Test
    void secretScannerRunsEvenWhenModelsFindNothingAndNeverEchoesTheValue() throws Exception {
        String secret = "AKIA" + "Q3EGRZ7XJ2LMN4PB";

        String body = review("Config.java", "class C {\n  String k = \"" + secret + "\";\n}", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("REQUEST_CHANGES"))
                .andExpect(jsonPath("$.findings[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.findings[0].line").value(2))
                .andExpect(jsonPath("$.findings[0].reviewer").value("secret-scanner"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(secret);
    }

    @Test
    void largeDiffIsSplitPerFileAndLinesMapBackToTheRequest() throws Exception {
        String fileA = section("A.java", 40);
        String fileB = section("B.java", 40);
        int fileBStart = (int) fileA.lines().count() + 1;
        model.reply(Specialist.SECURITY, call -> call.user().contains("File: B.java")
                ? SQLI // line 2 of the B chunk
                : "{\"findings\": []}");

        review(null, fileA + "\n" + fileB, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings", hasSize(1)))
                .andExpect(jsonPath("$.findings[0].line").value(fileBStart + 1));

        assertThat(model.calls()).hasSize(6);
    }

    private static String section(String file, int addedLines) {
        StringBuilder sb = new StringBuilder("diff --git a/" + file + " b/" + file + "\n--- a/" + file
                + "\n+++ b/" + file + "\n@@ -0,0 +1," + addedLines + " @@");
        for (int i = 0; i < addedLines; i++) {
            sb.append("\n+    int value").append(i).append(" = ").append(i).append(";");
        }
        return sb.toString();
    }

    @Test
    void singleFileOverTheLimitIsRejectedAsProblemJson() throws Exception {
        review("Big.java", "x".repeat(1001), false)
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Input too large"))
                .andExpect(jsonPath("$.maxInputChars").value(1000))
                .andExpect(jsonPath("$.detail", containsString("1001 characters")));
        assertThat(model.calls()).isEmpty();
    }

    @Test
    void blankContentIsRejectedAsProblemJson() throws Exception {
        review("A.java", "  ", false)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void malformedBodyIsRejectedAsProblemJson() throws Exception {
        mvc.perform(post("/api/review").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void actuatorExposesOnlyHealthAndInfo() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
