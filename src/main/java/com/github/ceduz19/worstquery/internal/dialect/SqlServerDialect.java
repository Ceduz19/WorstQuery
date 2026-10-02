package com.github.ceduz19.worstquery.internal.dialect;

import com.github.ceduz19.worstquery.ddl.ColumnDefinition;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlWriter;

final class SqlServerDialect extends StandardDialect {
    SqlServerDialect() {
        super("SQL_SERVER");
    }

    @Override
    public String quoteIdentifier(String identifier) {
        super.quoteIdentifier(identifier);
        return "[" + identifier.replace("]", "]]") + "]";
    }

    @Override
    public boolean supports(Feature feature) {
        return switch (feature) {
            case INTERSECT_ALL, EXCEPT_ALL, ALTER_COLUMN_TYPE, WINDOW_RANGE_OFFSET,
                 WINDOW_GROUPS, NESTED_WITH, ORDER_BY_IN_SUBQUERY -> false;
            case DROP_TABLE_IF_EXISTS, DROP_INDEX_IF_EXISTS,
                 DROP_COLUMN_IF_EXISTS, DROP_CONSTRAINT_IF_EXISTS -> true;
            default -> super.supports(feature);
        };
    }

    @Override
    public String recursiveKeyword() {
        return "";
    }

    @Override
    public void pagination(SqlWriter writer, Long limit, Long offset, boolean ordered) {
        if (limit == null && offset == null) {
            return;
        }
        if (!ordered) {
            throw unsupported("pagination without ORDER BY");
        }
        if (limit != null && limit == 0) {
            throw unsupported("FETCH with zero rows");
        }

        writer.sql(" OFFSET " + (offset == null ? 0 : offset) + " ROWS");
        if (limit != null) {
            writer.sql(" FETCH NEXT " + limit + " ROWS ONLY");
        }
    }

    @Override
    public String typeName(DataType type) {
        return switch (type.kind()) {
            case BOOLEAN -> "BIT";
            case TIMESTAMP -> "DATETIME2";
            case TEXT -> "VARCHAR(MAX)";
            case DOUBLE -> "FLOAT(53)";
            default -> super.typeName(type);
        };
    }

    @Override
    public String literal(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? "1" : "0";
        }
        return super.literal(value);
    }

    @Override
    public String stringLiteral(String text) {
        return "N" + super.stringLiteral(text);
    }

    @Override
    public void addColumn(SqlWriter writer, Identifier table, ColumnDefinition column) {
        writer.sql("ALTER TABLE ").part(table).sql(" ADD ").literalPart(column);
    }

    @Override
    public void dropIndex(SqlWriter writer, Identifier index, Identifier table) {
        if (table == null) {
            throw new IllegalArgumentException(name() + " DROP INDEX requires a table");
        }
        writer.sql("DROP INDEX ").part(index).sql(" ON ").part(table);
    }

    @Override
    public void dropIndex(SqlWriter writer, Identifier index, Identifier table, boolean ifExists) {
        if (!ifExists) {
            dropIndex(writer, index, table);
            return;
        }
        require(Feature.DROP_INDEX_IF_EXISTS);
        if (table == null) {
            throw new IllegalArgumentException(name() + " DROP INDEX requires a table");
        }
        writer.sql("DROP INDEX IF EXISTS ").part(index).sql(" ON ").part(table);
    }

    @Override
    public void modifyColumn(SqlWriter writer, Identifier table, ColumnDefinition column) {
        if (column.isPrimaryKey() || column.isUnique() || column.defaultValue() != null) {
            throw unsupported("constraints/defaults inside ALTER COLUMN");
        }
        writer.sql("ALTER TABLE ").part(table).sql(" ALTER COLUMN ").literalPart(column);
        if (column.nullable()) {
            writer.sql(" NULL");
        }
    }
}
