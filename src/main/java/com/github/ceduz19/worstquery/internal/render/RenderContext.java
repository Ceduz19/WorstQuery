package com.github.ceduz19.worstquery.internal.render;

import com.github.ceduz19.worstquery.api.RenderedQuery;
import com.github.ceduz19.worstquery.api.RenderMode;
import com.github.ceduz19.worstquery.spi.SqlDialect;
import com.github.ceduz19.worstquery.spi.SqlPart;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A single rendering session. Never share a context between threads or statements.
 */
public final class RenderContext implements SqlWriter {

    private final SqlDialect dialect;
    private final RenderMode mode;
    private final StringBuilder sql = new StringBuilder();
    private final List<Object> parameters = new ArrayList<>();
    private int literalDepth;
    private int subqueryDepth;

    RenderContext(SqlDialect dialect) {
        this(dialect, RenderMode.PARAMETERIZED);
    }

    private RenderContext(SqlDialect dialect, RenderMode mode) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * Renders one node in a fresh session and snapshots the resulting SQL and parameters.
     */
    public static RenderedQuery render(SqlPart part, SqlDialect dialect, RenderMode mode) {
        RenderContext context = new RenderContext(dialect, mode);
        context.part(part);
        return context.result();
    }

    @Override
    public SqlDialect dialect() {
        return dialect;
    }

    /**
     * Appends trusted SQL syntax; does not escape text or discover placeholders.
     */
    @Override
    public SqlWriter sql(String text) {
        sql.append(Objects.requireNonNull(text));
        return this;
    }

    /**
     * Appends a node without changing nesting or literal mode.
     */
    @Override
    public SqlWriter part(SqlPart part) {
        Objects.requireNonNull(part).appendTo(this);
        return this;
    }

    /**
     * Whether rendering is currently inside a nested query.
     */
    @Override
    public boolean inSubquery() {
        return subqueryDepth > 0;
    }

    /**
     * Appends a node in nested-query mode and restores the previous mode afterwards.
     */
    @Override
    public SqlWriter subquery(SqlPart part) {
        subqueryDepth++;
        try {
            return part(part);
        } finally {
            subqueryDepth--;
        }
    }

    /**
     * Appends one quoted identifier component.
     */
    @Override
    public SqlWriter identifier(String identifier) {
        return sql(dialect.quoteIdentifier(identifier));
    }

    /**
     * Appends a bind parameter, or an escaped literal when requested by the mode or subtree.
     */
    @Override
    public SqlWriter value(Object value) {
        if (mode == RenderMode.INLINED || literalDepth > 0) {
            return sql(dialect.literal(value));
        }

        parameters.add(value);
        return sql("?");
    }

    /**
     * DDL cannot bind values: render a subtree using dialect-specific literals.
     */
    @Override
    public SqlWriter literalPart(SqlPart part) {
        literalDepth++;
        try {
            return part(part);
        } finally {
            literalDepth--;
        }
    }

    /**
     * Copies the current SQL text and parameter list into an immutable result.
     */
    RenderedQuery result() {
        return new RenderedQuery(sql.toString(), parameters);
    }
}
