package com.example.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Slugs {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final int MAX_LENGTH = 80;

    private Slugs() {
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String ascii = DIACRITICS.matcher(Normalizer.normalize(input, Normalizer.Form.NFD)).replaceAll("");
        String slug = NON_ALNUM.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("(^-+|-+$)", "");
        return slug.length() > MAX_LENGTH ? slug.substring(0, MAX_LENGTH).replaceAll("-+$", "") : slug;
    }
}
