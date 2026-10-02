package com.github.ceduz19.worstquery.dml;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.internal.expression.ExpressionNodes;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements.Assignment;
import com.github.ceduz19.worstquery.model.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UPDATE builder. Omitting WHERE intentionally updates every row.
 */
public final class UpdateBuilder {
    private final Identifier table;
    private final Map<Identifier, Expression> assignments = new LinkedHashMap<>();
    private Condition where;

    UpdateBuilder(Identifier table) {
        this.table = Objects.requireNonNull(table);
    }

    /**
     * Adds an assignment or replaces its value without changing assignment order.
     */
    public UpdateBuilder set(String column, Object value) {
        assignments.put(Identifier.ofParts(column), ExpressionNodes.operand(value));
        return this;
    }

    /**
     * Replaces the WHERE predicate.
     */
    public UpdateBuilder where(Condition condition) {
        where = Objects.requireNonNull(condition);
        return this;
    }

    /**
     * Appends an AND condition to WHERE.
     */
    public UpdateBuilder andWhere(Condition condition) {
        return where(where == null ? condition : where.and(condition));
    }

    /**
     * Returns an immutable snapshot; at least one assignment is required.
     */
    public Query build() {
        if (assignments.isEmpty()) {
            throw new IllegalStateException("UPDATE requires SET");
        }
        List<Assignment> snapshot = assignments.entrySet().stream().map(e -> new Assignment(e.getKey(), e.getValue()))
                .toList();
        return new DmlStatements.Update(table, snapshot, where);
    }
}
