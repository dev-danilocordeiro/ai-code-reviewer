package com.danilocordeiro.reviewer.api;

import com.danilocordeiro.reviewer.agent.CodeReviewerAgent;
import com.danilocordeiro.reviewer.model.ReviewReport;
import com.danilocordeiro.reviewer.model.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final CodeReviewerAgent agent;

    public ReviewController(CodeReviewerAgent agent) {
        this.agent = agent;
    }

    @PostMapping(path = "/review", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ReviewReport review(@Valid @RequestBody ReviewRequest request) {
        return agent.review(request);
    }
}
