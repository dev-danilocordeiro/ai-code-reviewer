package com.danilocordeiro.reviewer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param fileName optional name used for context in the prompt
 * @param content  source file or unified diff
 * @param isDiff   {@code true} when {@code content} is a unified diff; optional, defaults to {@code false}
 */
public record ReviewRequest(
        @Size(max = 255) String fileName,
        @NotBlank String content,
        @JsonProperty("isDiff") Boolean isDiff) {

    public ReviewRequest {
        isDiff = Boolean.TRUE.equals(isDiff);
    }
}
