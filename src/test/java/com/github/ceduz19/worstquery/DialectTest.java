package com.github.ceduz19.worstquery;

import com.github.ceduz19.worstquery.api.UnsupportedSqlFeatureException;
import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.expression.FrameBound;
import com.github.ceduz19.worstquery.spi.SqlDialect;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.ceduz19.worstquery.dialect.Dialects.ANSI;
import static com.github.ceduz19.worstquery.dialect.Dialects.MARIADB;
import static com.github.ceduz19.worstquery.dialect.Dialects.MYSQL;
import static com.github.ceduz19.worstquery.dialect.Dialects.ORACLE;
import static com.github.ceduz19.worstquery.dialect.Dialects.POSTGRESQL;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQLITE;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQL_SERVER;
import static com.github.ceduz19.worstquery.dml.Queries.select;
import static com.github.ceduz19.worstquery.expression.Expressions.column;
import static com.github.ceduz19.worstquery.expression.Expressions.param;
import static com.github.ceduz19.worstquery.expression.Expressions.star;
import static com.github.ceduz19.worstquery.expression.Expressions.table;
import static com.github.ceduz19.worstquery.expression.Expressions.window;
import static com.github.ceduz19.worstquery.expression.Functions.rowNumber;
import static com.github.ceduz19.worstquery.expression.Functions.sum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialectTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.github.ceduz19.worstquery.DialectCases#all")
    void paginationHasExactDialectSpecificSql(SqlDialect dialect) {
        Map<SqlDialect, String> expected = Map.of(
                ANSI, "SELECT \"id\" FROM \"t\" ORDER BY \"id\" ASC OFFSET 5 ROWS FETCH FIRST 10 ROWS ONLY",
                POSTGRESQL, "SELECT \"id\" FROM \"t\" ORDER BY \"id\" ASC LIMIT 10 OFFSET 5",
                MYSQL, "SELECT `id` FROM `t` ORDER BY `id` ASC LIMIT 10 OFFSET 5",
                MARIADB, "SELECT `id` FROM `t` ORDER BY `id` ASC LIMIT 10 OFFSET 5",
                SQLITE, "SELECT \"id\" FROM \"t\" ORDER BY \"id\" ASC LIMIT 10 OFFSET 5",
                SQL_SERVER, "SELECT [id] FROM [t] ORDER BY [id] ASC OFFSET 5 ROWS FETCH NEXT 10 ROWS ONLY",
                ORACLE, "SELECT \"id\" FROM \"t\" ORDER BY \"id\" ASC OFFSET 5 ROWS FETCH FIRST 10 ROWS ONLY");

        var statement = select(column("id"))
                .from("t")
                .orderBy(column("id").asc())
                .limit(10)
                .offset(5)
                .build();

        var result = statement.render(dialect);

        assertEquals(expected.get(dialect), result.sql());
    }

    @Test
    void offsetOnlyAndLimitOnlyUseDialectSpecificDefaults() {
        var offsetOnly = select(star())
                .from("items")
                .orderBy(column("id").asc())
                .offset(5)
                .build();
        var limitOnly = select(star())
                .from("items")
                .orderBy(column("id").asc())
                .limit(2)
                .build();

        assertTrue(offsetOnly.render(SQLITE).sql().endsWith("LIMIT -1 OFFSET 5"));
        assertTrue(offsetOnly.render(MYSQL).sql().endsWith("LIMIT 18446744073709551615 OFFSET 5"));
        assertTrue(offsetOnly.render(POSTGRESQL).sql().endsWith("ASC OFFSET 5"));
        assertTrue(offsetOnly.render(SQL_SERVER).sql().endsWith("ASC OFFSET 5 ROWS"));
        assertTrue(limitOnly.render(SQL_SERVER).sql().endsWith("OFFSET 0 ROWS FETCH NEXT 2 ROWS ONLY"));
    }

    @Test
    void unsupportedPaginationIsRejectedWithoutChangingItsMeaning() {
        var unorderedPage = select(star()).from("items").limit(2).build();
        var emptyPage = select(star())
                .from("items")
                .orderBy(column("id").asc())
                .limit(0)
                .build();

        assertThrows(UnsupportedSqlFeatureException.class, () -> unorderedPage.render(SQL_SERVER));

        for (SqlDialect dialect : List.of(ANSI, SQL_SERVER, ORACLE)) {
            assertThrows(UnsupportedSqlFeatureException.class, () -> emptyPage.render(dialect), dialect.name());
        }

        assertTrue(emptyPage.render(SQLITE).sql().endsWith("LIMIT 0"));
    }

    @Test
    void fullJoinSupportIsChecked() {
        var query = select(star())
                .from("a")
                .fullJoin(table("b"), column("a.id").eq(column("b.id")))
                .build();
        for (SqlDialect dialect : List.of(MYSQL, MARIADB)) {
            assertThrows(UnsupportedSqlFeatureException.class, () -> query.render(dialect));
        }

        assertTrue(query.render(POSTGRESQL).sql().contains(" FULL JOIN "));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.github.ceduz19.worstquery.DialectCases#withoutAllSetOperations")
    void unsupportedAllSetOperationsFail(SqlDialect dialect) {
        var a = select(param(1)).build();

        assertThrows(UnsupportedSqlFeatureException.class, () -> a.intersectAll(a).render(dialect));
        assertThrows(UnsupportedSqlFeatureException.class, () -> a.exceptAll(a).render(dialect));
    }

    @Test
    void oracleUsesMinusAndOmitsAsForTableAliases() {
        var a = select(star())
                .from(table("t").as("x"))
                .build();

        assertEquals("SELECT * FROM \"t\" \"x\" MINUS SELECT * FROM \"t\" \"x\"", a.except(a).render(ORACLE).sql());
        assertEquals("SELECT ? FROM DUAL", select(param(1)).build().render(ORACLE).sql());
    }

    @Test
    void windowFramesValidateBoundsAndDialectSupport() {
        var range = rowNumber().over(window()
                .orderBy(column("id").asc())
                .rangeBetween(FrameBound.preceding(2), FrameBound.CURRENT_ROW));
        var groups = sum(column("n")).over(window()
                .orderBy(column("id").asc())
                .groupsBetween(FrameBound.preceding(1), FrameBound.CURRENT_ROW));
        var rangeQuery = select(range).from("items").build();
        var groupsQuery = select(groups).from("items").build();

        assertThrows(UnsupportedSqlFeatureException.class, () -> rangeQuery.render(SQL_SERVER));
        assertThrows(UnsupportedSqlFeatureException.class, () -> groupsQuery.render(MYSQL));
        assertTrue(groupsQuery.render(POSTGRESQL).sql().contains("GROUPS BETWEEN 1 PRECEDING AND CURRENT ROW"));

        assertThrows(IllegalArgumentException.class,
                () -> window().rowsBetween(FrameBound.following(2), FrameBound.preceding(1)));
        assertThrows(IllegalArgumentException.class, () -> FrameBound.preceding(-1));
    }

    @Test
    void customNodesAndDialectParticipateInSqlAndParameterRendering() {
        SqlDialect dialect = new SqlDialect() {
            @Override
            public String name() {
                return "Custom";
            }

            @Override
            public String quoteIdentifier(String name) {
                return "<" + name.replace(">", ">>") + ">";
            }

            @Override
            public void pagination(SqlWriter c, Long limit, Long offset, boolean ordered) {
                if (limit != null) {
                    c.sql(" TAKE ").value(limit);
                }
            }
        };
        Expression addition = writer -> writer.sql("(").value(2).sql(" + ").value(3).sql(")");
        var statement = select(addition.as("total"))
                .from("t")
                .where(column("id").eq(5))
                .limit(3)
                .build();

        var result = statement.render(dialect);

        assertEquals("SELECT (? + ?) AS <total> FROM <t> WHERE (<id> = ?) TAKE ?", result.sql());
        assertEquals(List.of(2, 3, 5, 3L), result.parameters());
    }
}
