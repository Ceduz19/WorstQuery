package com.github.ceduz19.worstquery.expression;

import java.util.Objects;

/**
 * A boolean predicate with explicit grouping.
 */
@FunctionalInterface
public interface Condition extends Expression {
    /**
     * Combines predicates using AND with explicit grouping.
     */
    default Condition and(Condition other) {
        Objects.requireNonNull(other);
        return c -> c.sql("(").part(this).sql(" AND ").part(other).sql(")");
    }

    /**
     * Combines predicates using OR with explicit grouping.
     */
    default Condition or(Condition other) {
        Objects.requireNonNull(other);
        return c -> c.sql("(").part(this).sql(" OR ").part(other).sql(")");
    }

    /**
     * Negates this grouped predicate.
     */
    default Condition not() {
        return c -> c.sql("(NOT ").part(this).sql(")");
    }
}
