package com.github.ceduz19.worstquery.internal.dialect;

import com.github.ceduz19.worstquery.ddl.ColumnDefinition;
import com.github.ceduz19.worstquery.ddl.Constraint;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlWriter;

final class MariaDbDialect extends MySqlDialect {
    MariaDbDialect() {
        super("MARIADB");
    }

    @Override
    public boolean supports(Feature feature) {
        return switch (feature) {
            case CREATE_INDEX_IF_NOT_EXISTS, DROP_INDEX_IF_EXISTS, ALTER_TABLE_IF_EXISTS,
                 ADD_COLUMN_IF_NOT_EXISTS, DROP_COLUMN_IF_EXISTS, MODIFY_COLUMN_IF_EXISTS,
                 ADD_CONSTRAINT_IF_NOT_EXISTS, DROP_CONSTRAINT_IF_EXISTS -> true;
            default -> super.supports(feature);
        };
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
    public void modifyColumn(SqlWriter writer, Identifier table, ColumnDefinition column,
                             boolean tableIfExists, boolean ifExists) {
        if (!tableIfExists && !ifExists) {
            modifyColumn(writer, table, column);
            return;
        }
        if (ifExists) {
            require(Feature.MODIFY_COLUMN_IF_EXISTS);
        }
        if (column.isPrimaryKey() || column.isUnique()) {
            throw unsupported("keys inside MODIFY COLUMN; use constraints");
        }

        alterTable(writer, table, tableIfExists).sql(" MODIFY COLUMN ");
        if (ifExists) {
            writer.sql("IF EXISTS ");
        }
        writer.literalPart(column);
        if (column.nullable()) {
            writer.sql(" NULL");
        }
    }

    @Override
    public void addConstraint(SqlWriter writer, Identifier table, Constraint constraint,
                              boolean tableIfExists, boolean ifNotExists) {
        if (!ifNotExists) {
            super.addConstraint(writer, table, constraint, tableIfExists, false);
            return;
        }
        require(Feature.ADD_CONSTRAINT_IF_NOT_EXISTS);
        if (constraint.kind() == Constraint.Kind.CHECK) {
            throw unsupported("ADD CHECK IF NOT EXISTS");
        }

        alterTable(writer, table, tableIfExists).sql(" ADD ");
        if (constraint.kind() == Constraint.Kind.UNIQUE) {
            writer.sql("UNIQUE INDEX IF NOT EXISTS ").part(constraint.name());
        } else {
            writer.sql("CONSTRAINT ").part(constraint.name());
            writer.sql(constraint.kind() == Constraint.Kind.PRIMARY_KEY
                    ? " PRIMARY KEY IF NOT EXISTS" : " FOREIGN KEY IF NOT EXISTS");
        }
        writer.sql(" (").separated(constraint.columns(), ", ").sql(")");
        if (constraint.kind() == Constraint.Kind.FOREIGN_KEY) {
            writer.sql(" REFERENCES ").part(constraint.referencedTable())
                    .sql(" (").separated(constraint.referencedColumns(), ", ").sql(")");
        }
    }

    @Override
    public void dropConstraint(SqlWriter writer, Identifier table, Identifier constraint, Constraint.Kind kind,
                               boolean tableIfExists, boolean ifExists) {
        if (!tableIfExists && !ifExists) {
            dropConstraint(writer, table, constraint, kind);
            return;
        }
        if (ifExists) {
            require(Feature.DROP_CONSTRAINT_IF_EXISTS);
        }
        if (ifExists && kind == Constraint.Kind.PRIMARY_KEY) {
            throw unsupported("DROP PRIMARY KEY IF EXISTS");
        }

        alterTable(writer, table, tableIfExists);
        switch (kind) {
            case PRIMARY_KEY -> writer.sql(" DROP PRIMARY KEY");
            case UNIQUE -> writer.sql(" DROP INDEX ");
            case FOREIGN_KEY -> writer.sql(" DROP FOREIGN KEY ");
            case CHECK -> writer.sql(" DROP CONSTRAINT ");
        }
        if (kind != Constraint.Kind.PRIMARY_KEY) {
            if (ifExists) {
                writer.sql("IF EXISTS ");
            }
            writer.part(constraint);
        }
    }

    @Override
    String dropCheckClause() {
        return " DROP CONSTRAINT ";
    }
}
