package com.danilocordeiro.reviewer.agent;

import com.danilocordeiro.reviewer.model.Category;

/** The LLM reviewers. Each persona is deliberately narrow so the three do not overlap much. */
public enum Specialist {

    SECURITY("security-reviewer", Category.SECURITY,
            "You are a senior application security reviewer. Look ONLY for: injection (SQL, NoSQL, OS command, "
                    + "LDAP, template), XSS, SSRF, unsafe deserialization, broken authentication or authorization, "
                    + "weak or misused cryptography, unsafe file handling (path traversal, uploads, permissions), "
                    + "sensitive data written to logs, and missing validation of untrusted input."),

    PERFORMANCE("performance-reviewer", Category.PERFORMANCE,
            "You are a senior performance engineer. Look ONLY for: N+1 queries, unbounded collections or queries, "
                    + "blocking calls on hot paths, missing pagination or caching where it clearly matters, "
                    + "resource leaks (connections, streams, threads), and concurrency problems "
                    + "(races, contention, unsafe shared mutable state)."),

    MAINTAINABILITY("maintainability-reviewer", Category.MAINTAINABILITY,
            "You are a senior Java and Spring maintainer. Look ONLY for: SOLID violations, oversized classes or "
                    + "methods, duplicated logic, misleading names, swallowed exceptions, non-trivial logic added "
                    + "without tests, and misuse of Spring or JPA (field injection, transactional pitfalls, "
                    + "exposing entities, lazy-loading traps).");

    private final String id;
    private final Category defaultCategory;
    private final String persona;

    Specialist(String id, Category defaultCategory, String persona) {
        this.id = id;
        this.defaultCategory = defaultCategory;
        this.persona = persona;
    }

    public String id() {
        return id;
    }

    public Category defaultCategory() {
        return defaultCategory;
    }

    public String persona() {
        return persona;
    }
}
