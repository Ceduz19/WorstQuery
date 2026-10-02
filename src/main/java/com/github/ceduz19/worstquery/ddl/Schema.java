package com.github.ceduz19.worstquery.ddl;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.internal.statement.DdlStatements;
import com.github.ceduz19.worstquery.model.Identifier;

/**
 * Entry points for schema statements and immutable column definitions.
 */
public final class Schema {
    private Schema() {
    }

    /**
     * Starts a CREATE TABLE builder for a qualified table name.
     */
    public static CreateTableBuilder createTable(String name) {
        return new CreateTableBuilder(Identifier.of(name));
    }

    /**
     * Starts a builder for one ALTER TABLE operation.
     */
    public static AlterTableBuilder alterTable(String name) {
        return new AlterTableBuilder(Identifier.of(name));
    }

    /**
     * Starts a CREATE INDEX builder.
     */
    public static CreateIndexBuilder createIndex(String name) {
        return new CreateIndexBuilder(Identifier.of(name));
    }

    /**
     * Creates a DROP TABLE statement without executing it.
     */
    public static Query dropTable(String name) {
        Identifier table = Identifier.of(name);
        return new DdlStatements.DropTable(table, false);
    }

    /**
     * Drops a table only if it exists, where supported.
     */
    public static Query dropTableIfExists(String name) {
        return new DdlStatements.DropTable(Identifier.of(name), true);
    }

    /**
     * Creates a DROP INDEX statement; some dialects require a table.
     */
    public static Query dropIndex(String name) {
        return dropIndex(name, null);
    }

    /**
     * Table is required by MySQL, MariaDB, and SQL Server.
     */
    public static Query dropIndex(String name, String table) {
        Identifier index = Identifier.of(name);
        Identifier target = table == null ? null : Identifier.of(table);
        return new DdlStatements.DropIndex(index, target, false);
    }

    /**
     * Drops an index only if it exists, where supported.
     */
    public static Query dropIndexIfExists(String name) {
        return dropIndexIfExists(name, null);
    }

    /**
     * Some dialects require the table when dropping an index.
     */
    public static Query dropIndexIfExists(String name, String table) {
        Identifier index = Identifier.of(name);
        Identifier target = table == null ? null : Identifier.of(table);
        return new DdlStatements.DropIndex(index, target, true);
    }

    /**
     * Creates an immutable, initially nullable column definition.
     */
    public static ColumnDefinition column(String name, DataType type) {
        return ColumnDefinition.of(name, type);
    }
}
