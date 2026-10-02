package com.github.ceduz19.worstquery.dml;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.api.SelectQuery;
import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.internal.Checks;
import com.github.ceduz19.worstquery.internal.expression.ExpressionNodes;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements;
import com.github.ceduz19.worstquery.model.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * INSERT builder with explicit columns and either rows or a SELECT source.
 */
public final class InsertBuilder {
    private final Identifier table;
    private List<Identifier> columns = List.of();
    private final List<List<Expression>> rows = new ArrayList<>();
    private SelectQuery source;

    InsertBuilder(Identifier table) {
        this.table = Objects.requireNonNull(table);
    }

    /**
     * Replaces the column list; column names must be unique and unqualified.
     */
    public InsertBuilder columns(String... names) {
        columns = Arrays.stream(names).map(Identifier::ofParts).toList();
        if (new HashSet<>(columns).size() != columns.size()) {
            throw new IllegalArgumentException("Duplicate INSERT column");
        }
        return this;
    }

    /**
     * Appends a row; objects bind as values and expressions compose as SQL.
     */
    public InsertBuilder values(Object... values) {
        rows.add(Arrays.stream(values).map(ExpressionNodes::operand).toList());
        return this;
    }

    /**
     * Sets an INSERT SELECT source; it cannot be combined with VALUES rows.
     */
    public InsertBuilder from(SelectQuery source) {
        this.source = Objects.requireNonNull(source);
        return this;
    }

    /**
     * Snapshots rows and validates that each row matches the column count.
     */
    public Query build() {
        List<Identifier> columnSnapshot = Checks.nonEmpty(columns, "INSERT columns");
        List<List<Expression>> rowSnapshot = List.copyOf(rows);
        SelectQuery select = source;
        boolean hasValues = !rowSnapshot.isEmpty();
        boolean hasSelect = select != null;
        if (hasValues == hasSelect) {
            throw new IllegalStateException("INSERT requires either VALUES or SELECT");
        }
        if (rowSnapshot.stream().anyMatch(row -> row.size() != columnSnapshot.size())) {
            throw new IllegalStateException("INSERT row length differs from column count");
        }
        return new DmlStatements.Insert(table, columnSnapshot, rowSnapshot, select);
    }
}
