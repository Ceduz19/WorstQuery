package com.github.ceduz19.worstquery.internal.dialect;

import com.github.ceduz19.worstquery.ddl.ColumnDefinition;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.model.SetOperator;
import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlWriter;

final class OracleDialect extends StandardDialect {
    OracleDialect() {
        super("ORACLE");
    }

    @Override
    public boolean supports(Feature feature) {
        return switch (feature) {
            case INTERSECT_ALL, EXCEPT_ALL, WINDOW_GROUPS, NESTED_WITH -> false;
            default -> super.supports(feature);
        };
    }

    @Override
    public String tableAliasSeparator() {
        return " ";
    }

    @Override
    public String recursiveKeyword() {
        return "";
    }

    @Override
    public boolean recursiveColumnsRequired() {
        return true;
    }

    @Override
    public String selectWithoutFromSuffix() {
        return " FROM DUAL";
    }

    @Override
    public boolean usesInsertAll() {
        return true;
    }

    @Override
    public String setOperator(SetOperator operator) {
        String standard = super.setOperator(operator);
        return operator == SetOperator.EXCEPT ? "MINUS" : standard;
    }

    @Override
    public String typeName(DataType type) {
        return switch (type.kind()) {
            case BOOLEAN -> "NUMBER(1)";
            case SMALLINT -> "NUMBER(5)";
            case INTEGER -> "NUMBER(10)";
            case BIGINT -> "NUMBER(19)";
            case VARCHAR -> "VARCHAR2(" + type.length() + ")";
            case BINARY -> "RAW(" + type.length() + ")";
            case DOUBLE -> "BINARY_DOUBLE";
            case REAL -> "BINARY_FLOAT";
            case TIME -> throw unsupported("a standalone TIME data type");
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
    public void addColumn(SqlWriter writer, Identifier table, ColumnDefinition column) {
        writer.sql("ALTER TABLE ").part(table).sql(" ADD ").literalPart(column);
    }

    @Override
    public void alterColumnType(SqlWriter writer, Identifier table, Identifier column, DataType type) {
        writer.sql("ALTER TABLE ").part(table).sql(" MODIFY (").part(column).sql(" ").part(type).sql(")");
    }
}
