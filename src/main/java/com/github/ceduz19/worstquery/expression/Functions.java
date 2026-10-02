package com.github.ceduz19.worstquery.expression;

import com.github.ceduz19.worstquery.internal.expression.ExpressionNodes;

/**
 * SQL functions and aggregates. Function availability depends on the selected database.
 */
public final class Functions {
    private Functions() {
    }

    /**
     * Calls a trusted function name; argument expressions remain composable.
     */
    public static Expression function(String name, Expression... arguments) {
        return ExpressionNodes.function(name, false, arguments);
    }

    /**
     * Counts all rows, or the non-null values of the supplied expression.
     */
    public static Expression count() {
        return function("COUNT", Expressions.star());
    }

    /**
     * Counts all rows, or the non-null values of the supplied expression.
     */
    public static Expression count(Expression value) {
        return function("COUNT", value);
    }

    /**
     * Counts distinct non-null values of an expression.
     */
    public static Expression countDistinct(Expression value) {
        return ExpressionNodes.function("COUNT", true, value);
    }

    /**
     * Creates a SUM aggregate.
     */
    public static Expression sum(Expression value) {
        return function("SUM", value);
    }

    /**
     * Creates an AVG aggregate.
     */
    public static Expression avg(Expression value) {
        return function("AVG", value);
    }

    /**
     * Creates a MIN aggregate.
     */
    public static Expression min(Expression value) {
        return function("MIN", value);
    }

    /**
     * Creates a MAX aggregate.
     */
    public static Expression max(Expression value) {
        return function("MAX", value);
    }

    /**
     * Creates ROW_NUMBER(); attach a window using {@code over}.
     */
    public static Expression rowNumber() {
        return function("ROW_NUMBER");
    }
}
