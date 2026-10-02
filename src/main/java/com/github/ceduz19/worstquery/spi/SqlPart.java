package com.github.ceduz19.worstquery.spi;

/**
 * A composable SQL node. Implementations must be immutable to be reusable.
 */
@FunctionalInterface
public interface SqlPart {
    /**
     * Appends this node to the current rendering session.
     */
    void appendTo(SqlWriter context);
}
