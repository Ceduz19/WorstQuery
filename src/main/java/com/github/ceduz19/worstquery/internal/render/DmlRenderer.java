package com.github.ceduz19.worstquery.internal.render;

import com.github.ceduz19.worstquery.expression.Expression;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements.Cte;
import com.github.ceduz19.worstquery.internal.statement.DmlStatements.Join;
import com.github.ceduz19.worstquery.model.JoinType;
import com.github.ceduz19.worstquery.spi.Feature;
import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.List;

/**
 * SQL emission for immutable DML snapshots. Builders never write SQL.
 */
public final class DmlRenderer {
    private DmlRenderer() {
    }

    public static void render(DmlStatements.Select statement, SqlWriter writer) {
        validateNesting(statement, writer);
        appendCtes(statement.ctes(), writer);

        writer.sql(statement.distinct() ? "SELECT DISTINCT " : "SELECT ")
                .separated(statement.columns(), ", ");
        if (statement.from() != null) {
            writer.sql(" FROM ").part(statement.from());
        } else {
            writer.sql(writer.dialect().selectWithoutFromSuffix());
        }

        for (Join join : statement.joins()) {
            appendJoin(join, writer);
        }
        if (statement.where() != null) {
            writer.sql(" WHERE ").part(statement.where());
        }
        if (!statement.groups().isEmpty()) {
            writer.sql(" GROUP BY ").separated(statement.groups(), ", ");
        }
        if (statement.having() != null) {
            writer.sql(" HAVING ").part(statement.having());
        }
        if (!statement.ordering().isEmpty()) {
            writer.sql(" ORDER BY ").separated(statement.ordering(), ", ");
        }

        writer.dialect().pagination(writer, statement.limit(), statement.offset(), !statement.ordering().isEmpty());
    }

    private static void validateNesting(DmlStatements.Select statement, SqlWriter writer) {
        if (!writer.inSubquery()) {
            return;
        }

        if (!statement.ctes().isEmpty()) {
            writer.dialect().require(Feature.NESTED_WITH);
        }

        if (!statement.ordering().isEmpty() && statement.limit() == null && statement.offset() == null) {
            writer.dialect().require(Feature.ORDER_BY_IN_SUBQUERY);
        }
    }

    private static void appendCtes(List<Cte> ctes, SqlWriter writer) {
        if (ctes.isEmpty()) {
            return;
        }

        writer.sql("WITH ");
        if (ctes.stream().anyMatch(Cte::recursive)) {
            writer.sql(writer.dialect().recursiveKeyword());
        }
        for (int index = 0; index < ctes.size(); index++) {
            Cte cte = ctes.get(index);
            if (cte.recursive() && writer.dialect().recursiveColumnsRequired() && cte.columns().isEmpty()) {
                throw new IllegalArgumentException(writer.dialect().name() + " recursive CTE requires column aliases");
            }
            if (index > 0) {
                writer.sql(", ");
            }
            writer.part(cte.name());
            if (!cte.columns().isEmpty()) {
                writer.sql(" (").separated(cte.columns(), ", ").sql(")");
            }
            writer.sql(" AS (").subquery(cte.query()).sql(")");
        }
        writer.sql(" ");
    }

    private static void appendJoin(Join join, SqlWriter writer) {
        if (join.type() == JoinType.RIGHT) {
            writer.dialect().require(Feature.RIGHT_JOIN);
        }
        if (join.type() == JoinType.FULL) {
            writer.dialect().require(Feature.FULL_JOIN);
        }

        writer.sql(" " + join.type().name() + " JOIN ").part(join.table());
        if (join.on() != null) {
            writer.sql(" ON ").part(join.on());
        }
    }

    public static void render(DmlStatements.Insert statement, SqlWriter writer) {
        if (writer.dialect().usesInsertAll() && statement.rows().size() > 1) {
            appendInsertAll(statement, writer);
            return;
        }

        writer.sql("INSERT INTO ").part(statement.table())
                .sql(" (").separated(statement.columns(), ", ").sql(") ");
        if (statement.source() != null) {
            writer.subquery(statement.source());
            return;
        }

        writer.sql("VALUES ");
        for (int index = 0; index < statement.rows().size(); index++) {
            if (index > 0) {
                writer.sql(", ");
            }
            writer.sql("(").separated(statement.rows().get(index), ", ").sql(")");
        }
    }

    private static void appendInsertAll(DmlStatements.Insert statement, SqlWriter writer) {
        writer.sql("INSERT ALL");
        for (List<Expression> row : statement.rows()) {
            writer.sql(" INTO ").part(statement.table())
                    .sql(" (").separated(statement.columns(), ", ")
                    .sql(") VALUES (").separated(row, ", ").sql(")");
        }
        writer.sql(" SELECT 1 FROM DUAL");
    }

    public static void render(DmlStatements.Update statement, SqlWriter writer) {
        writer.sql("UPDATE ").part(statement.table()).sql(" SET ");
        for (int index = 0; index < statement.assignments().size(); index++) {
            if (index > 0) {
                writer.sql(", ");
            }
            var assignment = statement.assignments().get(index);
            writer.part(assignment.column()).sql(" = ").part(assignment.value());
        }
        if (statement.where() != null) {
            writer.sql(" WHERE ").part(statement.where());
        }
    }

    public static void render(DmlStatements.Delete statement, SqlWriter writer) {
        writer.sql("DELETE FROM ").part(statement.table());
        if (statement.where() != null) {
            writer.sql(" WHERE ").part(statement.where());
        }
    }
}
