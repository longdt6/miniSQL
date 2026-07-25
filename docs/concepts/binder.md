# Binder (Semantic Analysis)

Resolves names, type-checks the query, and annotates the AST with metadata. Bridges the gap between syntax (parser output) and meaning (executor input).

## What a Binder Does

The parser produced a syntactically valid AST. But is it **meaningful**?

```sql
SELECT name, age FROM users WHERE age > 25
```

The binder answers:
1. Does table `users` exist? → Look up in catalog
2. Do columns `name` and `age` exist in `users`? → Check TableMetadata
3. Is `age > 25` a valid comparison? → Both are INTEGER? Yes.
4. What types are `name` and `age`? → Annotate ColumnRef with DataType

## Three Core Responsibilities

### 1. Table Resolution

Every time the query references a table by name, the binder looks it up:

```java
TableMetadata table = catalog.getTable(tableName);
// If not found: throw SqlException("Table 'users' does not exist")
```

After binding, the AST node holds a **reference** to the `TableMetadata`, not just the string `"users"`.

### 2. Column Resolution

Every `ColumnRef("name")` must be resolved to a real column:

```java
ColumnMetadata col = tableMetadata.getColumn("name");
// If not found: throw SqlException("Column 'name' not found in table 'users'")
```

After binding, `ColumnRef` is annotated with:
- `dataType` — the column's type (e.g., `IntegerType`)
- `columnIndex` — the column's position in the table (e.g., 0 for `id`, 1 for `name`)
- `tableName` — which table this column belongs to (matters when JOINs are added)

### 3. Type Checking

The binder verifies every operation makes type sense:

```
LiteralValue(25) → parsed as INTEGER ✓
LiteralValue("'hello'") → parsed as TEXT ✓

BinaryExpression(ColumnRef("age"), GT, LiteralValue(25))
  → left type: INTEGER, right type: INTEGER
  → comparison is valid ✓

BinaryExpression(ColumnRef("name"), GT, LiteralValue(25))
  → left type: TEXT, right type: INTEGER
  → CANNOT compare TEXT with INTEGER → ERROR ✗
```

## Additional Bind-Time Operations

### Star Expansion

```sql
SELECT * FROM users
```

The column list `*` is syntactic sugar. The binder expands it:

```java
if (selectStmt.isStar()) {
    // Replace STAR with all columns from the table
    List<Expression> allCols = new ArrayList<>();
    for (ColumnMetadata col : table.getColumns()) {
        allCols.add(new ColumnRef(col.getName()));
    }
    selectStmt.setColumns(allCols);
}
```

Before binding: `SelectStmt { columns: [Star], table: "users" }`  
After binding: `SelectStmt { columns: [ColumnRef(id, INTEGER, 0), ColumnRef(name, TEXT, 1), ColumnRef(age, INTEGER, 2)], table: users }`

### Literal Parsing

The parser produces raw literal tokens. The binder converts them to typed Java values:

```java
// INT_LITERAL "25" → Java int 25
// STRING_LITERAL "'Alice'" → Java String "Alice"
// TRUE_KW → Java boolean true

LiteralValue lit = (LiteralValue) expr;
Object parsed = DataType.parseLiteral(lit.getRawValue(), lit.getTokenType());
lit.setValue(parsed);
```

### Implicit Type Coercion (Future)

Real databases do implicit coercion: `age > '25'` works because `'25'` can be coerced to INTEGER. We don't do this — it adds complexity and hides bugs. We enforce strict type matching.

## What the Binder Produces

The binder takes the parser's raw AST and produces a **resolved AST** — the same tree structure, but with metadata attached to every node:

```
Before binding:                        After binding:
SelectStmt {                           SelectStmt {
  columns: [                             columns: [
    ColumnRef("name"),        →            ColumnRef("name", TEXT, pos=1),
    ColumnRef("age")          →            ColumnRef("age", INTEGER, pos=2)
  ],                                      ],
  table: "users",             →          table: TableMetadata(1, "users", ...),
  where:                       →          where:
    BinaryExpr(                  →            BinaryExpr(
      left: ColumnRef("age"),    →              left: ColumnRef("age", INTEGER, pos=2),
      op: GT,                    →              op: GT,
      right: LiteralValue(25)    →              right: LiteralValue(25, INTEGER)
    )                            →            )
}                                      }
```

The planner and executor can now work with types and column indices directly — no more name lookups, no more type guessing.

## Binding for Each Statement Type

### SELECT
- Resolve table name → TableMetadata
- Expand `*` if present
- Resolve each column reference
- Type-check WHERE expression
- Check ORDER BY columns exist
- Resolve column types for output schema

### INSERT
- Resolve table name → TableMetadata
- Check column count matches (or if no column list, count must match all columns)
- For each value: resolve type against target column, parse literal accordingly
- Error if: too few values, too many values, type mismatch

### UPDATE
- Resolve table name → TableMetadata
- Resolve SET assignments: verify columns exist, type-check values
- Type-check WHERE expression

### DELETE
- Resolve table name → TableMetadata
- Type-check WHERE expression

### CREATE TABLE
- Check table doesn't already exist
- Resolve column type names (INTEGER, TEXT, etc.) → DataType instances
- Check for duplicate column names

### DROP TABLE
- Check table exists (or handle IF EXISTS)

## Error Messages

The binder should produce helpful errors:

```
✗ "SELECT bad_column FROM users"
  → Error: Column 'bad_column' not found in table 'users'.
     Available columns: id, name, age

✗ "SELECT * FROM nonexistent"
  → Error: Table 'nonexistent' does not exist

✗ "INSERT INTO users VALUES ('hello', 42)"
  → Error: Type mismatch for column 'id'. Expected INTEGER, got TEXT ('hello')

✗ "INSERT INTO users VALUES (1)"
  → Error: Expected 3 values for table 'users', got 1
```

---

→ Previous: [Parser](parser.md)
→ Related: [SQL Query Lifecycle](query-lifecycle.md)
→ Related: [Catalog](catalog.md) — where the binder looks up tables and columns
