package com.github.ceduz19.worstquery.api;

import com.github.ceduz19.worstquery.internal.render.RenderContext;
import com.github.ceduz19.worstquery.spi.SqlDialect;
import com.github.ceduz19.worstquery.spi.SqlPart;

/**
 * An immutable statement, reusable across independent rendering sessions.
 */
@FunctionalInterface
public interface Query extends SqlPart {
    /**
     * Renders SQL with placeholders and ordered parameters without accessing a database.
     */
    default RenderedQuery render(SqlDialect dialect) {
        return render(dialect, RenderMode.PARAMETERIZED);
    }

    /**
     * Renders SQL using the selected value representation without accessing a database.
     * Inlined rendering has no bind parameters and rejects values the dialect cannot encode.
     * Trusted raw SQL is preserved without interpreting question marks.
     *
     * @throws NullPointerException     if the dialect or mode is null
     * @throws IllegalArgumentException if a literal cannot be encoded by the dialect
     */
    default RenderedQuery render(SqlDialect dialect, RenderMode mode) {
        return RenderContext.render(this, dialect, mode);
    }
}
