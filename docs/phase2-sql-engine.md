# Phase 2: SQL Engine

> **Status**: 🔜 Pending | **Depends on**: Phase 1 (storage engine)

## Goal

Build the SQL processing pipeline: lexer → parser → binder → planner → executor. When done, you can pass a SQL string to `SqlEngine.execute()` and get back rows — the full path from text to data.

## Steps

### 1. Lexer — Hand-Written Tokenizer

Converts a SQL string into a stream of tokens.

```
"SELECT name FROM users WHERE age > 25"
                    ↓
[SELECT, IDENT("name"), FROM, IDENT("users"), WHERE, IDENT("age"), GT, INT_LITERAL(25)]
```

**Token types:** `SELECT`, `FROM`, `WHERE`, `INSERT`, `INTO`, `VALUES`, `CREATE`, `TABLE`, `DROP`, `UPDATE`, `SET`, `DELETE`, `ORDER`, `BY`, `ASC`, `DESC`, `LIMIT`, `AND`, `OR`, `NOT`, `IDENTIFIER`, `INT_LITERAL`, `FLOAT_LITERAL`, `STRING_LITERAL`, `BOOLEAN_LITERAL`, `NULL`, `COMMA`, `LPAREN`, `RPAREN`, `SEMICOLON`, `STAR`, `EQ`, `NEQ`, `LT`, `GT`, `LTE`, `GTE`, `PLUS`, `MINUS`, `SLASH`, `EOF`

**Key behaviors:**
- Skip whitespace
- Identifiers: `[a-zA-Z_][a-zA-Z0-9_]*` → check if keyword, else IDENTIFIER
- Numbers: digits+ → INT_LITERAL; digits.digits → FLOAT_LITERAL
- Strings: `'...'` → STRING_LITERAL (handle `''` as escaped single quote)
- Line comment: `--` to end of line
- Case-insensitive keywords: `select` = `SELECT` = `Select`

### 2. Parser — Recursive Descent

Converts token stream into AST nodes.

**Grammar (subset):**
```
statement    → select_stmt | insert_stmt | update_stmt | delete_stmt
              | create_table_stmt | drop_table_stmt

select_stmt  → SELECT column_list FROM table
               [WHERE expr] [ORDER BY order_list] [LIMIT int]

insert_stmt  → INSERT INTO table [(col_list)] VALUES (value_list)

update_stmt  → UPDATE table SET col = value [, ...] [WHERE expr]

delete_stmt  → DELETE FROM table [WHERE expr]

create_table → CREATE TABLE table (col_def [, ...])

drop_table   → DROP TABLE [IF EXISTS] table

expr         → term (AND | OR term)*
term         → operand (EQ | NEQ | LT | GT | LTE | GTE operand)*
operand      → column_ref | literal | LPAREN expr RPAREN
```

**AST nodes (in `com.minisql.engine.parser.ast`):**
- `Statement` — base interface
- `CreateTableStmt { table, List<ColumnDef> }`
- `DropTableStmt { table, ifExists }`
- `InsertStmt { table, columns, List<List<Expression>> values }`
- `SelectStmt { columns, table, where, orderBy, limit, offset }`
- `UpdateStmt { table, List<Pair<col,expr>> sets, where }`
- `DeleteStmt { table, where }`
- `Expression` — base for WHERE expressions
- `ColumnRef { name }`
- `LiteralValue { value, DataType }`
- `BinaryExpression { left, operator, right }`

### 3. Binder — Semantic Analysis

Resolves names and type-checks the AST.

**Responsibilities:**
1. **Table resolution** — `"users"` → `TableMetadata` from catalog. Errors if table doesn't exist.
2. **Column resolution** — `ColumnRef("age")` → verified it exists in the table, annotated with its `DataType` and column position.
3. **Type checking** — `INSERT INTO users VALUES ('hello', 42)` where column 0 is INTEGER? Error.
4. **Star expansion** — `SELECT *` → expand to all columns from `TableMetadata`.
5. **WHERE expression type checking** — comparing `age > 'hello'`? Error.

**Output:** same AST shape, but with metadata attached to nodes. `ColumnRef` now holds `DataType` and `columnIndex`. `LiteralValue` values are parsed to Java types (`"42"` → `Integer(42)`).

### 4. Planner — AST → Execution Plan

Converts the resolved AST into a tree of `PlanNode` (operator nodes).

```
SELECT name, age FROM users WHERE age > 25 ORDER BY name LIMIT 10

                    ↓

          LimitNode(10)
              │
          SortNode(name, ASC)
              │
          ProjectNode([name, age])
              │
          FilterNode(age > 25)
              │
          TableScanNode(users)
```

**Plan node types (in `com.minisql.engine.planner`):**
- `PlanNode` — base, each node knows its output schema
- `TableScanPlan { table, tableMetadata }`
- `FilterPlan { child, expression }`
- `ProjectPlan { child, List<ColumnRef> }`
- `SortPlan { child, List<OrderBy> }`
- `LimitPlan { child, limit }`
- `InsertPlan { table, columns, rows }`
- `UpdatePlan { table, tableMetadata, sets, where }`
- `DeletePlan { table, tableMetadata, where }`
- `CreateTablePlan { table, columns }`
- `DropTablePlan { table, ifExists }`

### 5. Operators — Volcano Iterator Model

Each plan node becomes an operator. Every operator follows the same interface:

```java
interface Operator {
    void open();                          // initialize, acquire resources
    Row next();                           // return next row, or null if done
    void close();                         // release resources
    TupleDesc getOutputSchema();         // what columns this produces
}
```

**Operators (in `com.minisql.engine.executor.operators`):**

| Operator | What it does |
|----------|-------------|
| `TableScanOperator` | Iterates all live tuples from a `HeapFile` via `BufferPool`, deserializes each into a `Row` |
| `FilterOperator` | Wraps another operator, calls `next()` and only emits rows that satisfy the WHERE expression |
| `ProjectOperator` | Wraps another operator, strips each row to only the requested columns |
| `SortOperator` | Wraps another operator, accumulates ALL rows in memory, sorts by Comparator, then emits |
| `LimitOperator` | Wraps another operator, stops after N rows emitted |
| `InsertOperator` | Serializes each row via `TupleSerializer`, inserts into `HeapFile` pages |
| `UpdateOperator` | Scans with `FilterOperator`, writes updated tuples back (delete old slot + insert new) |
| `DeleteOperator` | Scans with `FilterOperator`, soft-deletes matching tuples (marks slot deleted) |

### 6. Executor — Plan Tree Walker

Walks the plan tree and produces `Iterator<Row>` + metadata as `ResultSet`.

```java
interface Executor {
    ResultSet execute(PlanNode plan);
}
```

For DML (INSERT/UPDATE/DELETE) → execute the plan, return affected row count.
For DDL (CREATE/DROP TABLE) → modify catalog, return success.
For SELECT → build operator tree, pull rows, collect into `ResultSet`.

### 7. Row + ResultSet

```java
class Row {
    Map<String, Object> values;  // column name → value
    Object get(String column);
}

class ResultSet {
    List<String> columns;        // column names in order
    List<Row> rows;
    int affectedRows;            // for INSERT/UPDATE/DELETE
    String statementType;        // "SELECT", "INSERT", etc.
    long elapsedMs;
}
```

### 8. SqlEngine — Facade

The single entry point. Wires lexer → parser → binder → planner → executor:

```java
SqlEngine engine = new SqlEngine(catalog, bufferPool);

ResultSet rs = engine.execute("SELECT * FROM users WHERE age > 25");
System.out.println(rs.getColumns());   // [id, name, age]
System.out.println(rs.getRows());      // [{id=2, name=Alice, age=30}, ...]
```

Internal flow:
```java
List<Token> tokens = lexer.tokenize(sql);
Statement stmt = parser.parse(tokens);
Statement resolvedStmt = binder.bind(stmt, catalog);
PlanNode plan = planner.plan(resolvedStmt);
ResultSet result = executor.execute(plan);
return result;
```
