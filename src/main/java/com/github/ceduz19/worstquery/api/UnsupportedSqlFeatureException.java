package com.github.ceduz19.worstquery.api;

/**
 * The selected dialect cannot render a requested construct without changing its meaning.
 */
public final class UnsupportedSqlFeatureException extends UnsupportedOperationException {
    private static final long serialVersionUID = 1L;

    public UnsupportedSqlFeatureException(String message) {
        super(message);
    }
}
