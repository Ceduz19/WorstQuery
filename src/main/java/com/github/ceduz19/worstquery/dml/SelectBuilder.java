package com.github.ceduz19.worstquery.dml;

import com.github.ceduz19.worstquery.api.SelectQuery;
import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.expression.Sort;
import com.github.ceduz19.worstquery.internal.Checks;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements.Cte;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements.Join;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.model.JoinType;
import com.github.ceduz19.worstquery.model.Table;
import com.github.ceduz19.worstquery.model.TableSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Mutable SELECT builder. Repeated list methods append; FROM/WHERE/HAVING replace.
 */
public final class SelectBuilder {

    private final List<Expression> columns = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();
    private final List<Expression> groups = new ArrayList<>();
    private final List<Sort> ordering = new ArrayList<>();
    private final List<Cte> ctes = new ArrayList<>();
    private TableSource from;
    private Condition where;
    private Condition having;
    private boolean distinct;
    private Long limit;
    private Long offset;

    SelectBuilder(Expression... columns) {
        select(columns);
    }

    /**
     * Appends projection expressions; at least one is required by {@code build}.
     */
    public SelectBuilder select(Expression... columns) {
        this.columns.addAll(List.of(columns));
        return this;
    }

    /**
     * Enables duplicate elimination.
     */
    public SelectBuilder distinct() {
        distinct = true;
        return this;
    }

    /**
     * Replaces the FROM source.
     */
    public SelectBuilder from(String table) {
        return from(new Table(Identifier.of(table), null));
    }

    /**
     * Replaces the FROM source.
     */
    public SelectBuilder from(TableSource table) {
        from = Objects.requireNonNull(table);
        return this;
    }

    /**
     * Appends a join; all types except CROSS require an ON predicate.
     */
    public SelectBuilder join(TableSource table, Condition on) {
        return join(JoinType.INNER, table, on);
    }

    /**
     * Appends a LEFT JOIN with an ON predicate.
     */
    public SelectBuilder leftJoin(TableSource table, Condition on) {
        return join(JoinType.LEFT, table, on);
    }

    /**
     * Appends a RIGHT JOIN with an ON predicate.
     */
    public SelectBuilder rightJoin(TableSource table, Condition on) {
        return join(JoinType.RIGHT, table, on);
    }

    /**
     * Appends a FULL JOIN, checked against the selected dialect at rendering time.
     */
    public SelectBuilder fullJoin(TableSource table, Condition on) {
        return join(JoinType.FULL, table, on);
    }

    /**
     * Appends a CROSS JOIN without an ON predicate.
     */
    public SelectBuilder crossJoin(TableSource table) {
        return join(JoinType.CROSS, table, null);
    }

    /**
     * Appends a join; all types except CROSS require an ON predicate.
     */
    public SelectBuilder join(JoinType type, TableSource table, Condition on) {
        joins.add(new Join(type, table, on));
        return this;
    }

    /**
     * Replaces the WHERE predicate.
     */
    public SelectBuilder where(Condition condition) {
        where = Objects.requireNonNull(condition);
        return this;
    }

    /**
     * Combines the existing WHERE predicate with AND, or sets the first predicate.
     */
    public SelectBuilder andWhere(Condition condition) {
        return where(where == null ? condition : where.and(condition));
    }

    /**
     * Replaces the HAVING predicate.
     */
    public SelectBuilder having(Condition condition) {
        having = Objects.requireNonNull(condition);
        return this;
    }

    /**
     * Appends grouping expressions.
     */
    public SelectBuilder groupBy(Expression... expressions) {
        groups.addAll(List.of(expressions));
        return this;
    }

    /**
     * Appends sort keys.
     */
    public SelectBuilder orderBy(Sort... sorts) {
        ordering.addAll(List.of(sorts));
        return this;
    }

    /**
     * Sets a nonnegative row limit; support for zero depends on the dialect.
     */
    public SelectBuilder limit(long rows) {
        if (rows < 0) {
            throw new IllegalArgumentException("Negative limit");
        }
        limit = rows;
        return this;
    }

    /**
     * Sets a nonnegative row offset.
     */
    public SelectBuilder offset(long rows) {
        if (rows < 0) {
            throw new IllegalArgumentException("Negative offset");
        }
        offset = rows;
        return this;
    }

    /**
     * Appends a named non-recursive CTE, optionally declaring its output columns.
     */
    public SelectBuilder with(String name, SelectQuery query, String... columns) {
        return cte(name, query, false, columns);
    }

    /**
     * Appends a recursive CTE; Oracle requires explicit output column names.
     */
    public SelectBuilder withRecursive(String name, SelectQuery query, String... columns) {
        return cte(name, query, true, columns);
    }

    private SelectBuilder cte(String name, SelectQuery query, boolean recursive, String[] columns) {
        Identifier identifier = Identifier.ofParts(name);
        if (ctes.stream().anyMatch(c -> c.name().equals(identifier))) {
            throw new IllegalArgumentException("Duplicate CTE: " + name);
        }

        ctes.add(new Cte(
                identifier,
                Objects.requireNonNull(query),
                Arrays.stream(columns).map(Identifier::ofParts).toList(),
                recursive
        ));
        return this;
    }

    /**
     * Validates the structure and returns an immutable snapshot of this builder.
     */
    public SelectQuery build() {
        if (from == null && !joins.isEmpty()) {
            throw new IllegalStateException("JOIN requires FROM");
        }
        return new DmlStatements.Select(
                Checks.nonEmpty(columns, "SELECT columns"),
                from,
                List.copyOf(joins),
                where,
                List.copyOf(groups),
                having,
                List.copyOf(ordering),
                List.copyOf(ctes),
                distinct,
                limit,
                offset
        );
    }

}
