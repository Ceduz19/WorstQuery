package com.github.ceduz19.worstquery.dml;

import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.model.Identifier;

/**
 * Entry points for data queries. Each call creates an independent mutable builder.
 */
public final class Queries {

    private Queries() {
    }

    /**
     * Starts a SELECT builder with explicit projection expressions.
     */
    public static SelectBuilder select(Expression... columns) {
        return new SelectBuilder(columns);
    }

    /**
     * Starts an INSERT builder for a qualified table name.
     */
    public static InsertBuilder insertInto(String table) {
        return new InsertBuilder(Identifier.of(table));
    }

    /**
     * Starts an UPDATE builder; omitting WHERE is permitted.
     */
    public static UpdateBuilder update(String table) {
        return new UpdateBuilder(Identifier.of(table));
    }

    /**
     * Starts a DELETE builder; omitting WHERE is permitted.
     */
    public static DeleteBuilder deleteFrom(String table) {
        return new DeleteBuilder(Identifier.of(table));
    }
}
