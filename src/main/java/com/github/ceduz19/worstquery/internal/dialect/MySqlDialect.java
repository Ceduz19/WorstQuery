package com.github.ceduz19.worstquery.internal.dialect;

import com.github.ceduz19.worstquery.ddl.ColumnDefinition;
import com.github.ceduz19.worstquery.ddl.Constraint;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

class MySqlDialect extends StandardDialect {
    MySqlDialect(String name) {
        super(name);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        super.quoteIdentifier(identifier);
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public boolean supports(Feature feature) {
        return switch (feature) {
            case FULL_JOIN, ALTER_COLUMN_TYPE, WINDOW_GROUPS -> false;
            case CREATE_TABLE_IF_NOT_EXISTS, DROP_TABLE_IF_EXISTS -> true;
            default -> super.supports(feature);
        };
    }

    @Override
    public void pagination(SqlWriter writer, Long limit, Long offset, boolean ordered) {
        appendLimitOffset(writer, limit, offset, "18446744073709551615");
    }

    @Override
    public String typeName(DataType type) {
        return type.kind() == DataType.Kind.TEXT ? "TEXT" : super.typeName(type);
    }

    @Override
    public String stringLiteral(String text) {
        super.stringLiteral(text);
        // Hex is independent of the connection's NO_BACKSLASH_ESCAPES setting.
        String hex = HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
        return "CONVERT(X'" + hex + "' USING utf8mb4)";
    }

    @Override
    public void dropIndex(SqlWriter writer, Identifier index, Identifier table) {
        if (table == null) {
            throw new IllegalArgumentException(name() + " DROP INDEX requires a table");
        }
        writer.sql("DROP INDEX ").part(index).sql(" ON ").part(table);
    }

    @Override
    public void modifyColumn(SqlWriter writer, Identifier table, ColumnDefinition column) {
        if (column.isPrimaryKey() || column.isUnique()) {
            throw unsupported("keys inside MODIFY COLUMN; use constraints");
        }

        writer.sql("ALTER TABLE ").part(table).sql(" MODIFY COLUMN ").literalPart(column);
        if (column.nullable()) {
            writer.sql(" NULL");
        }
    }

    @Override
    public void dropConstraint(SqlWriter writer, Identifier table, Identifier constraint, Constraint.Kind kind) {
        writer.sql("ALTER TABLE ").part(table);
        switch (kind) {
            case PRIMARY_KEY -> writer.sql(" DROP PRIMARY KEY");
            case UNIQUE -> writer.sql(" DROP INDEX ").part(constraint);
            case FOREIGN_KEY -> writer.sql(" DROP FOREIGN KEY ").part(constraint);
            case CHECK -> writer.sql(dropCheckClause()).part(constraint);
        }
    }

    String dropCheckClause() {
        return " DROP CHECK ";
    }
}
