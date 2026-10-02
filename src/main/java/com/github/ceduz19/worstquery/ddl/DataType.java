package com.github.ceduz19.worstquery.ddl;

import com.github.ceduz19.worstquery.internal.render.DdlRenderer;
import com.github.ceduz19.worstquery.spi.SqlPart;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.Objects;

/**
 * A logical SQL type, mapped by the dialect. Custom types are trusted SQL.
 */
public final class DataType implements SqlPart {

    private final Kind kind;
    private final Integer length;
    private final Integer scale;
    private final String customSql;

    public enum Kind {
        SMALLINT,
        INTEGER,
        BIGINT,
        DECIMAL,
        REAL,
        DOUBLE,
        BOOLEAN,
        VARCHAR,
        TEXT,
        DATE,
        TIME,
        TIMESTAMP,
        BINARY,
        CUSTOM
    }

    public static final DataType SMALLINT = simple(Kind.SMALLINT);
    public static final DataType INTEGER = simple(Kind.INTEGER);
    public static final DataType BIGINT = simple(Kind.BIGINT);
    public static final DataType REAL = simple(Kind.REAL);
    public static final DataType DOUBLE = simple(Kind.DOUBLE);
    public static final DataType BOOLEAN = simple(Kind.BOOLEAN);
    public static final DataType TEXT = simple(Kind.TEXT);
    public static final DataType DATE = simple(Kind.DATE);
    public static final DataType TIME = simple(Kind.TIME);
    public static final DataType TIMESTAMP = simple(Kind.TIMESTAMP);

    private DataType(Kind kind, Integer length, Integer scale, String customSql) {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.CUSTOM) {
            if (customSql == null || customSql.isBlank()) {
                throw new IllegalArgumentException("Custom type is empty");
            }
        } else if (customSql != null) {
            throw new IllegalArgumentException("Custom SQL requires CUSTOM kind");
        }

        boolean requiresLength = kind == Kind.VARCHAR || kind == Kind.BINARY || kind == Kind.DECIMAL;
        boolean hasLength = length != null;
        if (requiresLength != hasLength) {
            throw new IllegalArgumentException("Invalid length for " + kind);
        }
        if (length != null && length <= 0) {
            throw new IllegalArgumentException("Length/precision must be positive");
        }

        if (kind == Kind.DECIMAL) {
            if (scale == null || scale < 0 || scale > length) {
                throw new IllegalArgumentException("Invalid decimal scale");
            }
        } else if (scale != null) {
            throw new IllegalArgumentException("Scale only applies to DECIMAL");
        }

        this.kind = kind;
        this.length = length;
        this.scale = scale;
        this.customSql = customSql;
    }

    private static DataType simple(Kind kind) {
        return new DataType(kind, null, null, null);
    }

    public static DataType varchar(int length) {
        return new DataType(Kind.VARCHAR, length, null, null);
    }

    public static DataType binary(int length) {
        return new DataType(Kind.BINARY, length, null, null);
    }

    public static DataType decimal(int precision, int scale) {
        return new DataType(Kind.DECIMAL, precision, scale, null);
    }

    public static DataType custom(String trustedSql) {
        return new DataType(Kind.CUSTOM, null, null, trustedSql);
    }

    @Override
    public void appendTo(SqlWriter writer) {
        DdlRenderer.render(this, writer);
    }

    public Kind kind() {
        return kind;
    }

    public Integer length() {
        return length;
    }

    public Integer scale() {
        return scale;
    }

    public String customSql() {
        return customSql;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataType value)) {
            return false;
        }
        return Objects.equals(kind, value.kind)
                && Objects.equals(length, value.length)
                && Objects.equals(scale, value.scale)
                && Objects.equals(customSql, value.customSql);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, length, scale, customSql);
    }

    @Override
    public String toString() {
        return "DataType[kind=" + kind
                + ", length=" + length
                + ", scale=" + scale
                + ", customSql=" + customSql + "]";
    }
}
