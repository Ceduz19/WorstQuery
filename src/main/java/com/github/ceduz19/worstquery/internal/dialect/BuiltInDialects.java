package com.github.ceduz19.worstquery.internal.dialect;

import com.github.ceduz19.worstquery.spi.SqlDialect;

/**
 * Module-internal bridge to package-private dialect implementations.
 */
public final class BuiltInDialects {

    public static final SqlDialect ANSI = new StandardDialect("ANSI");
    public static final SqlDialect POSTGRESQL = new PostgreSqlDialect();
    public static final SqlDialect MYSQL = new MySqlDialect("MYSQL");
    public static final SqlDialect MARIADB = new MariaDbDialect();
    public static final SqlDialect SQLITE = new SqliteDialect();
    public static final SqlDialect SQL_SERVER = new SqlServerDialect();
    public static final SqlDialect ORACLE = new OracleDialect();

    private BuiltInDialects() {
    }
}
