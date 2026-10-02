package com.github.ceduz19.worstquery.expression;

import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlPart;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.List;
import java.util.Objects;

/**
 * Immutable window specification. Each fluent method returns a new specification.
 */
public final class Window implements SqlPart {

    private final List<Expression> partitions;
    private final List<Sort> ordering;
    private final Unit unit;
    private final FrameBound start;
    private final FrameBound end;

    public enum Unit {ROWS, RANGE, GROUPS}

    private Window(List<Expression> partitions, List<Sort> ordering, Unit unit, FrameBound start, FrameBound end) {
        partitions = List.copyOf(partitions);
        ordering = List.copyOf(ordering);
        if (unit == null && (start != null || end != null)) {
            throw new IllegalArgumentException("Missing frame unit");
        }
        if (unit != null) {
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            if (start.kind() == FrameBound.Kind.UNBOUNDED_FOLLOWING
                    || end.kind() == FrameBound.Kind.UNBOUNDED_PRECEDING || start.position() > end.position()) {
                throw new IllegalArgumentException("Invalid frame boundaries");
            }
        }

        this.partitions = partitions;
        this.ordering = ordering;
        this.unit = unit;
        this.start = start;
        this.end = end;
    }

    /**
     * Creates a window without partitioning, ordering, or an explicit frame.
     */
    static Window empty() {
        return new Window(List.of(), List.of(), null, null, null);
    }

    /**
     * Returns a new specification replacing the partition expressions.
     */
    public Window partitionBy(Expression... expressions) {
        return new Window(List.of(expressions), ordering, unit, start, end);
    }

    /**
     * Returns a new specification replacing the ordering keys.
     */
    public Window orderBy(Sort... sorts) {
        return new Window(partitions, List.of(sorts), unit, start, end);
    }

    /**
     * Returns a new ROWS frame with inclusive boundaries.
     */
    public Window rowsBetween(FrameBound start, FrameBound end) {
        return frame(Unit.ROWS, start, end);
    }

    /**
     * Returns a new RANGE frame; offset frames require one ordering key.
     */
    public Window rangeBetween(FrameBound start, FrameBound end) {
        return frame(Unit.RANGE, start, end);
    }

    /**
     * Returns a new GROUPS frame, subject to dialect support.
     */
    public Window groupsBetween(FrameBound start, FrameBound end) {
        return frame(Unit.GROUPS, start, end);
    }

    private Window frame(Unit unit, FrameBound start, FrameBound end) {
        return new Window(partitions, ordering, unit, start, end);
    }

    @Override
    public void appendTo(SqlWriter c) {
        if (unit != null && ordering.isEmpty()) {
            throw new IllegalArgumentException("Explicit window frames require ORDER BY");
        }
        if (unit == Unit.GROUPS) {
            c.dialect().require(Feature.WINDOW_GROUPS);
        }
        if (unit == Unit.RANGE && (start.hasOffset() || end.hasOffset())) {
            c.dialect().require(Feature.WINDOW_RANGE_OFFSET);
            if (ordering.size() != 1) {
                throw new IllegalArgumentException("RANGE offsets require one ordering expression");
            }
        }
        if (!partitions.isEmpty()) {
            c.sql("PARTITION BY ").separated(partitions, ", ");
        }
        if (!ordering.isEmpty()) {
            if (!partitions.isEmpty()) {
                c.sql(" ");
            }
            c.sql("ORDER BY ").separated(ordering, ", ");
        }
        if (unit != null) {
            c.sql(" " + unit.name() + " BETWEEN ").part(start).sql(" AND ").part(end);
        }
    }

    public List<Expression> partitions() {
        return partitions;
    }

    public List<Sort> ordering() {
        return ordering;
    }

    public Unit unit() {
        return unit;
    }

    public FrameBound start() {
        return start;
    }

    public FrameBound end() {
        return end;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Window value)) {
            return false;
        }
        return Objects.equals(partitions, value.partitions)
                && Objects.equals(ordering, value.ordering)
                && Objects.equals(unit, value.unit)
                && Objects.equals(start, value.start)
                && Objects.equals(end, value.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitions, ordering, unit, start, end);
    }

    @Override
    public String toString() {
        return "Window[partitions=" + partitions
                + ", ordering=" + ordering
                + ", unit=" + unit
                + ", start=" + start
                + ", end=" + end + "]";
    }
}
