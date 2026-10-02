package com.github.ceduz19.worstquery.expression;

import com.github.ceduz19.worstquery.api.SelectQuery;
import com.github.ceduz19.worstquery.internal.expression.ExpressionNodes;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.model.Table;

/**
 * Factories for references, bind values, predicates, CASE expressions, and windows.
 */
public final class Expressions {
    private Expressions() {
    }

    /**
     * Creates a table reference with separately quoted name components.
     */
    public static Table table(String name) {
        return new Table(Identifier.of(name), null);
    }

    /**
     * Explicit parts allow identifiers containing literal dots, spaces, or quote characters.
     */
    public static Identifier identifier(String... parts) {
        return Identifier.ofParts(parts);
    }

    /**
     * Registers a value at rendering time without converting it to SQL text.
     */
    public static Expression param(Object value) {
        return ExpressionNodes.value(value);
    }

    /**
     * Creates an unqualified or table-qualified wildcard projection.
     */
    public static Expression star() {
        return c -> c.sql("*");
    }

    /**
     * Creates an unqualified or table-qualified wildcard projection.
     */
    public static Expression star(String qualifier) {
        Identifier name = Identifier.of(qualifier);
        return c -> c.part(name).sql(".*");
    }

    /**
     * Creates an EXISTS predicate for a row-returning query.
     */
    public static Condition exists(SelectQuery query) {
        return ExpressionNodes.exists(query);
    }

    /**
     * Creates a NOT EXISTS predicate for a row-returning query.
     */
    public static Condition notExists(SelectQuery query) {
        return exists(query).not();
    }

    /**
     * Creates a portable predicate that is always true.
     */
    public static Condition alwaysTrue() {
        return c -> c.sql("(1 = 1)");
    }

    /**
     * Creates a portable predicate that is always false.
     */
    public static Condition alwaysFalse() {
        return c -> c.sql("(1 = 0)");
    }

    /**
     * Starts a searched CASE builder with its first branch.
     */
    public static CaseBuilder caseWhen(Condition condition, Object result) {
        return new CaseBuilder().when(condition, result);
    }

    /**
     * Creates an empty immutable window specification.
     */
    public static Window window() {
        return Window.empty();
    }

    /**
     * Creates a reference whose qualified name is quoted one component at a time.
     */
    public static Identifier column(String name) {
        return Identifier.of(name);
    }
}
