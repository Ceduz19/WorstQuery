package com.github.ceduz19.worstquery.internal;

import com.github.ceduz19.worstquery.api.SelectQuery;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.model.SetOperator;
import com.github.ceduz19.worstquery.model.TableSource;

import java.util.Objects;

/**
 * Internal factories for immutable compound nodes.
 */
public final class Nodes {
    private Nodes() {
    }

    public static TableSource derived(SelectQuery query, String alias) {
        Objects.requireNonNull(query);
        Identifier name = Identifier.ofParts(alias);
        return c -> c.sql("(").subquery(query).sql(")").sql(c.dialect().tableAliasSeparator()).part(name);
    }

    public static SelectQuery set(SelectQuery left, SetOperator operator, SelectQuery right) {
        Objects.requireNonNull(left);
        Objects.requireNonNull(operator);
        Objects.requireNonNull(right);
        // Wrap only compound/ordered operands; a recursive CTE member must stay direct.
        return c -> {
            String operation = c.dialect().setOperator(operator);
            if (requiresWrapping(left)) {
                c.sql("SELECT * FROM ").part(derived(left, "wq_left"));
            } else {
                c.part(left);
            }
            c.sql(" " + operation + " ");
            if (requiresWrapping(right)) {
                c.sql("SELECT * FROM ").part(derived(right, "wq_right"));
            } else {
                c.part(right);
            }
        };
    }

    private static boolean requiresWrapping(SelectQuery query) {
        return !(query instanceof SetOperand operand) || operand.requiresSetOperandWrapping();
    }
}
