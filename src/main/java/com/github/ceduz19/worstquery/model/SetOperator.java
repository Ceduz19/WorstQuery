package com.github.ceduz19.worstquery.model;

/**
 * Set operations; support for ALL variants depends on the dialect.
 */
public enum SetOperator {
    UNION("UNION"),
    UNION_ALL("UNION ALL"),
    INTERSECT("INTERSECT"),
    INTERSECT_ALL("INTERSECT ALL"),
    EXCEPT("EXCEPT"),
    EXCEPT_ALL("EXCEPT ALL");

    private final String sql;

    SetOperator(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
