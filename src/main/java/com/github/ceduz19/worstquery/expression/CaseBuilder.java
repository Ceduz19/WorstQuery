package com.github.ceduz19.worstquery.expression;

import com.github.ceduz19.worstquery.internal.expression.ExpressionNodes;
import com.github.ceduz19.worstquery.internal.expression.ExpressionNodes.Branch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mutable builder for a searched CASE expression; build creates a snapshot.
 */
public final class CaseBuilder {

    private final List<Branch> branches = new ArrayList<>();
    private Expression otherwise;

    CaseBuilder() {
    }

    /**
     * Appends a WHEN/THEN branch; result objects become parameters unless expressions.
     */
    public CaseBuilder when(Condition condition, Object result) {
        branches.add(new Branch(Objects.requireNonNull(condition), ExpressionNodes.operand(result)));
        return this;
    }

    /**
     * Sets the ELSE expression; passing null binds a SQL null.
     */
    public CaseBuilder otherwise(Object result) {
        otherwise = ExpressionNodes.operand(result);
        return this;
    }

    /**
     * Snapshots a CASE expression with at least one branch.
     */
    public Expression build() {
        if (branches.isEmpty()) {
            throw new IllegalStateException("CASE requires at least one WHEN");
        }
        return ExpressionNodes.caseExpression(branches, otherwise);
    }
}
