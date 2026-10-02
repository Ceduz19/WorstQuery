package com.github.ceduz19.worstquery.internal;

import java.util.List;
import java.util.Objects;

/**
 * Internal structural validation.
 */
public final class Checks {
    private Checks() {
    }

    public static <T> List<T> nonEmpty(List<T> items, String what) {
        List<T> copy = List.copyOf(items);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        return copy;
    }

    public static String token(String text, String what) {
        Objects.requireNonNull(text, what);
        if (!text.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
            throw new IllegalArgumentException("Invalid " + what + ": " + text);
        }
        return text;
    }
}
