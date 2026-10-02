package com.github.ceduz19.worstquery.internal.dialect;

import com.github.ceduz19.worstquery.spi.SqlDialect;
import com.github.ceduz19.worstquery.spi.SqlWriter;

/**
 * Shared ANSI defaults and helpers for the built-in implementations.
 */
class StandardDialect implements SqlDialect {
    private final String name;

    StandardDialect(String name) {
        this.name = name;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final String toString() {
        return name;
    }

    final void appendLimitOffset(SqlWriter writer, Long limit, Long offset, String offsetOnlyLimit) {
        if (limit != null) {
            writer.sql(" LIMIT " + limit);
        } else if (offset != null && offsetOnlyLimit != null) {
            writer.sql(" LIMIT " + offsetOnlyLimit);
        }
        if (offset != null) {
            writer.sql(" OFFSET " + offset);
        }
    }
}
