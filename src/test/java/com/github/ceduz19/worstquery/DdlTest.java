package com.github.ceduz19.worstquery;

import com.github.ceduz19.worstquery.api.UnsupportedSqlFeatureException;
import com.github.ceduz19.worstquery.ddl.Constraint;
import com.github.ceduz19.worstquery.ddl.Constraints;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.spi.SqlDialect;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.ceduz19.worstquery.api.RenderMode.INLINED;
import static com.github.ceduz19.worstquery.ddl.Schema.alterTable;
import static com.github.ceduz19.worstquery.ddl.Schema.column;
import static com.github.ceduz19.worstquery.ddl.Schema.createIndex;
import static com.github.ceduz19.worstquery.ddl.Schema.createTable;
import static com.github.ceduz19.worstquery.ddl.Schema.dropIndex;
import static com.github.ceduz19.worstquery.ddl.Schema.dropIndexIfExists;
import static com.github.ceduz19.worstquery.ddl.Schema.dropTable;
import static com.github.ceduz19.worstquery.ddl.Schema.dropTableIfExists;
import static com.github.ceduz19.worstquery.dialect.Dialects.ANSI;
import static com.github.ceduz19.worstquery.dialect.Dialects.MARIADB;
import static com.github.ceduz19.worstquery.dialect.Dialects.MYSQL;
import static com.github.ceduz19.worstquery.dialect.Dialects.ORACLE;
import static com.github.ceduz19.worstquery.dialect.Dialects.POSTGRESQL;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQLITE;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQL_SERVER;
import static com.github.ceduz19.worstquery.expression.Expressions.column;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdlTest {
    @Test
    void createTableWithConstraintsAndLiterals() {
        var result = createTable("app.users")
                .column(column("id", DataType.BIGINT).primaryKey())
                .column(column("name", DataType.varchar(80)).notNull().defaultValue("O'Brien"))
                .column("parent_id", DataType.BIGINT)
                .constraint(Constraints.unique("uq_name", "name"))
                .constraint(Constraints.foreignKey("fk_parent", List.of("parent_id"), "app.users", List.of("id")))
                .constraint(Constraints.check("positive", column("id").gt(0)))
                .build().render(POSTGRESQL);

        String expectedSql = "CREATE TABLE \"app\".\"users\" ("
                + "\"id\" BIGINT NOT NULL PRIMARY KEY, "
                + "\"name\" VARCHAR(80) DEFAULT ('O''Brien') NOT NULL, "
                + "\"parent_id\" BIGINT,"
                + " CONSTRAINT \"uq_name\" UNIQUE (\"name\"),"
                + " CONSTRAINT \"fk_parent\" FOREIGN KEY (\"parent_id\")"
                + " REFERENCES \"app\".\"users\" (\"id\"),"
                + " CONSTRAINT \"positive\" CHECK ((\"id\" > 0)))";

        assertEquals(expectedSql, result.sql());
        assertTrue(result.parameters().isEmpty());
    }

    @Test
    void compositePrimaryKeyAndDefaultNull() {
        var result = createTable("pairs")
                .column(column("a", DataType.INTEGER).notNull())
                .column("b", DataType.INTEGER)
                .column(column("label", DataType.TEXT).defaultValue(null))
                .constraint(Constraints.primaryKey("pk_pair", "a", "b"))
                .build().render(ANSI);

        String expectedSql =
                "CREATE TABLE \"pairs\" (\"a\" INTEGER NOT NULL, \"b\" INTEGER, \"label\" CLOB DEFAULT (NULL),"
                        + " CONSTRAINT \"pk_pair\" PRIMARY KEY (\"a\", \"b\"))";

        assertEquals(expectedSql, result.sql());
        assertTrue(result.parameters().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.github.ceduz19.worstquery.DialectCases#all")
    void commonTypesHaveExactDialectMapping(SqlDialect dialect) {
        Map<SqlDialect, String> expected = Map.of(
                ANSI, "CREATE TABLE \"t\" (\"flag\" BOOLEAN, \"body\" CLOB, \"at\" TIMESTAMP, \"data\" VARBINARY(16))",
                POSTGRESQL, "CREATE TABLE \"t\" (\"flag\" BOOLEAN, \"body\" TEXT, \"at\" TIMESTAMP, \"data\" BYTEA)",
                MYSQL, "CREATE TABLE `t` (`flag` BOOLEAN, `body` TEXT, `at` TIMESTAMP, `data` VARBINARY(16))",
                MARIADB, "CREATE TABLE `t` (`flag` BOOLEAN, `body` TEXT, `at` TIMESTAMP, `data` VARBINARY(16))",
                SQLITE, "CREATE TABLE \"t\" (\"flag\" BOOLEAN, \"body\" TEXT, \"at\" TIMESTAMP, \"data\" BLOB)",
                SQL_SERVER, "CREATE TABLE [t] ([flag] BIT, [body] VARCHAR(MAX), [at] DATETIME2, [data] VARBINARY(16))",
                ORACLE, "CREATE TABLE \"t\" (\"flag\" NUMBER(1), \"body\" CLOB, \"at\" TIMESTAMP, \"data\" RAW(16))");
        var query = createTable("t")
                .column("flag", DataType.BOOLEAN)
                .column("body", DataType.TEXT)
                .column("at", DataType.TIMESTAMP)
                .column("data", DataType.binary(16))
                .build();

        assertEquals(expected.get(dialect), query.render(dialect).sql());
    }

    @Test
    void numericAndCustomTypesRenderWithValidatedDefinitions() {
        var schema = createTable("items")
                .column("value", DataType.decimal(12, 2))
                .column("doc", DataType.custom("JSONB"))
                .build();
        var unsupportedTime = createTable("events").column("at", DataType.TIME).build();

        var result = schema.render(POSTGRESQL);

        assertEquals("CREATE TABLE \"items\" (\"value\" DECIMAL(12, 2), \"doc\" JSONB)", result.sql());
        assertThrows(UnsupportedSqlFeatureException.class, () -> unsupportedTime.render(ORACLE));

        assertThrows(IllegalArgumentException.class, () -> DataType.decimal(2, 3));
        assertThrows(IllegalArgumentException.class, () -> DataType.varchar(0));
        assertThrows(IllegalArgumentException.class, () -> DataType.binary(-1));
        assertThrows(IllegalArgumentException.class, () -> DataType.custom(" "));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.github.ceduz19.worstquery.DialectCases#all")
    void ddlDefaultStringsAreEscapedAndNeverBound(SqlDialect dialect) {
        Map<SqlDialect, String> expected = Map.of(
                ANSI, "'O''Brien\\path'",
                POSTGRESQL, "E'O''Brien\\\\path'",
                MYSQL, "CONVERT(X'4f27427269656e5c70617468' USING utf8mb4)",
                MARIADB, "CONVERT(X'4f27427269656e5c70617468' USING utf8mb4)",
                SQLITE, "'O''Brien\\path'",
                SQL_SERVER, "N'O''Brien\\path'",
                ORACLE, "'O''Brien\\path'");

        var statement = createTable("t")
                .column(column("name", DataType.varchar(50)).defaultValue("O'Brien\\path"))
                .build();
        var query = statement.render(dialect);

        assertEquals(expected.get(dialect), dialect.literal("O'Brien\\path"));
        assertEquals(query, statement.render(dialect, INLINED));
        assertTrue(query.parameters().isEmpty());
        assertTrue(query.sql().contains(expected.get(dialect)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.github.ceduz19.worstquery.DialectCases#all")
    void addAndDropColumn(SqlDialect dialect) {
        var added = alterTable("t").addColumn(column("age", DataType.INTEGER)).build().render(dialect);
        var dropped = alterTable("t").dropColumn("age").build().render(dialect);

        assertTrue(added.sql().contains(dialect == SQL_SERVER || dialect == ORACLE ? " ADD " : " ADD COLUMN "));
        assertTrue(added.parameters().isEmpty());
        assertTrue(dropped.sql().contains(" DROP COLUMN "));
    }

    @Test
    void sqliteRejectsUnsupportedSchemaChanges() {
        var changeType = alterTable("items").alterColumnType("id", DataType.BIGINT).build();
        var addConstraint = alterTable("items").addConstraint(Constraints.unique("uq", "id")).build();
        var dropConstraint = alterTable("items").dropConstraint("uq", Constraint.Kind.UNIQUE).build();
        var addPrimaryKey = alterTable("items")
                .addColumn(column("id", DataType.INTEGER).primaryKey())
                .build();

        assertThrows(UnsupportedSqlFeatureException.class, () -> changeType.render(SQLITE));
        assertThrows(UnsupportedSqlFeatureException.class, () -> addConstraint.render(SQLITE));
        assertThrows(UnsupportedSqlFeatureException.class, () -> dropConstraint.render(SQLITE));
        assertThrows(UnsupportedSqlFeatureException.class, () -> addPrimaryKey.render(SQLITE));
    }

    @Test
    void changingTypeDoesNotGuessMissingColumnAttributes() {
        var query = alterTable("t").alterColumnType("age", DataType.BIGINT).build();

        assertEquals("ALTER TABLE \"t\" ALTER COLUMN \"age\" TYPE BIGINT", query.render(POSTGRESQL).sql());
        assertEquals("ALTER TABLE \"t\" MODIFY (\"age\" NUMBER(19))", query.render(ORACLE).sql());
        assertEquals("ALTER TABLE \"t\" ALTER COLUMN \"age\" SET DATA TYPE BIGINT", query.render(ANSI).sql());
        assertThrows(UnsupportedSqlFeatureException.class, () -> query.render(MYSQL));
        assertThrows(UnsupportedSqlFeatureException.class, () -> query.render(SQL_SERVER));

        var definition = alterTable("t").modifyColumn(column("age", DataType.BIGINT).notNull()).build();

        assertEquals("ALTER TABLE `t` MODIFY COLUMN `age` BIGINT NOT NULL", definition.render(MYSQL).sql());
        assertEquals("ALTER TABLE [t] ALTER COLUMN [age] BIGINT NOT NULL", definition.render(SQL_SERVER).sql());
        assertThrows(UnsupportedSqlFeatureException.class, () -> definition.render(POSTGRESQL));
    }

    @Test
    void sqlServerRejectsDefaultsInsideAlterColumn() {
        var query = alterTable("t").modifyColumn(column("n", DataType.INTEGER).defaultValue(1)).build();

        assertThrows(UnsupportedSqlFeatureException.class, () -> query.render(SQL_SERVER));
    }

    @Test
    void droppingConstraintsUsesTheirKind() {
        var primaryKey = alterTable("items").dropConstraint("pk", Constraint.Kind.PRIMARY_KEY).build();
        var foreignKey = alterTable("items").dropConstraint("fk", Constraint.Kind.FOREIGN_KEY).build();
        var unique = alterTable("items").dropConstraint("uq", Constraint.Kind.UNIQUE).build();
        var check = alterTable("items").dropConstraint("ck", Constraint.Kind.CHECK).build();

        assertEquals("ALTER TABLE `items` DROP PRIMARY KEY", primaryKey.render(MYSQL).sql());
        assertEquals("ALTER TABLE `items` DROP FOREIGN KEY `fk`", foreignKey.render(MYSQL).sql());
        assertEquals("ALTER TABLE `items` DROP INDEX `uq`", unique.render(MARIADB).sql());
        assertEquals("ALTER TABLE `items` DROP CHECK `ck`", check.render(MYSQL).sql());
        assertEquals("ALTER TABLE \"items\" DROP CONSTRAINT \"uq\"", unique.render(POSTGRESQL).sql());
    }

    @Test
    void indexAndTableStatementsUseTheRequiredDialectSyntax() {
        var uniqueIndex = createIndex("uq_name").unique().on("users", "name").build();
        var tableRemoval = dropTable("users");
        var standaloneIndexRemoval = dropIndex("uq_name");
        var qualifiedIndexRemoval = dropIndex("uq_name", "users");

        assertEquals("CREATE UNIQUE INDEX \"uq_name\" ON \"users\" (\"name\")", uniqueIndex.render(ANSI).sql());
        assertEquals("DROP TABLE \"users\"", tableRemoval.render(ANSI).sql());
        assertEquals("DROP INDEX \"uq_name\"", standaloneIndexRemoval.render(POSTGRESQL).sql());
        assertEquals("DROP INDEX [uq_name] ON [users]", qualifiedIndexRemoval.render(SQL_SERVER).sql());
        assertEquals("DROP INDEX `uq_name` ON `users`", qualifiedIndexRemoval.render(MYSQL).sql());

        assertThrows(IllegalArgumentException.class, () -> standaloneIndexRemoval.render(MYSQL));
    }

    @Test
    void conditionalCreateAndDropStatementsRenderForSupportedDialects() {
        var table = createTable("app.items")
                .column("id", DataType.INTEGER)
                .ifNotExists()
                .build();
        var index = createIndex("idx_items_id")
                .on("items", "id")
                .unique()
                .ifNotExists()
                .build();

        assertEquals("CREATE TABLE IF NOT EXISTS \"app\".\"items\" (\"id\" INTEGER)",
                table.render(POSTGRESQL).sql());
        assertEquals("CREATE TABLE IF NOT EXISTS `app`.`items` (`id` INTEGER)", table.render(MYSQL).sql());
        assertEquals("CREATE UNIQUE INDEX IF NOT EXISTS \"idx_items_id\" ON \"items\" (\"id\")",
                index.render(POSTGRESQL).sql());
        assertEquals("CREATE UNIQUE INDEX IF NOT EXISTS `idx_items_id` ON `items` (`id`)",
                index.render(MARIADB).sql());
        assertEquals("CREATE UNIQUE INDEX IF NOT EXISTS \"idx_items_id\" ON \"items\" (\"id\")",
                index.render(SQLITE).sql());

        assertEquals("DROP TABLE IF EXISTS [items]", dropTableIfExists("items").render(SQL_SERVER).sql());
        assertEquals("DROP INDEX IF EXISTS \"idx_items_id\"",
                dropIndexIfExists("idx_items_id").render(POSTGRESQL).sql());
        assertEquals("DROP INDEX IF EXISTS `idx_items_id` ON `items`",
                dropIndexIfExists("idx_items_id", "items").render(MARIADB).sql());
        assertEquals("DROP INDEX IF EXISTS [idx_items_id] ON [items]",
                dropIndexIfExists("idx_items_id", "items").render(SQL_SERVER).sql());

        assertTrue(table.render(POSTGRESQL).parameters().isEmpty());
        assertTrue(index.render(MARIADB).parameters().isEmpty());
    }

    @Test
    void conditionalAlterStatementsKeepTableAndActionChecksSeparate() {
        var addColumn = alterTable("items")
                .ifExists()
                .addColumnIfNotExists(column("age", DataType.INTEGER))
                .build();
        var dropColumn = alterTable("items")
                .dropColumnIfExists("age")
                .ifExists()
                .build();
        var changeType = alterTable("items").ifExists().alterColumnType("age", DataType.BIGINT).build();
        var modifyColumn = alterTable("items")
                .ifExists()
                .modifyColumnIfExists(column("age", DataType.BIGINT).notNull())
                .build();
        var addConstraint = alterTable("items")
                .ifExists()
                .addConstraint(Constraints.unique("uq_age", "age"))
                .build();
        var dropConstraint = alterTable("items")
                .ifExists()
                .dropConstraintIfExists("uq_age", Constraint.Kind.UNIQUE)
                .build();

        assertEquals("ALTER TABLE IF EXISTS \"items\" ADD COLUMN IF NOT EXISTS \"age\" INTEGER",
                addColumn.render(POSTGRESQL).sql());
        assertEquals("ALTER TABLE IF EXISTS `items` ADD COLUMN IF NOT EXISTS `age` INTEGER",
                addColumn.render(MARIADB).sql());
        assertEquals("ALTER TABLE IF EXISTS \"items\" DROP COLUMN IF EXISTS \"age\"",
                dropColumn.render(POSTGRESQL).sql());
        assertEquals("ALTER TABLE [items] DROP COLUMN IF EXISTS [age]",
                alterTable("items").dropColumnIfExists("age").build().render(SQL_SERVER).sql());
        assertEquals("ALTER TABLE IF EXISTS \"items\" ALTER COLUMN \"age\" TYPE BIGINT",
                changeType.render(POSTGRESQL).sql());
        assertEquals("ALTER TABLE IF EXISTS `items` MODIFY COLUMN IF EXISTS `age` BIGINT NOT NULL",
                modifyColumn.render(MARIADB).sql());
        assertEquals("ALTER TABLE IF EXISTS \"items\" ADD CONSTRAINT \"uq_age\" UNIQUE (\"age\")",
                addConstraint.render(POSTGRESQL).sql());
        assertEquals("ALTER TABLE IF EXISTS \"items\" DROP CONSTRAINT IF EXISTS \"uq_age\"",
                dropConstraint.render(POSTGRESQL).sql());
        assertEquals("ALTER TABLE IF EXISTS `items` DROP INDEX IF EXISTS `uq_age`",
                dropConstraint.render(MARIADB).sql());
    }

    @Test
    void mariaDbConditionalConstraintsUseTheirNativeForms() {
        var primaryKey = alterTable("items")
                .addConstraintIfNotExists(Constraints.primaryKey("pk_items", "id"))
                .build();
        var unique = alterTable("items")
                .addConstraintIfNotExists(Constraints.unique("uq_items_id", "id"))
                .build();
        var foreignKey = alterTable("items")
                .addConstraintIfNotExists(Constraints.foreignKey("fk_parent", List.of("parent_id"),
                        "parent", List.of("id")))
                .build();

        assertEquals("ALTER TABLE `items` ADD CONSTRAINT `pk_items` PRIMARY KEY IF NOT EXISTS (`id`)",
                primaryKey.render(MARIADB).sql());
        assertEquals("ALTER TABLE `items` ADD UNIQUE INDEX IF NOT EXISTS `uq_items_id` (`id`)",
                unique.render(MARIADB).sql());
        assertEquals("ALTER TABLE `items` ADD CONSTRAINT `fk_parent` FOREIGN KEY IF NOT EXISTS "
                        + "(`parent_id`) REFERENCES `parent` (`id`)", foreignKey.render(MARIADB).sql());
        assertEquals("ALTER TABLE `items` DROP FOREIGN KEY IF EXISTS `fk_parent`",
                alterTable("items").dropConstraintIfExists("fk_parent", Constraint.Kind.FOREIGN_KEY)
                        .build().render(MARIADB).sql());
        assertEquals("ALTER TABLE `items` DROP CONSTRAINT IF EXISTS `check_age`",
                alterTable("items").dropConstraintIfExists("check_age", Constraint.Kind.CHECK)
                        .build().render(MARIADB).sql());
        assertEquals("ALTER TABLE [items] DROP CONSTRAINT IF EXISTS [uq_items_id]",
                alterTable("items").dropConstraintIfExists("uq_items_id", Constraint.Kind.UNIQUE)
                        .build().render(SQL_SERVER).sql());
    }

    @Test
    void unsupportedConditionalDdlIsRejectedAtRenderTime() {
        var table = createTable("items").column("id", DataType.INTEGER).ifNotExists().build();
        var index = createIndex("idx_items_id").on("items", "id").ifNotExists().build();

        assertThrows(UnsupportedSqlFeatureException.class, () -> table.render(ANSI));
        assertThrows(UnsupportedSqlFeatureException.class, () -> table.render(SQL_SERVER));
        assertThrows(UnsupportedSqlFeatureException.class, () -> table.render(ORACLE));
        assertThrows(UnsupportedSqlFeatureException.class, () -> index.render(MYSQL));
        assertThrows(UnsupportedSqlFeatureException.class, () -> index.render(SQL_SERVER));
        assertThrows(UnsupportedSqlFeatureException.class, () -> dropTableIfExists("items").render(ORACLE));
        assertThrows(UnsupportedSqlFeatureException.class,
                () -> dropIndexIfExists("idx_items_id", "items").render(MYSQL));
        assertThrows(IllegalArgumentException.class, () -> dropIndexIfExists("idx_items_id").render(MARIADB));

        assertThrows(UnsupportedSqlFeatureException.class,
                () -> alterTable("items").ifExists().addColumn(column("age", DataType.INTEGER))
                        .build().render(SQL_SERVER));
        assertThrows(UnsupportedSqlFeatureException.class,
                () -> alterTable("items").addColumnIfNotExists(column("age", DataType.INTEGER))
                        .build().render(SQLITE));
        assertThrows(UnsupportedSqlFeatureException.class,
                () -> alterTable("items").dropColumnIfExists("age").build().render(SQLITE));
        assertThrows(UnsupportedSqlFeatureException.class,
                () -> alterTable("items").modifyColumnIfExists(column("age", DataType.BIGINT))
                        .build().render(MYSQL));
        assertThrows(UnsupportedSqlFeatureException.class,
                () -> alterTable("items").addConstraintIfNotExists(Constraints.unique("uq_age", "age"))
                        .build().render(POSTGRESQL));
        assertThrows(UnsupportedSqlFeatureException.class,
                () -> alterTable("items").addConstraintIfNotExists(Constraints.check("age_check", column("age").gt(0)))
                        .build().render(MARIADB));
        assertThrows(UnsupportedSqlFeatureException.class,
                () -> alterTable("items").dropConstraintIfExists("pk_items", Constraint.Kind.PRIMARY_KEY)
                        .build().render(MARIADB));
    }

    @Test
    void conditionalBuildersPreserveEarlierSnapshots() {
        var tableBuilder = createTable("items").column("id", DataType.INTEGER);
        var firstTable = tableBuilder.build();
        var conditionalTable = tableBuilder.ifNotExists().build();

        assertEquals("CREATE TABLE \"items\" (\"id\" INTEGER)", firstTable.render(POSTGRESQL).sql());
        assertEquals("CREATE TABLE IF NOT EXISTS \"items\" (\"id\" INTEGER)",
                conditionalTable.render(POSTGRESQL).sql());

        var indexBuilder = createIndex("idx_items_id").on("items", "id");
        var firstIndex = indexBuilder.build();
        var conditionalIndex = indexBuilder.ifNotExists().build();

        assertEquals("CREATE INDEX \"idx_items_id\" ON \"items\" (\"id\")",
                firstIndex.render(POSTGRESQL).sql());
        assertEquals("CREATE INDEX IF NOT EXISTS \"idx_items_id\" ON \"items\" (\"id\")",
                conditionalIndex.render(POSTGRESQL).sql());

        var alterBuilder = alterTable("items").dropColumnIfExists("age");
        var firstAlter = alterBuilder.build();
        var conditionalAlter = alterBuilder.ifExists().build();

        assertEquals("ALTER TABLE \"items\" DROP COLUMN IF EXISTS \"age\"",
                firstAlter.render(POSTGRESQL).sql());
        assertEquals("ALTER TABLE IF EXISTS \"items\" DROP COLUMN IF EXISTS \"age\"",
                conditionalAlter.render(POSTGRESQL).sql());
    }

    @Test
    void tableAndIndexBuildersProduceSnapshots() {
        var table = createTable("t")
                .column("a", DataType.INTEGER);
        var first = table.build();

        table.column("b", DataType.INTEGER);

        assertEquals("CREATE TABLE \"t\" (\"a\" INTEGER)", first.render(ANSI).sql());

        var index = createIndex("idx").on("t", "a");
        var firstIndex = index.build();

        index.unique().on("t", "b");

        assertEquals("CREATE INDEX \"idx\" ON \"t\" (\"a\")", firstIndex.render(ANSI).sql());
    }

    @Test
    void invalidSchemaDefinitionsAreRejected() {
        var duplicateColumns = createTable("items")
                .column("id", DataType.INTEGER)
                .column("id", DataType.INTEGER);
        var multiplePrimaryKeys = createTable("items")
                .column(column("id", DataType.INTEGER).primaryKey())
                .column(column("code", DataType.INTEGER).primaryKey());
        var missingConstraintColumn = createTable("items")
                .column("id", DataType.INTEGER)
                .constraint(Constraints.unique("uq", "missing"));

        assertThrows(IllegalArgumentException.class, () -> createTable("items").build());
        assertThrows(IllegalStateException.class, duplicateColumns::build);
        assertThrows(IllegalStateException.class, multiplePrimaryKeys::build);
        assertThrows(IllegalStateException.class, missingConstraintColumn::build);

        // Foreign keys must pair each local column with a referenced column.
        assertThrows(IllegalArgumentException.class,
                () -> Constraints.foreignKey("fk", List.of("id"), "parent", List.of("id", "code")));

        // ALTER and index builders require one complete operation.
        assertThrows(IllegalStateException.class, () -> alterTable("items").build());
        assertThrows(IllegalStateException.class, () -> alterTable("items").dropColumn("id").dropColumn("code"));
        assertThrows(IllegalStateException.class, () -> createIndex("idx").build());
        assertThrows(IllegalArgumentException.class, () -> createIndex("idx").on("items").build());
    }
}
