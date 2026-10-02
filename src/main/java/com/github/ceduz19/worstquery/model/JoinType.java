package com.github.ceduz19.worstquery.model;

/**
 * Explicit joins; CROSS does not accept an ON condition.
 */
public enum JoinType {
    INNER, LEFT, RIGHT, FULL, CROSS
}
