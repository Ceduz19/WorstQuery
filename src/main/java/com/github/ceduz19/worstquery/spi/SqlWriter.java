package com.github.ceduz19.worstquery.spi;

import java.util.List;

/**
 * Write-only SQL rendering SPI supplied to custom nodes and dialects.
 * A writer belongs to one rendering session and must not be retained or shared.
 */
public interface SqlWriter {
    /**
     * Returns the dialect selected for the current rendering session.
     */
    SqlDialect dialect();

    /**
     * Appends trusted SQL syntax, without interpreting placeholders.
     */
    SqlWriter sql(String text);

    /**
     * Appends a node in the current context.
     */
    SqlWriter part(SqlPart part);

    /**
     * Appends one escaped identifier component.
     */
    SqlWriter identifier(String identifier);

    /**
     * Appends a bind value, or a dialect literal in inlined mode or a literal subtree.
     */
    SqlWriter value(Object value);

    /**
     * Whether the current node belongs to a nested query.
     */
    boolean inSubquery();

    /**
     * Appends a nested query and restores the previous context even on failure.
     */
    SqlWriter subquery(SqlPart part);

    /**
     * Appends a DDL subtree using escaped literals instead of bind values.
     */
    SqlWriter literalPart(SqlPart part);

    /**
     * Appends nodes separated by trusted SQL syntax.
     */
    default SqlWriter separated(List<? extends SqlPart> parts, String separator) {
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                sql(separator);
            }
            part(parts.get(index));
        }
        return this;
    }
}
