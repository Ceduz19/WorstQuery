package com.github.ceduz19.worstquery.ddl;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.internal.Checks;
import com.github.ceduz19.worstquery.internal.statement.DdlStatements;
import com.github.ceduz19.worstquery.model.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable CREATE TABLE builder with structural validation.
 */
public final class CreateTableBuilder {

    private final Identifier table;
    private final List<ColumnDefinition> columns = new ArrayList<>();
    private final List<Constraint> constraints = new ArrayList<>();
    private boolean ifNotExists;

    CreateTableBuilder(Identifier table) {
        this.table = Objects.requireNonNull(table);
    }

    /**
     * Appends a column definition.
     */
    public CreateTableBuilder column(ColumnDefinition column) {
        columns.add(Objects.requireNonNull(column));
        return this;
    }

    /**
     * Appends a column definition.
     */
    public CreateTableBuilder column(String name, DataType type) {
        return column(ColumnDefinition.of(name, type));
    }

    /**
     * Appends a named table constraint.
     */
    public CreateTableBuilder constraint(Constraint constraint) {
        constraints.add(Objects.requireNonNull(constraint));
        return this;
    }

    /**
     * Skips creation when a table with this name already exists, where supported.
     */
    public CreateTableBuilder ifNotExists() {
        ifNotExists = true;
        return this;
    }

    /**
     * Snapshots the schema and checks duplicate names, key columns, and primary keys.
     */
    public Query build() {
        List<ColumnDefinition> definitions = Checks.nonEmpty(columns, "Table columns");
        List<Constraint> keys = List.copyOf(constraints);
        Set<Identifier> names = new HashSet<>();

        for (ColumnDefinition column : definitions) {
            if (!names.add(column.name()))
                throw new IllegalStateException("Duplicate column: " + column.name());
        }

        Set<Identifier> constraintNames = new HashSet<>();
        for (Constraint key : keys) {
            if (!constraintNames.add(key.name()))
                throw new IllegalStateException("Duplicate constraint: " + key.name());

            if (!names.containsAll(key.columns()))
                throw new IllegalStateException("Constraint references undeclared columns");
        }

        long primaryKeys = definitions.stream().filter(ColumnDefinition::isPrimaryKey).count()
                + keys.stream().filter(k -> k.kind() == Constraint.Kind.PRIMARY_KEY).count();
        if (primaryKeys > 1)
            throw new IllegalStateException("Use a single composite PRIMARY KEY constraint");

        return new DdlStatements.CreateTable(table, definitions, keys, ifNotExists);
    }
}
