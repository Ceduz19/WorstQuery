package com.github.ceduz19.worstquery.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SQL and its positional JDBC-style parameters in textual order.
 * Inlined rendering embeds values in the SQL and returns an empty parameter list.
 * The list is immutable; mutable application values themselves are not deep copied.
 */
public record RenderedQuery(String sql, List<Object> parameters) {
    public RenderedQuery {
        Objects.requireNonNull(sql, "sql");
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }
}
