package com.github.ceduz19.worstquery.internal;

/**
 * Internal metadata; custom SELECT nodes are conservatively wrapped.
 */
public interface SetOperand {
    boolean requiresSetOperandWrapping();
}
