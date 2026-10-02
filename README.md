# WorstQuery

Java 17 library for building SQL queries with a fluent API. It produces SQL text and
ordered parameters without opening connections or executing statements. It has no
production dependencies.

The API is organized by purpose; package names below are relative to
`com.github.ceduz19.worstquery`.

| Class | Package | Purpose |
| --- | --- | --- |
| `Queries` | `dml` | SELECT, INSERT, UPDATE, and DELETE |
| `Schema` | `ddl` | Tables, columns, indexes, and schema changes |
| `Expressions` | `expression` | References, parameters, predicates, CASE, and windows |
| `Functions` | `expression` | SQL functions and aggregates |
| `SqlFragments` | `fragment` | Explicit SQL and composable fragments |

`Constraints` and `DataType` in `ddl` define constraints and data types. Builders come
from the factory methods, are mutable, and are not thread-safe. `build()` returns an
immutable snapshot that can be rendered with different dialects.

## Build and use

On Windows:

```powershell
.\gradlew.bat build
```

On Linux/macOS, run `./gradlew build`. The toolchain requires an available JDK 17.
The build generates JARs, sources, and Javadoc in `build/libs/`; HTML documentation
is in `build/docs/javadoc/`, and the test report is in `build/reports/tests/test/`.

To use a locally built JAR in another Gradle project:

```groovy
dependencies {
    implementation files('libs/WorstQuery-1.0-SNAPSHOT.jar')
}
```

The JAR supports both the classpath and the module path. In a modular project, declare:

```java
module my.application {
    requires com.github.ceduz19.worstquery;
}
```

The module exports its domain API and SPI. The `internal` packages are neither
exported nor opened. On the classpath, ordinary Java access modifiers still apply,
but module export restrictions do not. Internal types are not part of the public
contract in either mode.

The build does not publish automatically to Maven Central.

## SELECT and parameters

```java
import com.github.ceduz19.worstquery.api.RenderedQuery;
import com.github.ceduz19.worstquery.api.RenderMode;
import com.github.ceduz19.worstquery.dml.Queries;
import com.github.ceduz19.worstquery.expression.Expressions;
import com.github.ceduz19.worstquery.dialect.Dialects;

var query = Queries.select(Expressions.column("u.id"), Expressions.column("u.name"))
        .from(Expressions.table("users").as("u"))
        .where(Expressions.column("u.age").ge(18).and(Expressions.column("u.deleted_at").isNull()))
        .orderBy(Expressions.column("u.name").asc())
        .limit(20)
        .offset(40)
        .build();

RenderedQuery result = query.render(Dialects.POSTGRESQL);
// SELECT "u"."id", "u"."name" FROM "users" AS "u"
// WHERE (("u"."age" >= ?) AND ("u"."deleted_at" IS NULL))
// ORDER BY "u"."name" ASC LIMIT 20 OFFSET 40
// result.parameters(): [18]

// The same query with values inlined into the SQL text.
RenderedQuery inlined = query.render(Dialects.POSTGRESQL, RenderMode.INLINED);
// inlined.sql():
// SELECT "u"."id", "u"."name" FROM "users" AS "u"
// WHERE (("u"."age" >= 18) AND ("u"."deleted_at" IS NULL))
// ORDER BY "u"."name" ASC LIMIT 20 OFFSET 40
// inlined.parameters(): []

// Explicit parameterized rendering, equivalent to render(dialect).
RenderedQuery parameterized = query.render(Dialects.POSTGRESQL, RenderMode.PARAMETERIZED);
```

`PARAMETERIZED` remains the default mode for JDBC use. `INLINED` embeds values as
dialect-specific literals and returns an empty parameter list. The selected mode
also applies to subqueries, CTEs, set operations, and parameters in fragments. Raw
SQL is preserved: the library never replaces `?` characters in raw text. DDL
contexts that require literals inline them in both modes.

The built-in dialects accept `null`, `String`, `Character`, `Boolean`, `Byte`,
`Short`, `Integer`, `Long`, `BigInteger`, `BigDecimal`, and finite `Float`/`Double`
values as literals. Unsupported types (including dates and `byte[]`), non-finite
numbers, and strings containing NUL cause `IllegalArgumentException`. Values are
never converted through a generic `toString()`. Custom dialects can extend
`SqlDialect.literal()` to handle other types.

`INLINED` SQL contains application values. Avoid logging it when it could expose
credentials or other sensitive data.

`sql()` does not add a trailing semicolon. Parameters correspond to `?` placeholders
in SQL order, including those in CTEs, subqueries, and fragments. Limits and offsets
are nonnegative integers rendered by the dialect.

The parameter list is immutable and permits `null`. Application objects in the list
are not deep-copied, so avoid mutating them while reusing a query. The application
manages connections, JDBC types, and binding, including the SQL types needed for
some null parameters.

Qualified names such as `Expressions.column("schema.table.id")` are split and quoted
one component at a time. For names containing literal dots, use
`Expressions.identifier("schema", "table.with.dot")`. `Expressions.star()` and
`Expressions.star("u")` produce `*` and `"u".*`, respectively. Do not put asterisks
or aliases in column names.

Quoting preserves case. Pass actual object names, especially for databases that
distinguish between quoted and unquoted identifiers.

## Modifying data

```java
var insert = Queries.insertInto("users").columns("id", "name")
        .values(1, "Ada")
        .values(2, null)
        .build();

var update = Queries.update("users")
        .set("name", "Grace")
        .set("visits", Expressions.column("visits").plus(1))
        .where(Expressions.column("id").eq(1))
        .build();

var delete = Queries.deleteFrom("users").where(Expressions.column("id").eq(2)).build();

var copy = Queries.insertInto("archive").columns("id", "name")
        .from(Queries.select(Expressions.column("id"), Expressions.column("name")).from("users").build())
        .build();
```

`UPDATE` and `DELETE` without `where()` intentionally affect every row. A value
passed to `set`, `values`, or a comparison becomes a parameter; an `Expression`
is composed as SQL instead. `Expressions.param(value)` forces binding even when
the value implements a library interface.

## Advanced queries

INNER/LEFT/RIGHT/FULL/CROSS joins, aggregates, `DISTINCT`, `GROUP BY`, `HAVING`,
`CASE`, scalar subqueries, `EXISTS`, and `IN` are available. Logical and arithmetic
operators preserve grouping with parentheses.

```java
import com.github.ceduz19.worstquery.expression.Functions;

var totals = Queries.select(Expressions.column("user_id"), Functions.sum(Expressions.column("amount")).as("total"))
        .from("orders")
        .groupBy(Expressions.column("user_id"))
        .having(Functions.sum(Expressions.column("amount")).gt(100))
        .build();

var report = Queries.select(Expressions.column("u.name"), Expressions.column("t.total"))
        .from(Expressions.table("users").as("u"))
        .join(totals.as("t"), Expressions.column("u.id").eq(Expressions.column("t.user_id")))
        .build();

var grade = Expressions.caseWhen(Expressions.column("score").ge(90), "A")
        .when(Expressions.column("score").ge(60), "B")
        .otherwise("C").build();

var ranking = Queries.select(Expressions.column("id"),
        Functions.rowNumber().over(Expressions.window()
                .partitionBy(Expressions.column("team"))
                .orderBy(Expressions.column("score").desc())).as("position"))
        .from("scores").build();
```

Configure window frames with `rowsBetween`, `rangeBetween`, or `groupsBetween` and
`FrameBound` (`preceding`, `following`, `CURRENT_ROW`, `UNBOUNDED_PRECEDING`, and
`UNBOUNDED_FOLLOWING`). An explicit frame requires ordering; a RANGE frame with an
offset requires exactly one ordering expression.

CTEs and recursion:

```java
var anchor = Queries.select(Expressions.param(1)).build();
var recursive = Queries.select(Expressions.column("n").plus(1))
        .from("numbers").where(Expressions.column("n").lt(5)).build();

var sequence = Queries.select(Expressions.column("n"))
        .withRecursive("numbers", anchor.unionAll(recursive), "n")
        .from("numbers")
        .orderBy(Expressions.column("n").asc())
        .build();
```

`with(name, query, columns...)` defines a nonrecursive CTE. `union`, `unionAll`,
`intersect`, `intersectAll`, `except`, and `exceptAll` compose queries while
preserving grouping along the chain. To sort or paginate a compound result, use
it as a derived table: `Queries.select(Expressions.star()).from(set.as("s"))`.

`eq(null)` and `ne(null)` become `IS NULL` and `IS NOT NULL`. An empty `in()` is
false, and an empty `notIn()` is true. Null values within a list retain ordinary
SQL three-valued logic.

## Defining a schema

```java
import com.github.ceduz19.worstquery.ddl.Schema;
import com.github.ceduz19.worstquery.ddl.DataType;
import com.github.ceduz19.worstquery.ddl.Constraints;
import com.github.ceduz19.worstquery.ddl.Constraint;

var schema = Schema.createTable("users")
        .column(Schema.column("id", DataType.BIGINT).primaryKey())
        .column(Schema.column("name", DataType.varchar(100)).notNull())
        .column(Schema.column("active", DataType.BOOLEAN).defaultValue(true))
        .constraint(Constraints.unique("uq_users_name", "name"))
        .constraint(Constraints.check("positive_id", Expressions.column("id").gt(0)))
        .build();

var add = Schema.alterTable("users").addColumn(Schema.column("score", DataType.INTEGER)).build();
var change = Schema.alterTable("users").alterColumnType("score", DataType.BIGINT).build();
var index = Schema.createIndex("idx_users_name").on("users", "name").build();
var removeIndex = Schema.dropIndex("idx_users_name", "users");
var removeConstraint = Schema.alterTable("users")
        .dropConstraint("uq_users_name", Constraint.Kind.UNIQUE).build();
```

Every ALTER builder produces one operation. `alterColumnType` changes only the
type and is rejected by dialects that require more information. For MySQL,
MariaDB, and SQL Server, use `modifyColumn` with an explicit definition:

```java
var change = Schema.alterTable("users")
        .modifyColumn(Schema.column("score", DataType.BIGINT).notNull())
        .build();
```

On MySQL and MariaDB, the definition replaces the column's attributes; include
any defaults you want to retain. On SQL Server, this operation accepts a type and
nullability; defaults and constraints require separate operations or explicit SQL.

### Conditional schema changes

Use `ifNotExists()` when creating a table or index and the `Schema` factory methods
for conditional removal:

```java
var createUsers = Schema.createTable("users")
        .column("id", DataType.BIGINT)
        .column("name", DataType.varchar(100))
        .ifNotExists()
        .build();

var createNameIndex = Schema.createIndex("idx_users_name")
        .on("users", "name")
        .ifNotExists()
        .build();

var removeNameIndex = Schema.dropIndexIfExists("idx_users_name", "users");
var removeUsers = Schema.dropTableIfExists("users");
```

The table argument to `dropIndexIfExists` is required for MariaDB and SQL Server.
Each ALTER operation has a separate existence check for the table and, where
supported, for the column or constraint:

```java
var addScore = Schema.alterTable("users")
        .ifExists()
        .addColumnIfNotExists(Schema.column("score", DataType.INTEGER))
        .build();
// PostgreSQL: ALTER TABLE IF EXISTS "users" ADD COLUMN IF NOT EXISTS "score" INTEGER

var removeScore = Schema.alterTable("users")
        .dropColumnIfExists("score")
        .build();

var removeUniqueName = Schema.alterTable("users")
        .dropConstraintIfExists("uq_users_name", Constraint.Kind.UNIQUE)
        .build();
```

`ifExists()` applies to the table itself and can be called before or after the
ALTER action. The action methods are `addColumnIfNotExists`, `dropColumnIfExists`,
`modifyColumnIfExists`, `addConstraintIfNotExists`, and `dropConstraintIfExists`.
`addConstraintIfNotExists` supports MariaDB primary keys, unique indexes, and
foreign keys, but not CHECK constraints. MariaDB does not support
`dropConstraintIfExists` for a primary key. Unsupported clauses raise
`UnsupportedSqlFeatureException` when rendered. An existence clause only checks
whether an object has the given name; it does not verify its definition.

DDL DEFAULT and CHECK values render as literals because those contexts do not use
JDBC parameters. Strings, characters, booleans, standard Java numbers,
`BigInteger`, `BigDecimal`, and null are accepted; arbitrary types, NUL, and
non-finite numbers are rejected. For temporal expressions, provide trusted syntax,
for example `defaultValue(SqlFragments.raw("CURRENT_TIMESTAMP"))`.

Logical data types are adapted to the dialect. Maximum lengths, effective
precision, collation, and compatibility between values and types remain database
constraints. SQLite does not enforce declared lengths like strictly typed
databases. Oracle does not expose a standalone TIME type here.
`DataType.custom("JSONB")` allows an explicit native type.

## Parameterized raw SQL

Administrative commands, procedures, UPSERT, MERGE, vendor extensions, or entire
statements can be composed from explicit nodes:

```java
import com.github.ceduz19.worstquery.fragment.SqlFragments;

var command = SqlFragments.query(
        SqlFragments.raw("CALL "), Expressions.identifier("app", "recalculate"),
        SqlFragments.raw("("), Expressions.param(42), SqlFragments.raw(", "),
        Expressions.param("monthly"), SqlFragments.raw(")")
);

var expression = SqlFragments.fragment(
        SqlFragments.raw("COALESCE("), Expressions.column("name"),
        SqlFragments.raw(", "), Expressions.param("unknown"), SqlFragments.raw(")")
);
var predicate = SqlFragments.condition(
        Expressions.column("id"), SqlFragments.raw(" = "), Expressions.param(42)
);
```

Pass **trusted syntax only** to `raw` and `DataType.custom`. Use `param` for
application inputs and `identifier` for names. Fragments are concatenated exactly,
without added spaces. Raw SQL is not parsed: a `?` inside `raw` does not register
a parameter, and raw SQL is not translated between dialects.
`SqlFragments.selectSql(parts...)` declares a raw row-returning query that can be
used in other queries.

## Dialects and feature matrix

The reference versions are PostgreSQL 14, MySQL 8.0.31, MariaDB 10.6,
SQLite 3.39, SQL Server 2019, and Oracle 19c. The matrix describes this library's
support for those baselines; it is not a certification for every version or
configuration. ANSI is a starting point for custom adapters.

| Feature | ANSI | PostgreSQL | MySQL | MariaDB | SQLite | SQL Server | Oracle |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SELECT / INSERT / UPDATE / DELETE | yes | yes | yes | yes | yes | yes | yes |
| Multirow INSERT | VALUES | VALUES | VALUES | VALUES | VALUES | VALUES | INSERT ALL |
| INNER / LEFT / RIGHT / CROSS JOIN | yes | yes | yes | yes | yes | yes | yes |
| FULL JOIN | yes | yes | no | no | yes | yes | yes |
| CTEs, including recursive CTEs | yes | yes | yes | yes | yes | yes | yes |
| UNION / UNION ALL | yes | yes | yes | yes | yes | yes | yes |
| INTERSECT / EXCEPT | yes | yes | yes | yes | yes | yes | EXCEPT → MINUS |
| INTERSECT ALL / EXCEPT ALL | yes | yes | yes | yes | no | no | no |
| ROWS / RANGE windows | yes | yes | yes | yes | yes | RANGE without offset | yes |
| GROUPS windows | yes | yes | no | no | yes | no | no |
| CREATE / DROP TABLE and INDEX | yes | yes | yes | yes | yes | yes | yes |
| ADD / DROP COLUMN | yes | yes | yes | yes | limited | yes | yes |
| Type-only ALTER | yes | yes | no | no | no | no | yes |
| Full column redefinition | no | no | yes | yes | no | type and nullability | no |
| ADD / DROP constraints | yes | yes | yes | yes | no | yes | yes |

Conditional DDL support for the same baselines:

| Existence clause | ANSI | PostgreSQL | MySQL | MariaDB | SQLite | SQL Server | Oracle |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE TABLE IF NOT EXISTS | no | yes | yes | yes | yes | no | no |
| CREATE INDEX IF NOT EXISTS | no | yes | no | yes | yes | no | no |
| DROP TABLE IF EXISTS | no | yes | yes | yes | yes | yes | no |
| DROP INDEX IF EXISTS | no | yes | no | yes | yes | yes | no |
| ALTER TABLE IF EXISTS | no | yes | no | yes | no | no | no |
| ADD COLUMN IF NOT EXISTS | no | yes | no | yes | no | no | no |
| DROP COLUMN IF EXISTS | no | yes | no | yes | no | yes | no |
| MODIFY COLUMN IF EXISTS | no | no | no | yes | no | no | no |
| ADD CONSTRAINT IF NOT EXISTS | no | no | no | limited | no | no | no |
| DROP CONSTRAINT IF EXISTS | no | yes | no | limited | no | yes | no |

The MariaDB constraint exceptions are described above. The Oracle adapter targets
the general Oracle 19c baseline and does not enable existence clauses introduced
in release update 19.28.

Deliberate limitations:

- SQL Server requires `ORDER BY` for pagination. Ordering without pagination in a
  subquery is rejected.
- The library rejects `limit(0)` in dialects that render FETCH; LIMIT dialects
  accept it. Offset without a limit has dialect-specific syntax.
- Oracle recursive CTEs require output column names. Nested WITH is rejected for
  Oracle and SQL Server. Structured CTEs belong to SELECT queries; use raw SQL for
  database-specific placement before INSERT, UPDATE, or DELETE.
- DROP INDEX also requires the table on MySQL, MariaDB, and SQL Server.
- SQLite does not support type changes or adding and dropping constraints through
  this API. Adding PRIMARY KEY or UNIQUE columns is rejected.
- Native functions, function-specific window frames, recursive CTE restrictions,
  data conversions, and schema constraints must be valid for the database. The
  library checks structure and declared capabilities; it does not inspect the
  schema or replace the database engine's validation.

Unsupported features raise `UnsupportedSqlFeatureException`. Incomplete structures
and invalid arguments raise `IllegalStateException` or `IllegalArgumentException`;
required Java references must not be null.

References for syntax differences:
[PostgreSQL WITH](https://www.postgresql.org/docs/14/queries-with.html),
[MySQL set operations](https://dev.mysql.com/doc/refman/8.4/en/set-operations.html),
[MySQL default expressions](https://dev.mysql.com/doc/refman/8.4/en/data-type-defaults.html),
[SQLite ALTER TABLE](https://www.sqlite.org/lang_altertable.html),
[SQL Server ORDER BY](https://learn.microsoft.com/en-us/sql/t-sql/queries/select-order-by-clause-transact-sql),
[Oracle SELECT](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SELECT.html),
[PostgreSQL ALTER TABLE](https://www.postgresql.org/docs/14/sql-altertable.html),
[MariaDB ALTER TABLE](https://mariadb.com/docs/server/reference/sql-statements/data-definition/alter/alter-table),
[SQL Server ALTER TABLE](https://learn.microsoft.com/en-us/sql/t-sql/statements/alter-table-transact-sql?view=sql-server-ver15),
and [Oracle 19.28 changes](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Changes-in-This-Release-for-Oracle-Database-SQL-Language-Reference.html).

## Extending the library

`SqlDialect`, `Feature`, `SqlPart`, and `SqlWriter` are in the `spi` package.
`Dialects` exposes immutable instances as `SqlDialect` constants; it is not an
enum and does not need to be instantiated.

Implement `SqlDialect` to add an RDBMS. Its default methods describe the ANSI
baseline; adapt quoting, types, literals, pagination, DDL operations, and
`supports(Feature)` to the database's capabilities. Conditional DDL features are
disabled by default. When enabling one, provide any required dialect-specific
rendering override as well.

```java
import com.github.ceduz19.worstquery.spi.SqlDialect;
import com.github.ceduz19.worstquery.spi.Feature;

SqlDialect dialect = new SqlDialect() {
    @Override
    public String name() {
        return "MyDatabase";
    }

    @Override
    public boolean supports(Feature feature) {
        return SqlDialect.super.supports(feature) && feature != Feature.FULL_JOIN;
    }
};

var result = Queries.select(Expressions.column("id")).from("users").build().render(dialect);
```

`SqlPart`, `Expression`, `Condition`, `Query`, and `SelectQuery` are extension
points for new nodes. Use `SqlWriter.value` to preserve parameter order and
`subquery` when entering a nested query. Added nodes must be immutable. The
library supplies the writer; do not retain it or share it across renderings.
The concrete rendering session and its buffers remain internal. Each rendering
creates an independent context; builders are not designed for concurrent access.

## Structure and verification

The project has one Gradle and Java module. Builders validate arguments and
produce snapshots; internal DML and DDL renderers emit SQL, while dialect
implementations handle syntax differences. The internal context collects SQL
and parameters.

Utility classes are final with private constructors. Builder constructors have
package-private access; complex immutable types are built through their factory
methods. The SPI exposes only the contracts needed for custom dialects and nodes.

The test suite focuses on SQL behavior: parameterized and inlined rendering,
parameter order, escaping, snapshots, concurrency, validation, and custom dialects
and nodes. It does not include structural API checks or compilation of external
JAR consumers. Additional JDBC tests run CRUD, DDL (including conditional schema
changes), recursive CTEs, set operations, and windows against in-memory SQLite.
The SQLite driver is a test-only dependency. Other dialect adapters are checked
through rendering tests without starting external servers.

## Migration from the original API

The single `Sql` facade was removed. No deprecated methods duplicate the new
factory methods.

| Before | Now |
| --- | --- |
| `Sql.select / insertInto / update / deleteFrom` | `Queries.select / insertInto / update / deleteFrom` |
| `Sql.createTable / alterTable / createIndex / dropTable / dropIndex` | Corresponding `Schema` methods |
| `Sql.column(name, type)` | `Schema.column(name, type)` |
| `Sql.column / table / identifier / param / caseWhen / window` | Corresponding `Expressions` methods |
| `Sql.count / sum / avg / min / max / rowNumber / function` | Corresponding `Functions` methods |
| `Sql.raw / fragment / condition / query / selectSql` | Corresponding `SqlFragments` methods |
| `dialect.SqlDialect`, `dialect.Feature`, `api.SqlPart` | Corresponding types in `spi` |
| Concrete rendering context | `spi.SqlWriter`, passed to extension methods |
| Direct builder construction | Domain factory methods |
| `Dialects` enum | `SqlDialect` constants |

## Contribution conventions

Follow `.editorconfig`: UTF-8, LF line endings, four spaces, and lines generally
within 120 columns. Use explicit imports, braces even for short blocks, and one
declaration per line. Separate validation, value construction, and returns with
blank lines; break fluent chains at logical steps.

Place new factory methods in their respective domains. Builders must not emit
SQL; shared helpers belong in internal packages. Before making a type or method
public, check that it is needed by library users or the SPI.
