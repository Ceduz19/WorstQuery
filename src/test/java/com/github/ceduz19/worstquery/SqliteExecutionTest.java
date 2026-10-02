package com.github.ceduz19.worstquery;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.ddl.Constraints;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.expression.FrameBound;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.ceduz19.worstquery.ddl.Schema.alterTable;
import static com.github.ceduz19.worstquery.ddl.Schema.column;
import static com.github.ceduz19.worstquery.ddl.Schema.createIndex;
import static com.github.ceduz19.worstquery.ddl.Schema.createTable;
import static com.github.ceduz19.worstquery.ddl.Schema.dropIndex;
import static com.github.ceduz19.worstquery.ddl.Schema.dropIndexIfExists;
import static com.github.ceduz19.worstquery.ddl.Schema.dropTable;
import static com.github.ceduz19.worstquery.ddl.Schema.dropTableIfExists;
import static com.github.ceduz19.worstquery.dialect.Dialects.SQLITE;
import static com.github.ceduz19.worstquery.dml.Queries.deleteFrom;
import static com.github.ceduz19.worstquery.dml.Queries.insertInto;
import static com.github.ceduz19.worstquery.dml.Queries.select;
import static com.github.ceduz19.worstquery.dml.Queries.update;
import static com.github.ceduz19.worstquery.expression.Expressions.caseWhen;
import static com.github.ceduz19.worstquery.expression.Expressions.column;
import static com.github.ceduz19.worstquery.expression.Expressions.param;
import static com.github.ceduz19.worstquery.expression.Expressions.star;
import static com.github.ceduz19.worstquery.expression.Expressions.table;
import static com.github.ceduz19.worstquery.expression.Expressions.window;
import static com.github.ceduz19.worstquery.expression.Functions.sum;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Execution checks are test-only; the library itself has no JDBC dependency or executor.
 */
class SqliteExecutionTest {
    private Connection connection;

    @BeforeEach
    void connect() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @AfterEach
    void close() throws SQLException {
        connection.close();
    }

    private void execute(Query query) throws SQLException {
        try (PreparedStatement statement = prepare(query)) {
            statement.execute();
        }
    }

    private PreparedStatement prepare(Query query) throws SQLException {
        var rendered = query.render(SQLITE);
        PreparedStatement statement = connection.prepareStatement(rendered.sql());
        try {
            for (int i = 0; i < rendered.parameters().size(); i++) {
                statement.setObject(i + 1, rendered.parameters().get(i));
            }
            return statement;
        } catch (SQLException error) {
            statement.close();
            throw error;
        }
    }

    private List<Object> values(Query query) throws SQLException {
        try (PreparedStatement statement = prepare(query); ResultSet rows = statement.executeQuery()) {
            List<Object> result = new ArrayList<>();
            while (rows.next()) {
                result.add(rows.getObject(1));
            }
            return result;
        }
    }

    @Test
    void ddlCrudAndHostileValuesRoundTrip() throws SQLException {
        // Create a table with a default value and a constraint.
        var peopleSchema = createTable("people")
                .column(column("id", DataType.INTEGER).primaryKey())
                .column(column("name", DataType.varchar(100)).defaultValue("O'Brien"))
                .constraint(Constraints.check("positive", column("id").gt(0)))
                .build();
        execute(peopleSchema);

        // Insert default, hostile and null values without treating them as SQL.
        String hostileValue = "'); DROP TABLE people; --";
        execute(insertInto("people").columns("id").values(1).build());
        execute(insertInto("people")
                .columns("id", "name")
                .values(2, hostileValue)
                .values(3, null)
                .build());

        var namesById = select(column("name"))
                .from("people")
                .orderBy(column("id").asc())
                .build();

        assertEquals(Arrays.asList("O'Brien", hostileValue, null), values(namesById));

        // Update one row and remove the row containing null.
        execute(update("people")
                .set("name", "updated")
                .where(column("id").eq(2))
                .build());
        execute(deleteFrom("people")
                .where(column("name").isNull())
                .build());

        assertEquals(List.of("O'Brien", "updated"), values(namesById));

        // Exercise index and column changes on the populated table.
        execute(createIndex("idx_name").on("people", "name").build());
        execute(dropIndex("idx_name"));
        execute(alterTable("people")
                .addColumn(column("age", DataType.INTEGER).defaultValue(18))
                .build());

        var ages = select(column("age")).from("people").build();

        assertEquals(List.of(18, 18), values(ages));

        execute(alterTable("people").dropColumn("age").build());
        execute(dropTable("people"));
    }

    @Test
    void conditionalSchemaChangesCanBeRepeatedWithoutLosingRows() throws SQLException {
        var table = createTable("items")
                .column("id", DataType.INTEGER)
                .ifNotExists()
                .build();
        var index = createIndex("idx_items_id")
                .on("items", "id")
                .ifNotExists()
                .build();

        execute(table);
        execute(insertInto("items").columns("id").values(7).build());
        execute(table);
        execute(index);
        execute(index);

        assertEquals(List.of(7), values(select(column("id")).from("items").build()));

        execute(dropIndexIfExists("idx_items_id"));
        execute(dropIndexIfExists("idx_items_id"));
        execute(dropTableIfExists("items"));
        execute(dropTableIfExists("items"));
    }

    @Test
    void recursiveCteProducesTheExpectedSequence() throws SQLException {
        var anchor = select(param(1)).build();
        var recursiveStep = select(column("n").plus(1))
                .from("numbers")
                .where(column("n").lt(5))
                .build();
        var sequence = select(column("n"))
                .withRecursive("numbers", anchor.unionAll(recursiveStep), "n")
                .from("numbers")
                .orderBy(column("n").asc())
                .build();

        var result = values(sequence);

        assertEquals(List.of(1, 2, 3, 4, 5), result);
    }

    @Test
    void mixedSetOperationsPreserveMeaning() throws SQLException {
        var one = select(param(1).as("n")).build();
        var two = select(param(2).as("n")).build();
        var three = select(param(3).as("n")).build();

        var intersection = values(one.union(two).intersect(two.union(three)));
        var difference = values(one.union(two).except(two));
        var duplicates = values(one.unionAll(one));

        assertEquals(List.of(2), intersection);
        assertEquals(List.of(1), difference);
        assertEquals(List.of(1, 1), duplicates);
    }

    @Test
    void windowsAndPaginatedDerivedTablesExecute() throws SQLException {
        execute(createTable("numbers").column("n", DataType.INTEGER).build());
        execute(insertInto("numbers")
                .columns("n")
                .values(1)
                .values(2)
                .values(3)
                .build());

        var runningTotal = sum(column("n")).over(window()
                .orderBy(column("n").asc())
                .rowsBetween(FrameBound.UNBOUNDED_PRECEDING, FrameBound.CURRENT_ROW));
        var runningTotals = select(runningTotal)
                .from("numbers")
                .orderBy(column("n").asc())
                .build();

        assertEquals(List.of(1, 3, 6), values(runningTotals));

        var page = select(column("n"))
                .from("numbers")
                .orderBy(column("n").asc())
                .limit(1)
                .offset(1)
                .build();
        var derivedPage = select(star()).from(page.as("page")).build();

        assertEquals(List.of(2), values(derivedPage));
    }

    @Test
    void groupedJoinsCaseAndInsertSelectExecute() throws SQLException {
        // Prepare source tables and rows.
        execute(createTable("users")
                .column(column("id", DataType.INTEGER).primaryKey())
                .build());
        execute(createTable("orders")
                .column("user_id", DataType.INTEGER)
                .column("amount", DataType.INTEGER)
                .build());
        execute(insertInto("users").columns("id").values(1).values(2).build());
        execute(insertInto("orders")
                .columns("user_id", "amount")
                .values(1, 10)
                .values(1, 20)
                .values(2, 5)
                .build());

        var totals = select(sum(column("o.amount")))
                .from(table("users").as("u"))
                .join(table("orders").as("o"), column("u.id").eq(column("o.user_id")))
                .groupBy(column("u.id"))
                .having(sum(column("o.amount")).ge(10))
                .build();

        assertEquals(List.of(30), values(totals));

        // Copy the grouped result, then classify it with CASE.
        execute(createTable("totals").column("amount", DataType.INTEGER).build());
        execute(insertInto("totals").columns("amount").from(totals).build());

        var label = caseWhen(column("amount").gt(20), "large").otherwise("small").build();
        var classifiedTotals = select(label).from("totals").build();

        assertEquals(List.of("large"), values(classifiedTotals));
    }
}
