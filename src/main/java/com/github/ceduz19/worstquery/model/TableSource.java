package com.github.ceduz19.worstquery.model;

import com.github.ceduz19.worstquery.spi.SqlPart;

/**
 * A table, aliased table, or derived query in a FROM or JOIN clause.
 */
@FunctionalInterface
public interface TableSource extends SqlPart {
}
