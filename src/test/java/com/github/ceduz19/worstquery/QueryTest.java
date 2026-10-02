package com.github.ceduz19.worstquery;

import com.github.ceduz19.worstquery.api.RenderedQuery;
import com.github.ceduz19.worstquery.api.UnsupportedSqlFeatureException;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.expression.FrameBound;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.SqlDialect;
import com.github.ceduz19.worstquery.spi.SqlPart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.ceduz19.worstquery.api.RenderMode.INLINED;
import static com.github.ceduz19.worstquery.api.RenderMode.PARAMETERIZED;
import static com.github.ceduz19.worstquery.ddl.Schema.column;
import static com.github.ceduz19.worstquery.ddl.Schema.createTable;
import static com.github.ceduz19.worstquery.dialect.Dialects.ANSI;
import static com.github.ceduz19.worstquery.dialect.Dialects.MYSQL;
import static com.github.ceduz19.worstquery.dialect.Dialects.ORACLE;
import static com.github.ceduz19.worstquery.dialect.Dialects.POSTGRESQL;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQLITE;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQL_SERVER;
import static com.github.ceduz19.worstquery.dml.Queries.select;
import static com.github.ceduz19.worstquery.expression.Expressions.alwaysFalse;
import static com.github.ceduz19.worstquery.expression.Expressions.alwaysTrue;
import static com.github.ceduz19.worstquery.expression.Expressions.caseWhen;
import static com.github.ceduz19.worstquery.expression.Expressions.column;
import static com.github.ceduz19.worstquery.expression.Expressions.exists;
import static com.github.ceduz19.worstquery.expression.Expressions.identifier;
import static com.github.ceduz19.worstquery.expression.Expressions.notExists;
import static com.github.ceduz19.worstquery.expression.Expressions.param;
import static com.github.ceduz19.worstquery.expression.Expressions.star;
import static com.github.ceduz19.worstquery.expression.Expressions.table;
import static com.github.ceduz19.worstquery.expression.Expressions.window;
import static com.github.ceduz19.worstquery.expression.Functions.function;
import static com.github.ceduz19.worstquery.expression.Functions.rowNumber;
import static com.github.ceduz19.worstquery.expression.Functions.sum;
import static com.github.ceduz19.worstquery.fragment.SqlFragments.query;
import static com.github.ceduz19.worstquery.fragment.SqlFragments.raw;
import static com.github.ceduz19.worstquery.fragment.SqlFragments.selectSql;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.github.ceduz19.worstquery.DialectCases#all")
    void renderModesUseDialectLiteralsAndKeepSessionsIndependent(SqlDialect dialect) {
        String text = "O'Brien\\path?";
        var query = select(param(text), param(18), param(true), param(false), param(null),
                param(new BigDecimal("12.50")))
                .build();
        var parameterized = query.render(dialect);
        var inlined = query.render(dialect, INLINED);

        String expectedBooleans = dialect == ORACLE || dialect == SQL_SERVER ? "1, 0" : "TRUE, FALSE";
        String expectedSql = "SELECT " + dialect.literal(text) + ", 18, " + expectedBooleans
                + ", NULL, 12.50" + dialect.selectWithoutFromSuffix();

        assertEquals(parameterized, query.render(dialect, PARAMETERIZED));
        assertEquals(Arrays.asList(text, 18, true, false, null, new BigDecimal("12.50")), parameterized.parameters());
        assertEquals(expectedSql, inlined.sql());
        assertTrue(inlined.parameters().isEmpty());
        assertEquals(parameterized, query.render(dialect));
    }

    @Test
    void inlinedModePropagatesThroughCtesSubqueriesAndSetOperations() {
        var source = select(param(7).as("id")).build().unionAll(select(param(8)).build());
        var inner = select(param(9)).build();
        var query = select(inner.scalar())
                .with("base", source)
                .from("base")
                .where(column("id").eq(10))
                .build();
        var inlined = query.render(POSTGRESQL, INLINED);

        assertEquals("WITH \"base\" AS (SELECT 7 AS \"id\" UNION ALL SELECT 8)"
                + " SELECT (SELECT 9) FROM \"base\" WHERE (\"id\" = 10)", inlined.sql());
        assertTrue(inlined.parameters().isEmpty());
        assertEquals(List.of(7, 8, 9, 10), query.render(POSTGRESQL).parameters());
    }

    @Test
    void rawFragmentsPreserveQuestionMarksInBothRenderModes() {
        String hostileValue = "'); DROP TABLE x; --";
        var command = query(
                raw("CALL "), identifier("do.work"), raw("('?literal', "),
                param(hostileValue), raw(", "), param(null), raw(")"));

        var parameterized = command.render(ANSI);
        var inlined = command.render(ANSI, INLINED);

        assertEquals("CALL \"do.work\"('?literal', ?, ?)", parameterized.sql());
        assertEquals(Arrays.asList(hostileValue, null), parameterized.parameters());

        assertEquals("CALL \"do.work\"('?literal', '''); DROP TABLE x; --', NULL)", inlined.sql());
        assertTrue(inlined.parameters().isEmpty());
    }

    @Test
    void unsupportedLiteralsFailInQueriesAndDdlButRemainBindable() {
        for (Object unsupportedValue : List.of(new Object(), Double.NaN, Float.POSITIVE_INFINITY, "a\0b")) {
            var query = select(param(unsupportedValue)).build();
            var schema = createTable("items")
                    .column(column("value", DataType.TEXT).defaultValue(unsupportedValue))
                    .build();

            assertThrows(IllegalArgumentException.class, () -> query.render(ANSI, INLINED));
            assertThrows(IllegalArgumentException.class, () -> schema.render(ANSI));
            assertEquals(List.of(unsupportedValue), query.render(ANSI).parameters());
        }

        var query = select(param(1)).build();

        assertThrows(NullPointerException.class, () -> query.render(ANSI, null));
    }

    @Test
    void selectJoinsGroupsAndHavingKeepTextualParameterOrder() {
        var query = select(column("u.region"), sum(column("o.total").plus(3)).as("total"))
                .distinct()
                .from(table("users").as("u"))
                .leftJoin(table("orders").as("o"),
                        column("o.user_id").eq(column("u.id")).and(column("o.state").eq("paid")))
                .where(column("u.age").ge(18).and(column("u.disabled").eq(null)))
                .groupBy(column("u.region"))
                .having(sum(column("o.total")).gt(100))
                .orderBy(column("u.region").asc())
                .limit(10)
                .offset(20)
                .build().render(POSTGRESQL);

        String expectedSql = "SELECT DISTINCT \"u\".\"region\", SUM((\"o\".\"total\" + ?)) AS \"total\""
                + " FROM \"users\" AS \"u\""
                + " LEFT JOIN \"orders\" AS \"o\" ON ((\"o\".\"user_id\" = \"u\".\"id\") AND (\"o\".\"state\" = ?))"
                + " WHERE ((\"u\".\"age\" >= ?) AND (\"u\".\"disabled\" IS NULL))"
                + " GROUP BY \"u\".\"region\""
                + " HAVING (SUM(\"o\".\"total\") > ?)"
                + " ORDER BY \"u\".\"region\" ASC"
                + " LIMIT 10"
                + " OFFSET 20";

        assertEquals(expectedSql, query.sql());
        assertEquals(List.of(3, "paid", 18, 100), query.parameters());
    }

    @Test
    void nestedQueriesUseOneParameterCollector() {
        var inner = select(param("projection"))
                .from("events")
                .where(column("kind").eq("signup"))
                .build();
        var query = select(inner.scalar())
                .from(inner.as("x"))
                .where(exists(inner))
                .build().render(ANSI);

        assertEquals(List.of("projection", "signup", "projection", "signup", "projection", "signup"),
                query.parameters());
        assertEquals(6, query.sql().chars().filter(c -> c == '?').count());
    }

    @Test
    void cteParametersPrecedeOuterProjectionAndFilters() {
        var source = select(param(7).as("id")).build();
        var query = select(param(8))
                .with("base", source)
                .from("base")
                .where(column("id").eq(9))
                .build()
                .render(POSTGRESQL);

        assertEquals("WITH \"base\" AS (SELECT ? AS \"id\") SELECT ? FROM \"base\" WHERE (\"id\" = ?)", query.sql());
        assertEquals(List.of(7, 8, 9), query.parameters());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.github.ceduz19.worstquery.DialectCases#all")
    void recursiveCteKeepsRecursiveMemberDirect(SqlDialect dialect) {
        var anchor = select(raw("1")).build();
        var recursive = select(column("n").plus(1))
                .from("numbers")
                .where(column("n").lt(5))
                .build();
        var query = select(column("n"))
                .withRecursive("numbers", anchor.unionAll(recursive), "n")
                .from("numbers")
                .build().render(dialect);

        assertTrue(query.sql().startsWith(dialect == ORACLE || dialect == SQL_SERVER ? "WITH " : "WITH RECURSIVE "));
        assertTrue(query.sql().contains(" UNION ALL SELECT "));
        assertFalse(query.sql().contains("wq_right"));
        assertEquals(List.of(1, 5), query.parameters());
    }

    @Test
    void oracleRecursiveCteRequiresColumnNames() {
        var query = select(star())
                .withRecursive("x", select(param(1))
                        .build())
                .from("x")
                .build();

        assertThrows(IllegalArgumentException.class, () -> query.render(ORACLE));
    }

    @Test
    void mixedSetOperatorsPreserveExplicitGrouping() {
        var query = select(param(1)).build().union(select(param(2)).build()).intersect(select(param(3)).build())
                .render(SQLITE);

        assertEquals("SELECT * FROM (SELECT ? UNION SELECT ?) AS \"wq_left\" INTERSECT SELECT ?", query.sql());
        assertEquals(List.of(1, 2, 3), query.parameters());
    }

    @Test
    void orderedSetOperandIsWrapped() {
        var query = select(column("id"))
                .from("a")
                .orderBy(column("id").desc())
                .limit(2)
                .build()
                .unionAll(select(column("id"))
                        .from("b")
                        .build()).render(SQLITE);

        String expectedSql = "SELECT * FROM (SELECT \"id\" FROM \"a\" ORDER BY \"id\" DESC LIMIT 2) AS \"wq_left\""
                + " UNION ALL SELECT \"id\" FROM \"b\"";

        assertEquals(expectedSql, query.sql());
    }

    @Test
    void sqlServerRejectsOrderByWithoutPaginationInDerivedQuery() {
        var ordered = select(column("id"))
                .from("a")
                .orderBy(column("id").asc())
                .build();

        assertThrows(UnsupportedSqlFeatureException.class,
                () -> select(star())
                        .from(ordered.as("q"))
                        .build().render(SQL_SERVER));
        assertThrows(UnsupportedSqlFeatureException.class, () -> select(ordered.scalar()).build().render(SQL_SERVER));
    }

    @Test
    void sqlServerRejectsNestedWith() {
        var inner = select(star())
                .with("a", select(param(1).as("id"))
                        .build())
                .from("a")
                .build();

        assertThrows(UnsupportedSqlFeatureException.class,
                () -> select(star())
                        .from(inner.as("q"))
                        .build().render(SQL_SERVER));
    }

    @Test
    void caseAndWindowAggregatePreserveSqlAndParameterOrder() {
        Expression category = caseWhen(column("score").ge(90), "A")
                .when(column("score").ge(60), "B")
                .otherwise("C")
                .build();
        Expression running = sum(column("score")).over(window()
                .partitionBy(column("team"))
                .orderBy(column("id").asc())
                .rowsBetween(FrameBound.UNBOUNDED_PRECEDING, FrameBound.CURRENT_ROW));
        var result = select(category.as("grade"), running.as("running"))
                .from("scores")
                .build().render(ANSI);

        String expectedSql = "SELECT CASE WHEN (\"score\" >= ?) THEN ?"
                + " WHEN (\"score\" >= ?) THEN ? ELSE ? END AS \"grade\", "
                + "SUM(\"score\") OVER (PARTITION BY \"team\""
                + " ORDER BY \"id\" ASC"
                + " ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS \"running\""
                + " FROM \"scores\"";

        assertEquals(expectedSql, result.sql());
        assertEquals(List.of(90, "A", 60, "B", "C"), result.parameters());
    }

    @Test
    void nullAndEmptyMembershipHaveDefinedSemantics() {
        var query = select(star())
                .from("t")
                .where(column("a").eq(null).and(column("b").ne(null))
                        .and(column("c").in()).and(column("d").notIn()))
                .build().render(ANSI);

        assertEquals("SELECT * FROM \"t\" WHERE ((((\"a\" IS NULL) AND (\"b\" IS NOT NULL)) AND (1 = 0)) AND (1 = 1))",
                query.sql());
        assertTrue(query.parameters().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> column("a").gt(null));
    }

    @Test
    void inPredicateKeepsNullAsAnOrderedParameter() {
        var result = select(star())
                .from("t")
                .where(column("id").in(1, null, 3))
                .build().render(ANSI);

        assertEquals(Arrays.asList(1, null, 3), result.parameters());
        assertEquals("SELECT * FROM \"t\" WHERE (\"id\" IN (?, ?, ?))", result.sql());
    }

    @Test
    void predicatesAndArithmeticPreserveParentheses() {
        Condition predicate = column("n").between(1, 9).or(column("name").like("%x%")).not();
        var result = select(column("n").plus(2).multiply(3))
                .from("t")
                .where(predicate)
                .build().render(ANSI);

        assertEquals("SELECT ((\"n\" + ?) * ?) FROM \"t\" WHERE (NOT ((\"n\" BETWEEN ? AND ?) OR (\"name\" LIKE ?)))",
                result.sql());
        assertEquals(List.of(2, 3, 1, 9, "%x%"), result.parameters());
    }

    @Test
    void inQueryAndNotExistsAreComposable() {
        var source = select(column("id"))
                .from("b")
                .where(column("active").eq(true))
                .build();
        var query = select(star())
                .from("a")
                .where(column("id").in(source).and(notExists(source)))
                .build().render(ANSI);

        assertEquals(List.of(true, true), query.parameters());
        assertTrue(query.sql().contains(" IN (SELECT "));
        assertTrue(query.sql().contains("(NOT EXISTS (SELECT "));
    }

    @Test
    void identifierPartsAreEscapedSeparately() {
        var qualifiedNames = select(identifier("schema", "a\"b"), identifier("a.b"), star("t")).build();
        var bracketName = select(identifier("a]b")).build();
        var backtickName = select(identifier("a`b")).build();

        assertEquals("SELECT \"schema\".\"a\"\"b\", \"a.b\", \"t\".*", qualifiedNames.render(ANSI).sql());
        assertEquals("SELECT [a]]b]", bracketName.render(SQL_SERVER).sql());
        assertEquals("SELECT `a``b`", backtickName.render(MYSQL).sql());

        assertThrows(IllegalArgumentException.class, () -> Identifier.of("a..b"));
        assertThrows(IllegalArgumentException.class, () -> identifier("bad\0name"));
    }

    @Test
    void builtQueriesAreSnapshotsAndRenderResultsAreReadOnly() {
        Expression[] columns = {column("id")};
        var builder = select(columns)
                .from("a")
                .where(column("id").eq(1));
        var first = builder.build();

        columns[0] = star();
        builder.select(column("name"))
                .where(column("id").eq(2));

        assertEquals("SELECT \"id\" FROM \"a\" WHERE (\"id\" = ?)", first.render(ANSI).sql());
        assertEquals(List.of(1), first.render(ANSI).parameters());
        assertEquals(List.of(2), builder.build().render(ANSI).parameters());
        assertThrows(UnsupportedOperationException.class, () -> first.render(ANSI).parameters().add(2));
    }

    @Test
    void changingIdentifierSourceDoesNotChangeRenderedNames() {
        List<String> names = new ArrayList<>(List.of("schema", "table"));
        Identifier identifier = new Identifier(names);

        names.set(0, "changed");
        var result = select(identifier).build().render(ANSI);

        assertEquals("SELECT \"schema\".\"table\"", result.sql());
    }

    @Test
    void rawSelectSnapshotsItsPartsAndComposesAsADerivedTable() {
        SqlPart[] parts = {raw("SELECT "), param(1)};
        var source = selectSql(parts);

        parts[1] = param(9);
        var result = select(star())
                .from(source.as("source"))
                .build().render(ANSI);

        assertEquals("SELECT * FROM (SELECT ?) AS \"source\"", result.sql());
        assertEquals(List.of(1), result.parameters());
    }

    @Test
    void builtCaseKeepsOriginalBranchesAndNullElse() {
        var builder = caseWhen(alwaysTrue(), 1)
                .otherwise(null);
        var original = builder.build();

        builder.when(alwaysFalse(), 2)
                .otherwise(3);
        var result = select(original).build().render(ANSI);

        assertEquals("SELECT CASE WHEN (1 = 1) THEN ? ELSE ? END", result.sql());
        assertEquals(Arrays.asList(1, null), result.parameters());
    }

    @Test
    void addingWindowPartitionLeavesOriginalSqlUnchanged() {
        var original = window()
                .orderBy(column("id").asc());
        var partitioned = original.partitionBy(column("team"));

        var originalResult = select(rowNumber().over(original)).build().render(ANSI);
        var partitionedResult = select(rowNumber().over(partitioned)).build().render(ANSI);

        assertEquals("SELECT ROW_NUMBER() OVER (ORDER BY \"id\" ASC)", originalResult.sql());
        assertEquals("SELECT ROW_NUMBER() OVER (PARTITION BY \"team\" ORDER BY \"id\" ASC)",
                partitionedResult.sql());
    }

    @Test
    void renderIsSafeAcrossIndependentConcurrentSessions() throws Exception {
        var query = select(param(null), column("id"))
                .from("t")
                .where(column("id").eq(2))
                .build();
        var expected = query.render(POSTGRESQL);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            List<Callable<RenderedQuery>> jobs = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                jobs.add(() -> query.render(POSTGRESQL));
            }
            List<Future<RenderedQuery>> results = executor.invokeAll(jobs);

            for (Future<RenderedQuery> future : results) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void incompleteQueriesAndInvalidExpressionsFailEarly() {
        var joinWithoutSource = select(star()).join(table("orders"), alwaysTrue());
        var duplicateCte = select(star()).with("items", select(param(1)).build());

        assertThrows(IllegalArgumentException.class, () -> select().build());
        assertThrows(IllegalStateException.class, joinWithoutSource::build);
        assertThrows(IllegalArgumentException.class, () -> duplicateCte.with("items", select(param(2)).build()));

        assertThrows(IllegalArgumentException.class, () -> select(star()).limit(-1));
        assertThrows(IllegalArgumentException.class, () -> select(star()).offset(-1));

        assertThrows(NullPointerException.class, () -> caseWhen(null, 1));
        assertThrows(IllegalArgumentException.class, () -> function("COUNT); DROP TABLE t; --", star()));
    }
}
