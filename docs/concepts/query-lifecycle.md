# SQL Query Lifecycle

The full path from a SQL string to result rows. Every database runs this pipeline.

## The Five Stages

```
SQL String
   │
   ▼
┌─────────┐
│  LEXER  │  "SELECT name FROM users WHERE age > 25"
└────┬────┘
     │  List<Token>
     ▼
┌─────────┐
│ PARSER  │  Recursive-descent → AST
└────┬────┘
     │  SelectStmt { columns, table, where, ... }
     ▼
┌─────────┐
│ BINDER  │  Semantic analysis: resolve names, type-check
└────┬────┘
     │  Resolved SelectStmt (columns annotated with types)
     ▼
┌─────────┐
│ PLANNER │  AST → execution plan tree
└────┬────┘
     │  Limit → Sort → Project → Filter → TableScan
     ▼
┌─────────┐
│EXECUTOR │  Execute plan, return rows
└────┬────┘
     │  ResultSet { columns, rows }
     ▼
   JSON/HTTP response
```

## Stage 1: Lexer

Converts a string into tokens — the smallest meaningful units.

```
Input:  "SELECT name FROM users WHERE age > 25"

Output: [SELECT] [IDENTIFIER:name] [FROM] [IDENTIFIER:users]
        [WHERE] [IDENTIFIER:age] [GREATER_THAN] [NUMBER:25]
```

Keywords (`SELECT`, `FROM`, `WHERE`), identifiers (`name`, `users`, `age`), operators (`>`), literals (`25`). Whitespace and comments are consumed and discarded.

## Stage 2: Parser

Converts the flat token list into a tree structure (AST — Abstract Syntax Tree).

```
SelectStmt {
  columns: [ColumnRef("name")]
  table: "users"
  where: BinaryExpression(
    left: ColumnRef("age"),
    operator: GREATER_THAN,
    right: LiteralValue(25)
  )
}
```

The parser enforces grammar: `SELECT` must be followed by columns, then `FROM`, then a table name, optionally `WHERE`, etc. Syntax errors are caught here.

**We use recursive-descent parsing**, not a parser generator like ANTLR. Each grammar rule (`select_stmt`, `insert_stmt`, `expr`) is a method that calls other methods.

## Stage 3: Binder

Semantic analysis — does the query actually make sense?

```
What the binder checks:
- Table "users" → exists in catalog? Yes → get its TableMetadata
- Column "name" → exists in users table? Yes → annotate with type TEXT
- Column "age" → exists in users table? Yes → annotate with type INTEGER
- WHERE age > 25 → comparing INTEGER to INTEGER? Yes → OK
- WHERE age > 'hello' → comparing INTEGER to TEXT? No → ERROR
```

The binder annotates the AST with type information and column positions. After binding, every `ColumnRef` knows its `DataType` and its column index in the table.

## Stage 4: Planner

Converts the resolved AST into an execution plan — a tree of operators.

```
Input:  SELECT name, age FROM users WHERE age > 25 ORDER BY name LIMIT 10

Output:
  LimitNode(10)
    └── SortNode(name, ASC)
        └── ProjectNode([name, age])
            └── FilterNode(age > 25)
                └── TableScanNode(users)
```

In Phase 1, the plan is straightforward — one operator per clause. In a real database, the planner is the most complex component: it decides which indexes to use, which join algorithm, whether to reorder operations for efficiency.

## Stage 5: Executor

Walks the plan tree and returns `ResultSet`:

```java
ResultSet execute(PlanNode plan) {
    Operator root = buildOperatorTree(plan);
    root.open();
    List<Row> rows = new ArrayList<>();
    Row row;
    while ((row = root.next()) != null) {
        rows.add(row);
    }
    root.close();
    return new ResultSet(root.getOutputSchema(), rows);
}
```

For DDL (CREATE/DROP TABLE), the executor modifies the catalog directly. For DML (INSERT/UPDATE/DELETE), it runs the operator tree and returns affected row count.

## Example: `SELECT 1`

No table access, no storage. Still goes through all five stages:

```
Lexer:   [SELECT] [NUMBER:1]
Parser:  SelectStmt { columns: [LiteralValue(1)], table: null }
Binder:  Nothing to resolve — literal, no table
Planner: ProjectNode([LiteralValue(1)])
           └── SingleRowNode  (emits one empty row)
Executor: SingleRow → Project → ResultSet
Result:  {"columns":["1"], "rows":[[1]]}
```

→ Previous: [Volcano Iterator Model](volcano-model.md)
→ Next: [Wire Protocols](wire-protocols.md)
