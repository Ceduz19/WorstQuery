package com.github.ceduz19.worstquery;

import com.github.ceduz19.worstquery.spi.SqlDialect;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.ceduz19.worstquery.dialect.Dialects.ANSI;
import static com.github.ceduz19.worstquery.dialect.Dialects.ORACLE;
import static com.github.ceduz19.worstquery.dml.Queries.deleteFrom;
import static com.github.ceduz19.worstquery.dml.Queries.insertInto;
import static com.github.ceduz19.worstquery.dml.Queries.select;
import static com.github.ceduz19.worstquery.dml.Queries.update;
import static com.github.ceduz19.worstquery.expression.Expressions.column;
import static com.github.ceduz19.worstquery.expression.Expressions.param;
import static com.github.ceduz19.worstquery.fragment.SqlFragments.raw;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DmlTest {
    @Test
    void insertCopiesRowsAndPreservesNulls() {
        Object[] values = {1, null};
        var builder = insertInto("users").columns("id", "name").values(values);
        var original = builder.build();

        values[0] = 99;
        builder.values(2, "two");

        var originalResult = original.render(ANSI);
        var modifiedResult = builder.build().render(ANSI);

        assertEquals("INSERT INTO \"users\" (\"id\", \"name\") VALUES (?, ?)", originalResult.sql());
        assertEquals(Arrays.asList(1, null), originalResult.parameters());
        assertEquals(Arrays.asList(1, null, 2, "two"), modifiedResult.parameters());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.github.ceduz19.worstquery.DialectCases#all")
    void multiRowInsertUsesTheDialectSyntax(SqlDialect dialect) {
        var statement = insertInto("items")
                .columns("id")
                .values(1)
                .values(2)
                .build();
        String expectedSql = dialect == ORACLE
                ? "INSERT ALL INTO \"items\" (\"id\") VALUES (?)"
                + " INTO \"items\" (\"id\") VALUES (?) SELECT 1 FROM DUAL"
                : "INSERT INTO " + dialect.quoteIdentifier("items")
                + " (" + dialect.quoteIdentifier("id") + ") VALUES (?), (?)";

        var result = statement.render(dialect);

        assertEquals(expectedSql, result.sql());
        assertEquals(List.of(1, 2), result.parameters());
    }

    @Test
    void insertSelectPreservesParametersAndAcceptsSqlExpressions() {
        var source = select(column("id"))
                .from("active")
                .where(column("age").gt(10))
                .build();
        var copy = insertInto("archive").columns("id").from(source).build();
        var expressionInsert = insertInto("events")
                .columns("created_at")
                .values(raw("CURRENT_TIMESTAMP"))
                .build();

        var copiedRows = copy.render(ANSI);
        var expressionResult = expressionInsert.render(ANSI);

        String expectedCopy = "INSERT INTO \"archive\" (\"id\")"
                + " SELECT \"id\" FROM \"active\" WHERE (\"age\" > ?)";
        assertEquals(expectedCopy, copiedRows.sql());
        assertEquals(List.of(10), copiedRows.parameters());
        assertEquals("INSERT INTO \"events\" (\"created_at\") VALUES (CURRENT_TIMESTAMP)", expressionResult.sql());
    }

    @Test
    void updateSnapshotsAndPreservesAssignmentOrder() {
        var builder = update("users")
                .set("name", "before")
                .set("age", column("age").plus(1))
                .where(column("id").eq(7));
        var original = builder.build();

        builder.set("name", "after").where(column("id").eq(8));

        var originalResult = original.render(ANSI);
        var modifiedResult = builder.build().render(ANSI);
        String expectedSql = "UPDATE \"users\""
                + " SET \"name\" = ?, \"age\" = (\"age\" + ?)"
                + " WHERE (\"id\" = ?)";

        assertEquals(expectedSql, originalResult.sql());
        assertEquals(List.of("before", 1, 7), originalResult.parameters());
        assertEquals(List.of("after", 1, 8), modifiedResult.parameters());
    }

    @Test
    void deleteSnapshotsItsFilterAndAllowsFullTableOperations() {
        var builder = deleteFrom("users").where(column("id").eq(1));
        var original = builder.build();

        builder.where(column("id").eq(2));

        var filteredDelete = original.render(ANSI);
        var fullDelete = deleteFrom("users").build().render(ANSI);
        var fullUpdate = update("users").set("name", null).build().render(ANSI);

        assertEquals("DELETE FROM \"users\" WHERE (\"id\" = ?)", filteredDelete.sql());
        assertEquals(List.of(1), filteredDelete.parameters());
        assertEquals("DELETE FROM \"users\"", fullDelete.sql());
        assertEquals("UPDATE \"users\" SET \"name\" = ?", fullUpdate.sql());
    }

    @Test
    void invalidInsertsAndUpdatesAreRejected() {
        var missingColumns = insertInto("items").values(1);
        var missingRows = insertInto("items").columns("id");
        var wrongRowWidth = insertInto("items").columns("id").values(1, 2);
        var conflictingSources = insertInto("items")
                .columns("id")
                .values(1)
                .from(select(param(1)).build());

        assertThrows(IllegalArgumentException.class, missingColumns::build);
        assertThrows(IllegalStateException.class, missingRows::build);
        assertThrows(IllegalStateException.class, wrongRowWidth::build);
        assertThrows(IllegalStateException.class, conflictingSources::build);

        assertThrows(IllegalArgumentException.class, () -> insertInto("items").columns("id", "id"));
        assertThrows(IllegalStateException.class, () -> update("items").build());
    }
}
