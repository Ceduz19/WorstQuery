package com.github.ceduz19.worstquery.expression;

import com.github.ceduz19.worstquery.api.SelectQuery;
import com.github.ceduz19.worstquery.internal.expression.ExpressionNodes;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.SqlPart;

/**
 * An immutable SQL expression. Object operands become bind parameters unless already expressions.
 */
@FunctionalInterface
public interface Expression extends SqlPart {
    /**
     * Compares for equality; Java null becomes IS NULL.
     */
    default Condition eq(Object other) {
        return ExpressionNodes.compare(this, ExpressionNodes.Comparison.EQUAL, other);
    }

    /**
     * Compares for inequality; Java null becomes IS NOT NULL.
     */
    default Condition ne(Object other) {
        return ExpressionNodes.compare(this, ExpressionNodes.Comparison.NOT_EQUAL, other);
    }

    /**
     * Compares using greater-than; Java null is rejected.
     */
    default Condition gt(Object other) {
        return ExpressionNodes.compare(this, ExpressionNodes.Comparison.GREATER, other);
    }

    /**
     * Compares using greater-than-or-equal; Java null is rejected.
     */
    default Condition ge(Object other) {
        return ExpressionNodes.compare(this, ExpressionNodes.Comparison.GREATER_OR_EQUAL, other);
    }

    /**
     * Compares using less-than; Java null is rejected.
     */
    default Condition lt(Object other) {
        return ExpressionNodes.compare(this, ExpressionNodes.Comparison.LESS, other);
    }

    /**
     * Compares using less-than-or-equal; Java null is rejected.
     */
    default Condition le(Object other) {
        return ExpressionNodes.compare(this, ExpressionNodes.Comparison.LESS_OR_EQUAL, other);
    }

    /**
     * Tests whether this expression is null.
     */
    default Condition isNull() {
        return c -> c.sql("(").part(this).sql(" IS NULL)");
    }

    /**
     * Tests whether this expression is not null.
     */
    default Condition isNotNull() {
        return c -> c.sql("(").part(this).sql(" IS NOT NULL)");
    }

    /**
     * Creates a LIKE predicate; the caller supplies any pattern wildcards.
     */
    default Condition like(Object pattern) {
        return ExpressionNodes.compare(this, ExpressionNodes.Comparison.LIKE, pattern);
    }

    /**
     * Creates a NOT LIKE predicate.
     */
    default Condition notLike(Object pattern) {
        return ExpressionNodes.compare(this, ExpressionNodes.Comparison.NOT_LIKE, pattern);
    }

    /**
     * Creates an inclusive BETWEEN predicate.
     */
    default Condition between(Object lower, Object upper) {
        return ExpressionNodes.between(this, lower, upper);
    }

    /**
     * Creates IN membership; an empty value list is always false.
     */
    default Condition in(Object... values) {
        return ExpressionNodes.in(this, false, values);
    }

    /**
     * Creates NOT IN membership; an empty value list is always true.
     */
    default Condition notIn(Object... values) {
        return ExpressionNodes.in(this, true, values);
    }

    /**
     * Creates IN membership; an empty value list is always false.
     */
    default Condition in(SelectQuery query) {
        return ExpressionNodes.inQuery(this, false, query);
    }

    /**
     * Creates NOT IN membership; an empty value list is always true.
     */
    default Condition notIn(SelectQuery query) {
        return ExpressionNodes.inQuery(this, true, query);
    }

    /**
     * Creates a parenthesized addition expression.
     */
    default Expression plus(Object other) {
        return ExpressionNodes.arithmetic(this, ExpressionNodes.Arithmetic.ADD, other);
    }

    /**
     * Creates a parenthesized subtraction expression.
     */
    default Expression minus(Object other) {
        return ExpressionNodes.arithmetic(this, ExpressionNodes.Arithmetic.SUBTRACT, other);
    }

    /**
     * Creates a parenthesized multiplication expression.
     */
    default Expression multiply(Object other) {
        return ExpressionNodes.arithmetic(this, ExpressionNodes.Arithmetic.MULTIPLY, other);
    }

    /**
     * Creates a parenthesized division expression.
     */
    default Expression divide(Object other) {
        return ExpressionNodes.arithmetic(this, ExpressionNodes.Arithmetic.DIVIDE, other);
    }

    /**
     * Adds a quoted projection alias; use the unaliased expression in predicates.
     */
    default Expression as(String alias) {
        Identifier name = Identifier.ofParts(alias);
        return c -> c.part(this).sql(" AS ").part(name);
    }

    /**
     * Attaches an immutable window specification to a compatible function.
     */
    default Expression over(Window window) {
        java.util.Objects.requireNonNull(window);
        return c -> c.part(this).sql(" OVER (").part(window).sql(")");
    }

    /**
     * Creates an ascending sort key.
     */
    default Sort asc() {
        return new Sort(this, Sort.Direction.ASC);
    }

    /**
     * Creates a descending sort key.
     */
    default Sort desc() {
        return new Sort(this, Sort.Direction.DESC);
    }
}
