package com.github.ceduz19.worstquery.internal.statement;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.ddl.ColumnDefinition;
import com.github.ceduz19.worstquery.ddl.Constraint;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.internal.render.DdlRenderer;
import com.github.ceduz19.worstquery.model.Identifier;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.List;

/**
 * Immutable schema operations. Rendering belongs to DdlRenderer.
 */
public final class DdlStatements {
    private DdlStatements() {
    }

    public record CreateTable(Identifier table, List<ColumnDefinition> columns,
                              List<Constraint> constraints, boolean ifNotExists) implements Query {
        public CreateTable {
            columns = List.copyOf(columns);
            constraints = List.copyOf(constraints);
        }

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record CreateIndex(Identifier index, Identifier table, List<Identifier> columns,
                              boolean unique, boolean ifNotExists) implements Query {
        public CreateIndex {
            columns = List.copyOf(columns);
        }

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record DropTable(Identifier table, boolean ifExists) implements Query {

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record DropIndex(Identifier index, Identifier table, boolean ifExists) implements Query {

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record AddColumn(Identifier table, ColumnDefinition column,
                            boolean tableIfExists, boolean ifNotExists) implements Query {

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record DropColumn(Identifier table, Identifier column,
                             boolean tableIfExists, boolean ifExists) implements Query {

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record AlterColumnType(Identifier table, Identifier column, DataType type,
                                  boolean tableIfExists) implements Query {

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record ModifyColumn(Identifier table, ColumnDefinition column,
                               boolean tableIfExists, boolean ifExists) implements Query {

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record AddConstraint(Identifier table, Constraint constraint,
                                boolean tableIfExists, boolean ifNotExists) implements Query {

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

    public record DropConstraint(Identifier table, Identifier constraint, Constraint.Kind kind,
                                 boolean tableIfExists, boolean ifExists) implements Query {

        @Override
        public void appendTo(SqlWriter writer) {
            DdlRenderer.render(this, writer);
        }
    }

}
