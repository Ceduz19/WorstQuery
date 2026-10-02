package com.github.ceduz19.worstquery.ddl;

import com.github.ceduz19.worstquery.api.Query;
import com.github.ceduz19.worstquery.internal.Checks;
import com.github.ceduz19.worstquery.internal.statement.DdlStatements;
import com.github.ceduz19.worstquery.model.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Portable index builder for named columns. Expressions can use the trusted SQL API.
 */
public final class CreateIndexBuilder {
    private final Identifier index;
    private Identifier table;
    private List<Identifier> columns = List.of();
    private boolean unique;
    private boolean ifNotExists;

    CreateIndexBuilder(Identifier index) {
        this.index = Objects.requireNonNull(index);
    }

    /**
     * Sets the target table and replaces the list of index columns.
     */
    public CreateIndexBuilder on(String table, String... columns) {
        this.table = Identifier.of(table);
        this.columns = Arrays.stream(columns).map(Identifier::ofParts).toList();
        return this;
    }

    /**
     * Requests a unique index.
     */
    public CreateIndexBuilder unique() {
        unique = true;
        return this;
    }

    /**
     * Skips creation when an index with this name already exists, where supported.
     */
    public CreateIndexBuilder ifNotExists() {
        ifNotExists = true;
        return this;
    }

    /**
     * Snapshots the index; a target table and at least one column are required.
     */
    public Query build() {
        if (table == null) {
            throw new IllegalStateException("Index requires a table");
        }
        List<Identifier> keys = Checks.nonEmpty(columns, "Index columns");
        return new DdlStatements.CreateIndex(index, table, keys, unique, ifNotExists);
    }
}
