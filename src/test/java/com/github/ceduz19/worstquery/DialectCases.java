package com.github.ceduz19.worstquery;

import com.github.ceduz19.worstquery.spi.SqlDialect;

import java.util.stream.Stream;

import static com.github.ceduz19.worstquery.dialect.Dialects.ANSI;
import static com.github.ceduz19.worstquery.dialect.Dialects.MARIADB;
import static com.github.ceduz19.worstquery.dialect.Dialects.MYSQL;
import static com.github.ceduz19.worstquery.dialect.Dialects.ORACLE;
import static com.github.ceduz19.worstquery.dialect.Dialects.POSTGRESQL;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQLITE;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQL_SERVER;

/**
 * Shared dialect matrix; built-in dialects are constants, not an enum API.
 */
final class DialectCases {
    private DialectCases() {
    }

    static Stream<SqlDialect> all() {
        return Stream.of(ANSI, POSTGRESQL, MYSQL, MARIADB, SQLITE, SQL_SERVER, ORACLE);
    }

    static Stream<SqlDialect> withoutAllSetOperations() {
        return Stream.of(SQLITE, SQL_SERVER, ORACLE);
    }
}
