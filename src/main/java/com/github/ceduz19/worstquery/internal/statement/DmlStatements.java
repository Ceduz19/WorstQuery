package com.github.ceduz19.worstquery.internal.statement;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.api.SelectQuery;
import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.expression.Sort;
import com.github.ceduz19.worstquery.internal.SetOperand;
import com.github.ceduz19.worstquery.internal.render.DmlRenderer;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.model.JoinType;
import com.github.ceduz19.worstquery.model.TableSource;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.List;
import java.util.Objects;

/**
 * Immutable statement snapshots shared by builders and the DML renderer.
 */
public final class DmlStatements {
    private DmlStatements() {
    }

    public record Join(JoinType type, TableSource table, Condition on) {
        public Join {
            Objects.requireNonNull(type);
            Objects.requireNonNull(table);
            if (type == JoinType.CROSS && on != null) {
                throw new IllegalArgumentException("CROSS JOIN does not accept ON");
            }
            if (type != JoinType.CROSS && on == null) {
                throw new IllegalArgumentException("JOIN requires ON");
            }
        }
    }

    public record Cte(Identifier name, SelectQuery query, List<Identifier> columns, boolean recursive) {
        public Cte {
            columns = List.copyOf(columns);
        }
    }

    public record Assignment(Identifier column, Expression value) {
    }

    public record Select(
            List<Expression> columns, TableSource from, List<Join> joins, Condition where,
            List<Expression> groups, Condition having, List<Sort> ordering, List<Cte> ctes,
            boolean distinct, Long limit, Long offset
    ) implements SelectQuery, SetOperand {
        public Select {
            columns = List.copyOf(columns);
            joins = List.copyOf(joins);
            groups = List.copyOf(groups);
            ordering = List.copyOf(ordering);
            ctes = List.copyOf(ctes);
        }

        @Override
        public boolean requiresSetOperandWrapping() {
            return !ordering.isEmpty() || !ctes.isEmpty() || limit != null || offset != null;
        }

        @Override
        public void appendTo(SqlWriter writer) {
            DmlRenderer.render(this, writer);
        }
    }

    public record Insert(
            Identifier table, List<Identifier> columns, List<List<Expression>> rows, SelectQuery source
    ) implements Query {
        public Insert {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
        }

        @Override
        public void appendTo(SqlWriter writer) {
            DmlRenderer.render(this, writer);
        }
    }

    public record Update(Identifier table, List<Assignment> assignments, Condition where) implements Query {
        public Update {
            assignments = List.copyOf(assignments);
        }

        @Override
        public void appendTo(SqlWriter writer) {
            DmlRenderer.render(this, writer);
        }
    }

    public record Delete(Identifier table, Condition where) implements Query {
        @Override
        public void appendTo(SqlWriter writer) {
            DmlRenderer.render(this, writer);
        }
    }
}
