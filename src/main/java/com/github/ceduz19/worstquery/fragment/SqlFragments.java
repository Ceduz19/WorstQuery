package com.github.ceduz19.worstquery.fragment;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.api.SelectQuery;
import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.spi.SqlPart;

import java.util.List;
import java.util.Objects;

/**
 * Explicit trusted SQL syntax composed with identifiers and bind values.
 */
public final class SqlFragments {
    private SqlFragments() {
    }

    /**
     * Trusted SQL syntax, copied unchanged. Never pass untrusted input here.
     * A question mark inside this text does not register a bind parameter.
     */
    public static Expression raw(String trustedSql) {
        Objects.requireNonNull(trustedSql, "trustedSql");
        return c -> c.sql(trustedSql);
    }

    /**
     * Concatenates nodes exactly, without inserting spaces or parsing SQL.
     */
    public static Expression fragment(SqlPart... parts) {
        List<SqlPart> snapshot = List.of(parts);
        return c -> snapshot.forEach(c::part);
    }

    /**
     * Concatenates explicit nodes and treats the result as a predicate.
     */
    public static Condition condition(SqlPart... parts) {
        Expression expression = fragment(parts);
        return expression::appendTo;
    }

    /**
     * Builds an arbitrary command from trusted syntax, identifiers, and bind parameters.
     */
    public static Query query(SqlPart... parts) {
        Expression expression = fragment(parts);
        return expression::appendTo;
    }

    /**
     * Declares arbitrary SQL as a row-returning query, for composition into subqueries.
     */
    public static SelectQuery selectSql(SqlPart... parts) {
        Expression expression = fragment(parts);
        return expression::appendTo;
    }
}
