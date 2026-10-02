package com.github.ceduz19.worstquery.spi;

import com.github.ceduz19.worstquery.api.UnsupportedSqlFeatureException;
import com.github.ceduz19.worstquery.ddl.ColumnDefinition;
import com.github.ceduz19.worstquery.ddl.Constraint;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.model.SetOperator;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Extension point for SQL rendering. Defaults describe the ANSI implementation.
 * Overrides must reject unsupported features instead of silently changing semantics.
 */
public interface SqlDialect {

    String name();

    /**
     * Quotes one identifier component, escaping its closing delimiter.
     */
    default String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid identifier");

        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * Reports a renderer capability; override unsupported ANSI defaults in custom dialects.
     */
    default boolean supports(Feature feature) {
        return switch (feature) {
            case CREATE_TABLE_IF_NOT_EXISTS, CREATE_INDEX_IF_NOT_EXISTS,
                 DROP_TABLE_IF_EXISTS, DROP_INDEX_IF_EXISTS, ALTER_TABLE_IF_EXISTS,
                 ADD_COLUMN_IF_NOT_EXISTS, DROP_COLUMN_IF_EXISTS, MODIFY_COLUMN_IF_EXISTS,
                 ADD_CONSTRAINT_IF_NOT_EXISTS, DROP_CONSTRAINT_IF_EXISTS -> false;
            default -> true;
        };
    }

    /**
     * Throws an UnsupportedSqlFeatureException when a capability is unavailable.
     */
    default void require(Feature feature) {
        if (!supports(feature)) throw unsupported(feature.name());
    }

    /**
     * Creates an exception naming this dialect and the unsupported construct.
     */
    default UnsupportedSqlFeatureException unsupported(String feature) {
        return new UnsupportedSqlFeatureException(name() + " does not support " + feature);
    }

    /**
     * Returns the syntax between a table reference and its alias.
     */
    default String tableAliasSeparator() {
        return " AS ";
    }

    /**
     * Returns the optional keyword after WITH for recursive CTEs.
     */
    default String recursiveKeyword() {
        return "RECURSIVE ";
    }

    /**
     * Whether recursive CTE definitions require an explicit output column list.
     */
    default boolean recursiveColumnsRequired() {
        return false;
    }

    /**
     * Returns a suffix for SELECT without FROM, such as Oracle's FROM DUAL.
     */
    default String selectWithoutFromSuffix() {
        return "";
    }

    /**
     * Whether multirow INSERT should use Oracle-style INSERT ALL.
     */
    default boolean usesInsertAll() {
        return false;
    }

    /**
     * Renders a set operator after checking support for its ALL variant.
     */
    default String setOperator(SetOperator operator) {
        if (operator == SetOperator.INTERSECT_ALL) require(Feature.INTERSECT_ALL);
        if (operator == SetOperator.EXCEPT_ALL) require(Feature.EXCEPT_ALL);

        return operator.sql();
    }

    /**
     * Pagination numbers are validated SQL integers, not interpolated application text.
     */
    default void pagination(SqlWriter c, Long limit, Long offset, boolean ordered) {
        if (offset != null) c.sql(" OFFSET " + offset + " ROWS");

        if (limit != null) {
            if (limit == 0) throw unsupported("FETCH with zero rows");
            c.sql(" FETCH FIRST " + limit + " ROWS ONLY");
        }
    }

    /**
     * Maps a logical type to dialect-specific type syntax.
     */
    default String typeName(DataType type) {
        return switch (type.kind()) {
            case CUSTOM -> type.customSql();
            case VARCHAR -> "VARCHAR(" + type.length() + ")";
            case BINARY -> "VARBINARY(" + type.length() + ")";
            case DECIMAL -> "DECIMAL(" + type.length() + ", " + type.scale() + ")";
            case DOUBLE -> "DOUBLE PRECISION";
            case TEXT -> "CLOB";
            default -> type.kind().name();
        };
    }

    /**
     * Encodes only known literal types. Arbitrary object.toString() is never trusted.
     */
    default String literal(Object value) {
        if (value == null)
            return "NULL";

        if (value instanceof String || value instanceof Character)
            return stringLiteral(value.toString());

        if (value instanceof Boolean b)
            return b ? "TRUE" : "FALSE";

        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger || value instanceof BigDecimal)
            return value.toString();

        if (value instanceof Double d && Double.isFinite(d))
            return d.toString();

        if (value instanceof Float f && Float.isFinite(f))
            return f.toString();

        throw new IllegalArgumentException("Unsupported SQL literal type: " + value.getClass().getName());
    }

    /**
     * Escapes a string for inlined rendering and DDL literal contexts.
     */
    default String stringLiteral(String text) {
        if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL in SQL string literal");
        return "'" + text.replace("'", "''") + "'";
    }

    /**
     * Renders one ADD COLUMN operation, including dialect restrictions.
     */
    default void addColumn(SqlWriter c, Identifier table, ColumnDefinition column) {
        c.sql("ALTER TABLE ").part(table).sql(" ADD COLUMN ").literalPart(column);
    }

    /**
     * Renders conditional column addition while preserving the existing SPI method.
     */
    default void addColumn(SqlWriter c, Identifier table, ColumnDefinition column,
                           boolean tableIfExists, boolean ifNotExists) {
        if (!tableIfExists && !ifNotExists) {
            addColumn(c, table, column);
            return;
        }
        if (ifNotExists) {
            require(Feature.ADD_COLUMN_IF_NOT_EXISTS);
        }

        alterTable(c, table, tableIfExists).sql(" ADD COLUMN ");
        if (ifNotExists) {
            c.sql("IF NOT EXISTS ");
        }
        c.literalPart(column);
    }

    /**
     * Writes the common ALTER TABLE prefix and checks its optional existence clause.
     */
    default SqlWriter alterTable(SqlWriter c, Identifier table, boolean ifExists) {
        if (ifExists) {
            require(Feature.ALTER_TABLE_IF_EXISTS);
        }
        c.sql("ALTER TABLE ");
        if (ifExists) {
            c.sql("IF EXISTS ");
        }
        return c.part(table);
    }

    /**
     * Changes only the type; nullability and defaults are separate schema concerns.
     */
    default void alterColumnType(SqlWriter c, Identifier table, Identifier column, DataType type) {
        require(Feature.ALTER_COLUMN_TYPE);
        c.sql("ALTER TABLE ").part(table).sql(" ALTER COLUMN ").part(column).sql(" SET DATA TYPE ").part(type);
    }

    /**
     * Conditional table alteration requires a dialect-specific type-change form.
     */
    default void alterColumnType(SqlWriter c, Identifier table, Identifier column, DataType type,
                                 boolean tableIfExists) {
        if (!tableIfExists) {
            alterColumnType(c, table, column, type);
            return;
        }
        require(Feature.ALTER_TABLE_IF_EXISTS);
        throw unsupported("conditional column type alteration");
    }

    /**
     * Renders DROP INDEX; the optional table is required by some dialects.
     */
    default void dropIndex(SqlWriter c, Identifier index, Identifier table) {
        c.sql("DROP INDEX ").part(index);
    }

    /**
     * Renders conditional index removal, with overrides for dialects requiring ON table.
     */
    default void dropIndex(SqlWriter c, Identifier index, Identifier table, boolean ifExists) {
        if (!ifExists) {
            dropIndex(c, index, table);
            return;
        }
        require(Feature.DROP_INDEX_IF_EXISTS);
        c.sql("DROP INDEX IF EXISTS ").part(index);
    }

    /**
     * Renders a complete column redefinition when supported.
     */
    default void modifyColumn(SqlWriter c, Identifier table, ColumnDefinition column) {
        throw unsupported("full column redefinition; use alterColumnType or explicit SQL");
    }

    /**
     * Conditional column redefinition is dialect-specific.
     */
    default void modifyColumn(SqlWriter c, Identifier table, ColumnDefinition column,
                              boolean tableIfExists, boolean ifExists) {
        if (!tableIfExists && !ifExists) {
            modifyColumn(c, table, column);
            return;
        }
        if (ifExists) {
            require(Feature.MODIFY_COLUMN_IF_EXISTS);
        }
        if (tableIfExists) {
            require(Feature.ALTER_TABLE_IF_EXISTS);
        }
        throw unsupported("conditional column redefinition");
    }

    /**
     * Renders a table constraint; conditional addition needs dialect-specific syntax.
     */
    default void addConstraint(SqlWriter c, Identifier table, Constraint constraint,
                               boolean tableIfExists, boolean ifNotExists) {
        require(Feature.ADD_CONSTRAINT);
        if (ifNotExists) {
            require(Feature.ADD_CONSTRAINT_IF_NOT_EXISTS);
            throw unsupported("conditional constraint addition for " + constraint.kind());
        }

        alterTable(c, table, tableIfExists).sql(" ADD ").literalPart(constraint);
    }

    /**
     * Renders a constraint removal according to its kind.
     */
    default void dropConstraint(SqlWriter c, Identifier table, Identifier constraint, Constraint.Kind kind) {
        require(Feature.DROP_CONSTRAINT);
        c.sql("ALTER TABLE ").part(table).sql(" DROP CONSTRAINT ").part(constraint);
    }

    /**
     * Renders conditional constraint removal using the standard clause position.
     */
    default void dropConstraint(SqlWriter c, Identifier table, Identifier constraint, Constraint.Kind kind,
                                boolean tableIfExists, boolean ifExists) {
        if (!tableIfExists && !ifExists) {
            dropConstraint(c, table, constraint, kind);
            return;
        }
        require(Feature.DROP_CONSTRAINT);
        if (ifExists) {
            require(Feature.DROP_CONSTRAINT_IF_EXISTS);
        }

        alterTable(c, table, tableIfExists).sql(" DROP CONSTRAINT ");
        if (ifExists) {
            c.sql("IF EXISTS ");
        }
        c.part(constraint);
    }
}
