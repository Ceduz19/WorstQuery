package com.github.ceduz19.worstquery.ddl;

import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.internal.expression.ExpressionNodes;
import com.github.ceduz19.worstquery.internal.render.DdlRenderer;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.SqlPart;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.Objects;

/**
 * Immutable column definition. Fluent methods return new definitions.
 */
public final class ColumnDefinition implements SqlPart {

    private final Identifier name;
    private final DataType type;
    private final boolean nullable;
    private final boolean isPrimaryKey;
    private final boolean isUnique;
    private final Expression defaultValue;

    private ColumnDefinition(
            Identifier name,
            DataType type,
            boolean nullable,
            boolean isPrimaryKey,
            boolean isUnique,
            Expression defaultValue
    ) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        if (name.parts().size() != 1) {
            throw new IllegalArgumentException("Column definition name must be unqualified");
        }
        if (isPrimaryKey && nullable) {
            throw new IllegalArgumentException("Primary key cannot be nullable");
        }

        this.name = name;
        this.type = type;
        this.nullable = nullable;
        this.isPrimaryKey = isPrimaryKey;
        this.isUnique = isUnique;
        this.defaultValue = defaultValue;
    }

    /**
     * Creates a nullable column without defaults or constraints.
     */
    static ColumnDefinition of(String name, DataType type) {
        return new ColumnDefinition(Identifier.ofParts(name), type, true, false, false, null);
    }

    /**
     * Returns a definition with NOT NULL.
     */
    public ColumnDefinition notNull() {
        return new ColumnDefinition(name, type, false, isPrimaryKey, isUnique, defaultValue);
    }

    /**
     * Returns a definition with PRIMARY KEY and NOT NULL.
     */
    public ColumnDefinition primaryKey() {
        return new ColumnDefinition(name, type, false, true, isUnique, defaultValue);
    }

    /**
     * Returns a definition with an inline UNIQUE constraint.
     */
    public ColumnDefinition unique() {
        return new ColumnDefinition(name, type, nullable, isPrimaryKey, true, defaultValue);
    }

    /**
     * Returns a definition with a default; values render as escaped DDL literals.
     */
    public ColumnDefinition defaultValue(Object value) {
        return new ColumnDefinition(name, type, nullable, isPrimaryKey, isUnique, ExpressionNodes.operand(value));
    }

    @Override
    public void appendTo(SqlWriter writer) {
        DdlRenderer.render(this, writer);
    }

    public Identifier name() {
        return name;
    }

    public DataType type() {
        return type;
    }

    public boolean nullable() {
        return nullable;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public boolean isUnique() {
        return isUnique;
    }

    public Expression defaultValue() {
        return defaultValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ColumnDefinition value)) {
            return false;
        }
        return Objects.equals(name, value.name)
                && Objects.equals(type, value.type)
                && Objects.equals(nullable, value.nullable)
                && Objects.equals(isPrimaryKey, value.isPrimaryKey)
                && Objects.equals(isUnique, value.isUnique)
                && Objects.equals(defaultValue, value.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, nullable, isPrimaryKey, isUnique, defaultValue);
    }

    @Override
    public String toString() {
        return "ColumnDefinition[name=" + name
                + ", type=" + type
                + ", nullable=" + nullable
                + ", isPrimaryKey=" + isPrimaryKey
                + ", isUnique=" + isUnique
                + ", defaultValue=" + defaultValue + "]";
    }
}
