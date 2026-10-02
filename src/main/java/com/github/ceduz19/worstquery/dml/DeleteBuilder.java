package com.github.ceduz19.worstquery.dml;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements;
import com.github.ceduz19.worstquery.model.Identifier;

import java.util.Objects;

/**
 * DELETE builder. Omitting WHERE intentionally deletes every row.
 */
public final class DeleteBuilder {
    private final Identifier table;
    private Condition where;

    DeleteBuilder(Identifier table) {
        this.table = Objects.requireNonNull(table);
    }

    /**
     * Replaces the WHERE predicate.
     */
    public DeleteBuilder where(Condition condition) {
        where = Objects.requireNonNull(condition);
        return this;
    }

    /**
     * Appends an AND condition to WHERE.
     */
    public DeleteBuilder andWhere(Condition condition) {
        return where(where == null ? condition : where.and(condition));
    }

    /**
     * Returns an immutable snapshot, including the current optional WHERE predicate.
     */
    public Query build() {
        return new DmlStatements.Delete(table, where);
    }
}
