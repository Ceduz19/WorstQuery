package com.github.ceduz19.worstquery.expression;

import com.github.ceduz19.worstquery.spi.SqlPart;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.Objects;

/**
 * A sort key. Database default ordering of nulls is retained.
 */
public record Sort(Expression expression, Direction direction) implements SqlPart {

    public enum Direction {
        ASC,
        DESC
    }

    public Sort {
        Objects.requireNonNull(expression);
        Objects.requireNonNull(direction);
    }

    @Override
    public void appendTo(SqlWriter c) {
        c.part(expression).sql(" " + direction.name());
    }
}
