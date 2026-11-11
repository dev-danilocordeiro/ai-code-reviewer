package com.danilocordeiro.reviewer.tools;

import java.util.regex.Pattern;

/**
 * A deterministic secret detection rule applied line by line.
 *
 * @param name        stable id of the rule, e.g. {@code aws-access-key}
 * @param description human-readable label used in the finding title; must never contain the matched value
 * @param pattern     regex matched against a single line
 */
public record SecretRule(String name, String description, Pattern pattern) {

    public static SecretRule of(String name, String description, String regex) {
        return new SecretRule(name, description, Pattern.compile(regex));
    }
}
