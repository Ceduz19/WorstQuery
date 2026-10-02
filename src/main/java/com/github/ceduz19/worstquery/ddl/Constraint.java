package com.github.ceduz19.worstquery.ddl;

import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.internal.render.DdlRenderer;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.SqlPart;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.List;
import java.util.Objects;

/**
 * A named table constraint, including composite keys.
 */
public final class Constraint implements SqlPart {

    private final Identifier name;
    private final Kind kind;
    private final List<Identifier> columns;
    private final Identifier referencedTable;
    private final List<Identifier> referencedColumns;
    private final Condition check;

    public enum Kind {
        PRIMARY_KEY,
        UNIQUE,
        FOREIGN_KEY,
        CHECK
    }

    Constraint(
            Identifier name,
            Kind kind,
            List<Identifier> columns,
            Identifier referencedTable,
            List<Identifier> referencedColumns,
            Condition check
    ) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(kind);
        if (name.parts().size() != 1) {
            throw new IllegalArgumentException("Constraint name must be unqualified");
        }

        columns = List.copyOf(columns);
        referencedColumns = List.copyOf(referencedColumns);
        if (columns.stream().anyMatch(c -> c.parts().size() != 1)
                || referencedColumns.stream().anyMatch(c -> c.parts().size() != 1)) {
            throw new IllegalArgumentException("Constraint columns must be unqualified");
        }

        if (kind == Kind.CHECK) {
            Objects.requireNonNull(check);
            if (!columns.isEmpty()) {
                throw new IllegalArgumentException("CHECK does not declare key columns");
            }
        } else {
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("Constraint columns are empty");
            }
            if (check != null) {
                throw new IllegalArgumentException("Only CHECK accepts a condition");
            }
        }

        if (kind == Kind.FOREIGN_KEY) {
            Objects.requireNonNull(referencedTable);
            if (columns.size() != referencedColumns.size()) {
                throw new IllegalArgumentException("Foreign key column counts differ");
            }
        } else if (referencedTable != null || !referencedColumns.isEmpty()) {
            throw new IllegalArgumentException("Unexpected foreign key reference");
        }

        this.name = name;
        this.kind = kind;
        this.columns = columns;
        this.referencedTable = referencedTable;
        this.referencedColumns = referencedColumns;
        this.check = check;
    }

    @Override
    public void appendTo(SqlWriter writer) {
        DdlRenderer.render(this, writer);
    }

    public Identifier name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public List<Identifier> columns() {
        return columns;
    }

    public Identifier referencedTable() {
        return referencedTable;
    }

    public List<Identifier> referencedColumns() {
        return referencedColumns;
    }

    public Condition check() {
        return check;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Constraint value)) {
            return false;
        }
        return Objects.equals(name, value.name)
                && Objects.equals(kind, value.kind)
                && Objects.equals(columns, value.columns)
                && Objects.equals(referencedTable, value.referencedTable)
                && Objects.equals(referencedColumns, value.referencedColumns)
                && Objects.equals(check, value.check);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, kind, columns, referencedTable, referencedColumns, check);
    }

    @Override
    public String toString() {
        return "Constraint[name=" + name
                + ", kind=" + kind
                + ", columns=" + columns
                + ", referencedTable=" + referencedTable
                + ", referencedColumns=" + referencedColumns
                + ", check=" + check + "]";
    }
}
