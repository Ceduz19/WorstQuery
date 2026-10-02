package com.github.ceduz19.worstquery.ddl;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.internal.statement.DdlStatements;
import com.github.ceduz19.worstquery.model.Identifier;

import java.util.Objects;
import java.util.function.Function;

/**
 * Builds one ALTER TABLE operation; obtain instances from Schema.alterTable.
 */
public final class AlterTableBuilder {
    private final Identifier table;
    private Function<Boolean, Query> action;
    private boolean ifExists;

    AlterTableBuilder(Identifier table) {
        this.table = Objects.requireNonNull(table);
    }

    private AlterTableBuilder action(Function<Boolean, Query> factory) {
        if (action != null) {
            throw new IllegalStateException("One ALTER operation per builder");
        }
        action = factory;
        return this;
    }

    /**
     * Skips the ALTER operation if the table does not exist, where supported.
     */
    public AlterTableBuilder ifExists() {
        ifExists = true;
        return this;
    }

    /**
     * Selects an ADD COLUMN operation.
     */
    public AlterTableBuilder addColumn(ColumnDefinition column) {
        ColumnDefinition definition = Objects.requireNonNull(column);
        return action(tableIfExists -> new DdlStatements.AddColumn(table, definition, tableIfExists, false));
    }

    /**
     * Adds a column only if it does not already exist, where supported.
     */
    public AlterTableBuilder addColumnIfNotExists(ColumnDefinition column) {
        ColumnDefinition definition = Objects.requireNonNull(column);
        return action(tableIfExists -> new DdlStatements.AddColumn(table, definition, tableIfExists, true));
    }

    /**
     * Selects a DROP COLUMN operation.
     */
    public AlterTableBuilder dropColumn(String name) {
        Identifier column = Identifier.ofParts(name);
        return action(tableIfExists -> new DdlStatements.DropColumn(table, column, tableIfExists, false));
    }

    /**
     * Drops a column only if it exists, where supported.
     */
    public AlterTableBuilder dropColumnIfExists(String name) {
        Identifier column = Identifier.ofParts(name);
        return action(tableIfExists -> new DdlStatements.DropColumn(table, column, tableIfExists, true));
    }

    /**
     * Changes only the type; some dialects require modifyColumn instead.
     */
    public AlterTableBuilder alterColumnType(String name, DataType type) {
        Identifier column = Identifier.ofParts(name);
        DataType targetType = Objects.requireNonNull(type);
        return action(tableIfExists -> new DdlStatements.AlterColumnType(table, column, targetType, tableIfExists));
    }

    /**
     * Supplies the full definition required by MySQL/MariaDB and SQL Server.
     */
    public AlterTableBuilder modifyColumn(ColumnDefinition definition) {
        ColumnDefinition column = Objects.requireNonNull(definition);
        return action(tableIfExists -> new DdlStatements.ModifyColumn(table, column, tableIfExists, false));
    }

    /**
     * Redefines a column only if it exists, where supported.
     */
    public AlterTableBuilder modifyColumnIfExists(ColumnDefinition definition) {
        ColumnDefinition column = Objects.requireNonNull(definition);
        return action(tableIfExists -> new DdlStatements.ModifyColumn(table, column, tableIfExists, true));
    }

    /**
     * Selects an ADD CONSTRAINT operation.
     */
    public AlterTableBuilder addConstraint(Constraint constraint) {
        Constraint definition = Objects.requireNonNull(constraint);
        return action(tableIfExists -> new DdlStatements.AddConstraint(table, definition, tableIfExists, false));
    }

    /**
     * Adds a constraint only if it does not exist, where supported.
     */
    public AlterTableBuilder addConstraintIfNotExists(Constraint constraint) {
        Constraint definition = Objects.requireNonNull(constraint);
        return action(tableIfExists -> new DdlStatements.AddConstraint(table, definition, tableIfExists, true));
    }

    /**
     * Removes a constraint using the syntax appropriate to its kind.
     */
    public AlterTableBuilder dropConstraint(String name, Constraint.Kind kind) {
        Identifier constraint = Identifier.ofParts(name);
        Constraint.Kind constraintKind = Objects.requireNonNull(kind);
        return action(tableIfExists ->
                new DdlStatements.DropConstraint(table, constraint, constraintKind, tableIfExists, false));
    }

    /**
     * Drops a constraint only if it exists, where supported.
     */
    public AlterTableBuilder dropConstraintIfExists(String name, Constraint.Kind kind) {
        Identifier constraint = Identifier.ofParts(name);
        Constraint.Kind constraintKind = Objects.requireNonNull(kind);
        return action(tableIfExists ->
                new DdlStatements.DropConstraint(table, constraint, constraintKind, tableIfExists, true));
    }

    /**
     * Returns the immutable operation selected on this builder.
     */
    public Query build() {
        if (action == null) {
            throw new IllegalStateException("ALTER TABLE requires an operation");
        }
        return action.apply(ifExists);
    }
}
