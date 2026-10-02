package com.github.ceduz19.worstquery.internal.dialect;

import com.github.ceduz19.worstquery.ddl.ColumnDefinition;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlWriter;

final class SqliteDialect extends StandardDialect {
    SqliteDialect() {
        super("SQLITE");
    }

    @Override
    public boolean supports(Feature feature) {
        return switch (feature) {
            case ALTER_COLUMN_TYPE, ADD_CONSTRAINT, DROP_CONSTRAINT, INTERSECT_ALL, EXCEPT_ALL -> false;
            case CREATE_TABLE_IF_NOT_EXISTS, CREATE_INDEX_IF_NOT_EXISTS,
                 DROP_TABLE_IF_EXISTS, DROP_INDEX_IF_EXISTS -> true;
            default -> super.supports(feature);
        };
    }

    @Override
    public void pagination(SqlWriter writer, Long limit, Long offset, boolean ordered) {
        appendLimitOffset(writer, limit, offset, "-1");
    }

    @Override
    public String typeName(DataType type) {
        return switch (type.kind()) {
            case BINARY -> "BLOB";
            case TEXT -> "TEXT";
            default -> super.typeName(type);
        };
    }

    @Override
    public void addColumn(SqlWriter writer, Identifier table, ColumnDefinition column) {
        if (column.isPrimaryKey() || column.isUnique()) {
            throw unsupported("adding a PRIMARY KEY or UNIQUE column");
        }
        super.addColumn(writer, table, column);
    }
}
