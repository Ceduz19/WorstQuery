package com.github.ceduz19.worstquery.model;

import com.github.ceduz19.worstquery.spi.SqlWriter;

import java.util.Objects;

/**
 * An immutable table reference.
 */
public record Table(Identifier name, Identifier alias) implements TableSource {

    public Table {
        Objects.requireNonNull(name, "name");
    }

    public Table as(String alias) {
        return new Table(name, Identifier.ofParts(alias));
    }

    @Override
    public void appendTo(SqlWriter c) {
        c.part(name);
        if (alias != null) {
            c.sql(c.dialect().tableAliasSeparator()).part(alias);
        }
    }
}
