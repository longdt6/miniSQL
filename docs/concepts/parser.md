# Parser (Recursive Descent)

Converts a flat token stream into a tree structure (AST). Enforces SQL grammar and operator precedence.

## What a Parser Does

```
Tokens:  [SELECT] [IDENT:name] [FROM] [IDENT:users]
         [WHERE] [IDENT:age] [GT] [INT:25]

AST:
  SelectStmt {
    columns: [ColumnRef("name")],
    table: "users",
    where: BinaryExpression(
      left: ColumnRef("age"),
      operator: GT,
      right: LiteralValue(25)
    )
  }
```

The parser takes the flat sequence of tokens from the lexer and builds a **tree** that represents the query's structure. This tree is called an **AST** (Abstract Syntax Tree).

## Recursive Descent Parsing

Each grammar rule becomes **one method**. Each method calls other methods for sub-rules. The structure of the code mirrors the structure of the grammar.

```
Grammar rule:  select_stmt → SELECT columns FROM table [WHERE expr] [ORDER BY ...] [LIMIT int]

Method:
  SelectStmt parseSelect() {
      consume(SELECT);                  // expect SELECT keyword
      List<Expression> columns = parseColumnList();  // parse column list
      consume(FROM);                    // expect FROM keyword
      String table = parseTableName();  // parse table identifier
      Expression where = null;
      if (match(WHERE)) {               // optional WHERE
          where = parseExpression();    // parse WHERE expression
      }
      // ... ORDER BY, LIMIT ...
      return new SelectStmt(columns, table, where, ...);
  }
```

This is why it's called **recursive descent**: the parser methods recursively descend into sub-expressions. `parseExpression()` calls `parseTerm()`, which calls `parseFactor()`, which might call `parseExpression()` again for parenthesized expressions.

## The SQL Grammar (Subset)

```
statement     → select_stmt
               | insert_stmt
               | update_stmt
               | delete_stmt
               | create_table_stmt
               | drop_table_stmt

select_stmt   → SELECT column_list FROM table_name
                [WHERE expression]
                [ORDER BY order_list]
                [LIMIT int_literal]

insert_stmt   → INSERT INTO table_name [(col_list)] VALUES (value_list)
                [, (value_list), ...]

update_stmt   → UPDATE table_name SET col = value [, col = value, ...]
                [WHERE expression]

delete_stmt   → DELETE FROM table_name [WHERE expression]

create_table  → CREATE TABLE table_name (col_def [, col_def, ...])

drop_table    → DROP TABLE [IF EXISTS] table_name

column_list   → STAR | expression [, expression, ...]

expression    → term (AND term | OR term)*         ← lowest precedence

term          → operand (EQ | NEQ | LT | GT | LTE | GTE operand)*

operand       → literal | column_ref | LPAREN expression RPAREN
                | NOT operand

literal       → INT_LITERAL | FLOAT_LITERAL | STRING_LITERAL
                | TRUE | FALSE | NULL
```

## Operator Precedence

This is the trickiest part. Consider:

```sql
WHERE age > 25 AND name = 'Alice' OR city = 'Paris'
```

Without precedence, this is ambiguous. Is it `(age > 25 AND name = 'Alice') OR city = 'Paris'` or `age > 25 AND (name = 'Alice' OR city = 'Paris')`?

SQL says `AND` binds tighter than `OR`:

```
WHERE (age > 25 AND name = 'Alice') OR city = 'Paris'
```

Precedence is encoded by **which method calls which**:

```
parseExpression()       → handles OR (lowest precedence)
  └── parseAnd()        → handles AND
      └── parseComparison()  → handles = != < > <= >=
          └── parseUnary()   → handles NOT, -
              └── parsePrimary() → literals, column refs, ( expr )
```

Each level calls the next higher-precedence level for its operands. This is standard recursive descent technique — the same pattern used by every hand-written parser for any language.

## How It Works Step by Step

### Statement dispatch
```java
Statement parseStatement() {
    Token token = peek();
    return switch (token.type()) {
        case SELECT  -> parseSelect();
        case INSERT  -> parseInsert();
        case UPDATE  -> parseUpdate();
        case DELETE  -> parseDelete();
        case CREATE  -> parseCreate();
        case DROP    -> parseDrop();
        default -> throw error("Unexpected token: " + token.type());
    };
}
```

The first token determines which statement type to parse. This is called **predictive parsing** — one token of lookahead tells you which rule to apply.

### Match and consume
```java
void consume(TokenType expected) {
    if (peek().type() == expected) {
        advance();  // move to next token
    } else {
        throw error("Expected " + expected + " but got " + peek().type());
    }
}

boolean match(TokenType type) {
    if (peek().type() == type) {
        advance();
        return true;
    }
    return false;
}
```

`consume` enforces a required token ("FROM must follow the column list").  
`match` optionally consumes a token if present ("WHERE is optional").

### Expression parsing (precedence climbing)
```java
Expression parseExpression() {
    Expression left = parseAnd();
    while (match(OR)) {
        Token op = previous();   // the OR token we just consumed
        Expression right = parseAnd();
        left = new BinaryExpression(left, op.type(), right);
    }
    return left;
}

Expression parseAnd() {
    Expression left = parseComparison();
    while (match(AND)) {
        Token op = previous();
        Expression right = parseComparison();
        left = new BinaryExpression(left, op.type(), right);
    }
    return left;
}
```

## AST Nodes

The parser produces instances of these classes:

```
Statement (interface)
├── SelectStmt      { columns: List<Expression>, table: String,
│                     where: Expression, orderBy: List<OrderBy>, limit: int }
├── InsertStmt      { table: String, columns: List<String>,
│                     values: List<List<Expression>> }
├── UpdateStmt      { table: String, sets: List<Assignment>,
│                     where: Expression }
├── DeleteStmt      { table: String, where: Expression }
├── CreateTableStmt { table: String, ifNotExists: boolean,
│                     columns: List<ColumnDef> }
└── DropTableStmt   { table: String, ifExists: boolean }

Expression (interface)
├── ColumnRef       { name: String }
├── LiteralValue    { value: Object, type: TokenType }
├── BinaryExpression { left: Expression, op: TokenType, right: Expression }
└── Star             { } // SELECT *
```

## What the Parser Does NOT Do

- Does not check if `users` is a real table name
- Does not check if `name` is a real column in that table
- Does not check types (can't compare `age > 'hello'`)
- Does not expand `SELECT *` to actual column names

All of that is the **binder's** job. The parser only checks **syntax** — is this valid SQL structure? The binder checks **semantics** — does this query make sense?

## Example: Full Parse Trace

```
SQL:    "SELECT name, age FROM users WHERE age > 25 ORDER BY name LIMIT 10"

parseStatement():
  peek() = SELECT → parseSelect()
    consume(SELECT)
    parseColumnList():
      parseExpression() → ColumnRef("name")
      consume(COMMA)
      parseExpression() → ColumnRef("age")
      → [ColumnRef("name"), ColumnRef("age")]
    consume(FROM)
    table = parseIdentifier() → "users"
    match(WHERE) → yes
      parseExpression():
        parseAnd():
          parseComparison():
            left = ColumnRef("age")
            consume(GT)
            right = LiteralValue(25)
            → BinaryExpr(ColumnRef("age"), GT, LiteralValue(25))
    match(ORDER) → yes
      consume(BY)
      parseOrderList():
        parseIdentifier() → "name"
        (no ASC/DESC → default ASC)
        → [OrderBy("name", ASC)]
    match(LIMIT) → yes
      limit = parseInt() → 10

Result: SelectStmt {
  columns: [ColumnRef("name"), ColumnRef("age")],
  table: "users",
  where: BinaryExpr(ColumnRef("age"), GT, LiteralValue(25)),
  orderBy: [OrderBy("name", ASC)],
  limit: 10
}
```

---

→ Previous: [Lexer](lexer.md)
→ Next: [Binder](binder.md)
→ Related: [SQL Query Lifecycle](query-lifecycle.md)
