package com.github.ceduz19.worstquery.api;

/**
 * Controls how application values are represented in generated SQL.
 */
public enum RenderMode {
    /**
     * Uses JDBC-style placeholders and collects values in textual order.
     */
    PARAMETERIZED,

    /**
     * Encodes values as dialect-specific literals, leaving the parameter list empty.
     * The resulting SQL may contain sensitive application data.
     */
    INLINED
}
