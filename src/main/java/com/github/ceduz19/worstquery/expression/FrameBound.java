package com.github.ceduz19.worstquery.expression;

import com.github.ceduz19.worstquery.spi.SqlPart;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.Objects;

/**
 * One boundary of a window frame.
 */
public final class FrameBound implements SqlPart {

    private final Kind kind;
    private final long offset;

    public enum Kind {UNBOUNDED_PRECEDING, PRECEDING, CURRENT_ROW, FOLLOWING, UNBOUNDED_FOLLOWING}

    public static final FrameBound UNBOUNDED_PRECEDING = new FrameBound(Kind.UNBOUNDED_PRECEDING, 0);
    public static final FrameBound CURRENT_ROW = new FrameBound(Kind.CURRENT_ROW, 0);
    public static final FrameBound UNBOUNDED_FOLLOWING = new FrameBound(Kind.UNBOUNDED_FOLLOWING, 0);

    private FrameBound(Kind kind, long offset) {
        Objects.requireNonNull(kind);
        if (offset < 0 || offset == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid frame offset");
        }
        if (kind != Kind.PRECEDING && kind != Kind.FOLLOWING && offset != 0) {
            throw new IllegalArgumentException("Offset not applicable to " + kind);
        }

        this.kind = kind;
        this.offset = offset;
    }

    public static FrameBound preceding(long offset) {
        return new FrameBound(Kind.PRECEDING, offset);
    }

    public static FrameBound following(long offset) {
        return new FrameBound(Kind.FOLLOWING, offset);
    }

    long position() {
        return switch (kind) {
            case UNBOUNDED_PRECEDING -> Long.MIN_VALUE;
            case PRECEDING -> -offset;
            case CURRENT_ROW -> 0;
            case FOLLOWING -> offset;
            case UNBOUNDED_FOLLOWING -> Long.MAX_VALUE;
        };
    }

    boolean hasOffset() {
        return kind == Kind.PRECEDING || kind == Kind.FOLLOWING;
    }

    @Override
    public void appendTo(SqlWriter c) {
        if (hasOffset()) {
            c.sql(offset + " ");
        }
        c.sql(kind.name().replace('_', ' '));
    }

    public Kind kind() {
        return kind;
    }

    public long offset() {
        return offset;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FrameBound value)) {
            return false;
        }
        return Objects.equals(kind, value.kind)
                && Objects.equals(offset, value.offset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, offset);
    }

    @Override
    public String toString() {
        return "FrameBound[kind=" + kind + ", offset=" + offset + "]";
    }
}
