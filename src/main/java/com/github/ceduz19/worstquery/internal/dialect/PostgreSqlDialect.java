package com.github.ceduz19.worstquery.internal.dialect;

import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlWriter;

final class PostgreSqlDialect extends StandardDialect {
    PostgreSqlDialect() {
        super("POSTGRESQL");
    }

    @Override
    public boolean supports(Feature feature) {
        return switch (feature) {
            case CREATE_TABLE_IF_NOT_EXISTS, CREATE_INDEX_IF_NOT_EXISTS,
                 DROP_TABLE_IF_EXISTS, DROP_INDEX_IF_EXISTS, ALTER_TABLE_IF_EXISTS,
                 ADD_COLUMN_IF_NOT_EXISTS, DROP_COLUMN_IF_EXISTS, DROP_CONSTRAINT_IF_EXISTS -> true;
            default -> super.supports(feature);
        };
    }

    @Override
    public void pagination(SqlWriter writer, Long limit, Long offset, boolean ordered) {
        appendLimitOffset(writer, limit, offset, null);
    }

    @Override
    public String typeName(DataType type) {
        return switch (type.kind()) {
            case BINARY -> "BYTEA";
            case TEXT -> "TEXT";
            default -> super.typeName(type);
        };
    }

    @Override
    public String stringLiteral(String text) {
        String standard = super.stringLiteral(text);
        if (text.indexOf('\\') >= 0) {
            return "E" + standard.replace("\\", "\\\\");
        }
        return standard;
    }

    @Override
    public void alterColumnType(SqlWriter writer, Identifier table, Identifier column, DataType type) {
        writer.sql("ALTER TABLE ").part(table).sql(" ALTER COLUMN ").part(column).sql(" TYPE ").part(type);
    }

    @Override
    public void alterColumnType(SqlWriter writer, Identifier table, Identifier column, DataType type,
                                boolean tableIfExists) {
        if (!tableIfExists) {
            alterColumnType(writer, table, column, type);
            return;
        }
        alterTable(writer, table, true).sql(" ALTER COLUMN ").part(column).sql(" TYPE ").part(type);
    }
}
