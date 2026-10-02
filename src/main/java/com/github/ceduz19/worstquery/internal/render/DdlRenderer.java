package com.github.ceduz19.worstquery.internal.render;

import com.github.ceduz19.worstquery.ddl.ColumnDefinition;
import com.github.ceduz19.worstquery.ddl.Constraint;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.internal.statement.DdlStatements;
import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlWriter;

/**
 * Schema SQL emission, including literal-only defaults and CHECK expressions.
 */
public final class DdlRenderer {
    private DdlRenderer() {
    }

    public static void render(DdlStatements.CreateTable statement, SqlWriter writer) {
        writer.sql("CREATE TABLE ");
        if (statement.ifNotExists()) {
            writer.dialect().require(Feature.CREATE_TABLE_IF_NOT_EXISTS);
            writer.sql("IF NOT EXISTS ");
        }
        writer.part(statement.table()).sql(" (");
        for (int index = 0; index < statement.columns().size(); index++) {
            if (index > 0) {
                writer.sql(", ");
            }
            writer.literalPart(statement.columns().get(index));
        }
        for (Constraint constraint : statement.constraints()) {
            writer.sql(", ").literalPart(constraint);
        }
        writer.sql(")");
    }

    public static void render(DdlStatements.CreateIndex statement, SqlWriter writer) {
        writer.sql(statement.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ");
        if (statement.ifNotExists()) {
            writer.dialect().require(Feature.CREATE_INDEX_IF_NOT_EXISTS);
            writer.sql("IF NOT EXISTS ");
        }
        writer.part(statement.index()).sql(" ON ").part(statement.table())
                .sql(" (").separated(statement.columns(), ", ").sql(")");
    }

    public static void render(DdlStatements.DropTable statement, SqlWriter writer) {
        writer.sql("DROP TABLE ");
        if (statement.ifExists()) {
            writer.dialect().require(Feature.DROP_TABLE_IF_EXISTS);
            writer.sql("IF EXISTS ");
        }
        writer.part(statement.table());
    }

    public static void render(DdlStatements.DropIndex statement, SqlWriter writer) {
        writer.dialect().dropIndex(writer, statement.index(), statement.table(), statement.ifExists());
    }

    public static void render(DdlStatements.AddColumn statement, SqlWriter writer) {
        writer.dialect().addColumn(writer, statement.table(), statement.column(),
                statement.tableIfExists(), statement.ifNotExists());
    }

    public static void render(DdlStatements.DropColumn statement, SqlWriter writer) {
        if (statement.ifExists()) {
            writer.dialect().require(Feature.DROP_COLUMN_IF_EXISTS);
        }
        writer.dialect().alterTable(writer, statement.table(), statement.tableIfExists()).sql(" DROP COLUMN ");
        if (statement.ifExists()) {
            writer.sql("IF EXISTS ");
        }
        writer.part(statement.column());
    }

    public static void render(DdlStatements.AlterColumnType statement, SqlWriter writer) {
        writer.dialect().alterColumnType(writer, statement.table(), statement.column(), statement.type(),
                statement.tableIfExists());
    }

    public static void render(DdlStatements.ModifyColumn statement, SqlWriter writer) {
        writer.dialect().modifyColumn(writer, statement.table(), statement.column(),
                statement.tableIfExists(), statement.ifExists());
    }

    public static void render(DdlStatements.AddConstraint statement, SqlWriter writer) {
        writer.dialect().addConstraint(writer, statement.table(), statement.constraint(),
                statement.tableIfExists(), statement.ifNotExists());
    }

    public static void render(DdlStatements.DropConstraint statement, SqlWriter writer) {
        writer.dialect().dropConstraint(writer, statement.table(), statement.constraint(), statement.kind(),
                statement.tableIfExists(), statement.ifExists());
    }

    public static void render(ColumnDefinition column, SqlWriter writer) {
        writer.part(column.name()).sql(" ").part(column.type());
        if (column.defaultValue() != null) {
            writer.sql(" DEFAULT (").literalPart(column.defaultValue()).sql(")");
        }
        if (!column.nullable()) {
            writer.sql(" NOT NULL");
        }
        if (column.isPrimaryKey()) {
            writer.sql(" PRIMARY KEY");
        }
        if (column.isUnique()) {
            writer.sql(" UNIQUE");
        }
    }

    public static void render(Constraint constraint, SqlWriter writer) {
        writer.sql("CONSTRAINT ").part(constraint.name())
                .sql(" " + constraint.kind().name().replace('_', ' ') + " (");
        if (constraint.kind() == Constraint.Kind.CHECK) {
            writer.literalPart(constraint.check());
        } else {
            writer.separated(constraint.columns(), ", ");
        }
        writer.sql(")");

        if (constraint.kind() == Constraint.Kind.FOREIGN_KEY) {
            writer.sql(" REFERENCES ").part(constraint.referencedTable())
                    .sql(" (").separated(constraint.referencedColumns(), ", ").sql(")");
        }
    }

    public static void render(DataType type, SqlWriter writer) {
        writer.sql(writer.dialect().typeName(type));
    }
}
