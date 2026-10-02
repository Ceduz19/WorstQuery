package com.github.ceduz19.worstquery.model;

import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.Arrays;
import java.util.List;

/**
 * An identifier whose parts are quoted separately; use ofParts for literal dots in a name.
 */
public record Identifier(List<String> parts) implements Expression {

    public Identifier {
        parts = List.copyOf(parts);
        if (parts.isEmpty() || parts.stream().anyMatch(p -> p.isBlank() || p.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("Identifier parts must be nonblank and contain no NUL");
        }
    }

    public static Identifier of(String qualifiedName) {
        return new Identifier(Arrays.asList(qualifiedName.split("\\.", -1)));
    }

    public static Identifier ofParts(String... parts) {
        return new Identifier(Arrays.asList(parts));
    }

    @Override
    public void appendTo(SqlWriter context) {
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                context.sql(".");
            }
            context.identifier(parts.get(i));
        }
    }
}
