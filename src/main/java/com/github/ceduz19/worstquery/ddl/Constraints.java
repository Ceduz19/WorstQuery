package com.github.ceduz19.worstquery.ddl;

import com.github.ceduz19.worstquery.expression.Condition;
import com.github.ceduz19.worstquery.model.Identifier;

import java.util.Arrays;
import java.util.List;

/**
 * Non-instantiable factories for named constraints.
 */
public final class Constraints {
    private Constraints() {
    }

    private static List<Identifier> identifiers(String... names) {
        return Arrays.stream(names).map(Identifier::ofParts).toList();
    }

    /**
     * Creates a named, optionally composite PRIMARY KEY.
     */
    public static Constraint primaryKey(String name, String... columns) {
        return key(name, Constraint.Kind.PRIMARY_KEY, columns);
    }

    /**
     * Creates a named UNIQUE constraint.
     */
    public static Constraint unique(String name, String... columns) {
        return key(name, Constraint.Kind.UNIQUE, columns);
    }

    private static Constraint key(String name, Constraint.Kind kind, String[] columns) {
        return new Constraint(Identifier.ofParts(name), kind, identifiers(columns), null, List.of(), null);
    }

    /**
     * Creates a foreign key whose local and referenced column lists have equal length.
     */
    public static Constraint foreignKey(String name, List<String> columns, String table,
                                        List<String> referencedColumns) {
        return new Constraint(
                Identifier.ofParts(name),
                Constraint.Kind.FOREIGN_KEY,
                columns.stream().map(Identifier::ofParts).toList(),
                Identifier.of(table),
                referencedColumns.stream().map(Identifier::ofParts).toList(),
                null
        );
    }

    /**
     * Creates a named CHECK constraint; values in the predicate render as literals.
     */
    public static Constraint check(String name, Condition condition) {
        return new Constraint(Identifier.ofParts(name), Constraint.Kind.CHECK, List.of(), null, List.of(), condition);
    }
}
