package com.github.ceduz19.worstquery.api;

import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.internal.Nodes;
import com.github.ceduz19.worstquery.model.SetOperator;
import com.github.ceduz19.worstquery.model.TableSource;

/**
 * A query returning rows, usable as a subquery or set-operation operand.
 */
@FunctionalInterface
public interface SelectQuery extends Query {
    /**
     * Wraps this row query as a scalar subquery; the database checks its cardinality.
     */
    default Expression scalar() {
        return c -> c.sql("(").subquery(this).sql(")");
    }

    /**
     * Wraps this query as a derived table with a quoted alias.
     */
    default TableSource as(String alias) {
        return Nodes.derived(this, alias);
    }

    /**
     * Combines rows with duplicate elimination.
     */
    default SelectQuery union(SelectQuery other) {
        return combine(SetOperator.UNION, other);
    }

    /**
     * Combines rows while retaining duplicates.
     */
    default SelectQuery unionAll(SelectQuery other) {
        return combine(SetOperator.UNION_ALL, other);
    }

    /**
     * Returns distinct rows common to both queries.
     */
    default SelectQuery intersect(SelectQuery other) {
        return combine(SetOperator.INTERSECT, other);
    }

    /**
     * Returns common rows with multiplicity where the dialect supports it.
     */
    default SelectQuery intersectAll(SelectQuery other) {
        return combine(SetOperator.INTERSECT_ALL, other);
    }

    /**
     * Subtracts the other query with duplicate elimination.
     */
    default SelectQuery except(SelectQuery other) {
        return combine(SetOperator.EXCEPT, other);
    }

    /**
     * Subtracts the other query with multiplicity where supported.
     */
    default SelectQuery exceptAll(SelectQuery other) {
        return combine(SetOperator.EXCEPT_ALL, other);
    }

    /**
     * Composes queries while preserving the specified set-operation grouping.
     */
    default SelectQuery combine(SetOperator operator, SelectQuery other) {
        return Nodes.set(this, operator, other);
    }
}
