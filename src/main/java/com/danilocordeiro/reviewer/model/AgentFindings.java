package com.danilocordeiro.reviewer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Structured output that each specialist model call is mapped to. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentFindings(List<Finding> findings) {

    public AgentFindings {
        findings = findings == null ? List.of() : List.copyOf(findings.stream().filter(f -> f != null).toList());
    }
}
