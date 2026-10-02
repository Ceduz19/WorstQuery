package com.github.ceduz19.worstquery.internal.expression;

import com.github.ceduz19.worstquery.api.SelectQuery;
import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.internal.Checks;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Factories for immutable expression nodes.
 */
public final class ExpressionNodes {

    private ExpressionNodes() {
    }

    public static Expression value(Object value) {
        return c -> c.value(value);
    }

    public static Expression operand(Object value) {
        return value instanceof Expression e ? e : value(value);
    }

    public enum Comparison {
        EQUAL("="), NOT_EQUAL("<>"), GREATER(">"), GREATER_OR_EQUAL(">="),
        LESS("<"), LESS_OR_EQUAL("<="), LIKE("LIKE"), NOT_LIKE("NOT LIKE");

        private final String sql;

        Comparison(String sql) {
            this.sql = sql;
        }
    }

    public enum Arithmetic {
        ADD("+"), SUBTRACT("-"), MULTIPLY("*"), DIVIDE("/");

        private final String sql;

        Arithmetic(String sql) {
            this.sql = sql;
        }
    }

    public static Condition compare(Expression left, Comparison operator, Object right) {
        Objects.requireNonNull(left);
        Objects.requireNonNull(operator);

        if (right == null) {
            if (operator == Comparison.EQUAL) {
                return left.isNull();
            }
            if (operator == Comparison.NOT_EQUAL) {
                return left.isNotNull();
            }
            throw new IllegalArgumentException("NULL only supports equality/null checks");
        }

        Expression operand = operand(right);
        return c -> c.sql("(").part(left).sql(" " + operator.sql + " ").part(operand).sql(")");
    }

    public static Expression arithmetic(Expression left, Arithmetic operator, Object right) {
        Objects.requireNonNull(left);
        Objects.requireNonNull(operator);
        Expression operand = operand(right);
        return c -> c.sql("(").part(left).sql(" " + operator.sql + " ").part(operand).sql(")");
    }

    public static Condition between(Expression value, Object lower, Object upper) {
        Objects.requireNonNull(value);
        Expression lowerBound = operand(lower);
        Expression upperBound = operand(upper);

        return writer -> writer.sql("(").part(value).sql(" BETWEEN ")
                .part(lowerBound).sql(" AND ").part(upperBound).sql(")");
    }

    public static Condition in(Expression value, boolean negate, Object... values) {
        Objects.requireNonNull(value);
        List<Expression> operands = Arrays.stream(values).map(ExpressionNodes::operand).toList();
        if (operands.isEmpty()) {
            return c -> c.sql(negate ? "(1 = 1)" : "(1 = 0)");
        }
        return c -> c.sql("(").part(value).sql(negate ? " NOT IN (" : " IN (")
                .separated(operands, ", ").sql("))");
    }

    public static Condition inQuery(Expression value, boolean negate, SelectQuery query) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(query);
        return c -> c.sql("(").part(value).sql(negate ? " NOT IN (" : " IN (").subquery(query).sql("))");
    }

    public static Condition exists(SelectQuery query) {
        Objects.requireNonNull(query);
        return c -> c.sql("EXISTS (").subquery(query).sql(")");
    }

    /**
     * Function names are syntax tokens, arguments are explicit expressions.
     */
    public static Expression function(String name, boolean distinct, Expression... arguments) {
        String function = Checks.token(name, "function name");
        List<Expression> args = List.of(arguments);
        if (distinct && args.isEmpty()) {
            throw new IllegalArgumentException("DISTINCT requires arguments");
        }
        return c -> c.sql(function).sql("(").sql(distinct ? "DISTINCT " : "").separated(args, ", ").sql(")");
    }

    public record Branch(Condition condition, Expression result) {
    }

    public static Expression caseExpression(List<Branch> branches, Expression otherwise) {
        List<Branch> snapshot = List.copyOf(branches);
        return writer -> {
            writer.sql("CASE");
            for (Branch branch : snapshot) {
                writer.sql(" WHEN ").part(branch.condition()).sql(" THEN ").part(branch.result());
            }
            if (otherwise != null) {
                writer.sql(" ELSE ").part(otherwise);
            }
            writer.sql(" END");
        };
    }
}
