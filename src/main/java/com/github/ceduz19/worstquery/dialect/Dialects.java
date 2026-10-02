package com.github.ceduz19.worstquery.dialect;

import com.github.ceduz19.worstquery.internal.dialect.BuiltInDialects;
import com.github.ceduz19.worstquery.spi.SqlDialect;

/**
 * Reusable built-in dialects. Implementations are internal and immutable.
 * Baselines: PostgreSQL 14, MySQL 8.0.31, MariaDB 10.6, SQLite 3.39,
 * SQL Server 2019, and Oracle 19c.
 */
public final class Dialects {

    public static final SqlDialect ANSI = BuiltInDialects.ANSI;
    public static final SqlDialect POSTGRESQL = BuiltInDialects.POSTGRESQL;
    public static final SqlDialect MYSQL = BuiltInDialects.MYSQL;
    public static final SqlDialect MARIADB = BuiltInDialects.MARIADB;
    public static final SqlDialect SQLITE = BuiltInDialects.SQLITE;
    public static final SqlDialect SQL_SERVER = BuiltInDialects.SQL_SERVER;
    public static final SqlDialect ORACLE = BuiltInDialects.ORACLE;

    private Dialects() {
    }
}
