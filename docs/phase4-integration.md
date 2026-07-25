# Phase 4: Integration & Polish

> **Status**: 🔜 Pending | **Depends on**: Phase 3 (HTTP server)

## Goal

Test the full system end-to-end. Add error handling. Add `SHOW TABLES` and `DESCRIBE TABLE` commands as SQL statements (in addition to the REST API equivalents).

## Steps

### 1. End-to-End Tests

Test each DML command through the full pipeline (HTTP → SQL engine → storage → back):

```sql
-- DDL
CREATE TABLE users (id INTEGER, name TEXT, age INTEGER);
CREATE TABLE orders (id INTEGER, user_id INTEGER, amount FLOAT);

-- INSERT
INSERT INTO users VALUES (1, 'Alice', 30);
INSERT INTO users VALUES (2, 'Bob', 25);
INSERT INTO users VALUES (3, 'Carol', 35);

-- Basic SELECT
SELECT * FROM users;
SELECT id, name FROM users;

-- SELECT with WHERE
SELECT * FROM users WHERE age > 25;
SELECT * FROM users WHERE name = 'Alice';
SELECT * FROM users WHERE age >= 30 AND name != 'Bob';

-- SELECT with ORDER BY
SELECT * FROM users ORDER BY age;
SELECT * FROM users ORDER BY age DESC;
SELECT * FROM users ORDER BY name ASC;

-- SELECT with LIMIT
SELECT * FROM users LIMIT 2;
SELECT * FROM users ORDER BY age DESC LIMIT 1;

-- UPDATE
UPDATE users SET age = 31 WHERE id = 1;
UPDATE users SET age = 26, name = 'Bobby' WHERE name = 'Bob';

-- DELETE
DELETE FROM users WHERE age < 28;
DELETE FROM users WHERE name = 'Carol';

-- DROP TABLE
DROP TABLE orders;
DROP TABLE IF EXISTS nonexistent;   -- should not error

-- Edge cases
SELECT * FROM nonexistent;           -- error: table not found
SELECT nonexistent_column FROM users; -- error: column not found
INSERT INTO users VALUES (1);        -- error: column count mismatch
INSERT INTO users VALUES ('x', 1);   -- error: type mismatch
```

### 2. Error Handling

All errors go through `SqlException` (checked exception, caught in HTTP handler → 400 JSON response).

| Error | Example | Message |
|-------|---------|---------|
| Syntax error | `SELEC * FROM x` | `Syntax error at line 1: unexpected token 'SELEC'` |
| Unknown table | `SELECT * FROM foo` | `Table 'foo' does not exist` |
| Unknown column | `SELECT bar FROM users` | `Column 'bar' not found in table 'users'` |
| Type mismatch INSERT | `INSERT INTO users VALUES ('x', 1)` | `Column 'id' expects INTEGER, got TEXT` |
| Type mismatch WHERE | `WHERE name > 25` | `Cannot compare TEXT with INTEGER` |
| Column count INSERT | `INSERT INTO users VALUES (1)` | `Expected 3 values, got 1` |
| Ambiguous column | (future, with JOINs) | `Column 'id' is ambiguous` |
| Row too large | Any INSERT with row > ~8KB | `Row too large: 9234 bytes exceeds max tuple size` |

> **Known limitation**: A single row larger than ~8161 bytes cannot fit in an 8KB page. See [phase1-storage.md](phase1-storage.md) Step 4 for details and the PostgreSQL TOAST comparison. For now, oversized rows are rejected with a clear error.

### 3. SHOW TABLES Command

Add as a SQL statement alongside the REST API:

```sql
SHOW TABLES;
→
┌─────────┐
│ TABLE   │
├─────────┤
│ users   │
│ orders  │
└─────────┘
```

Implemented as a special AST node `ShowTablesStmt` → executor returns catalog table names directly. No storage access needed.

### 4. DESCRIBE TABLE Command

```sql
DESCRIBE users;
→
┌────────┬─────────┐
│ COLUMN │ TYPE    │
├────────┼─────────┤
│ id     │ INTEGER │
│ name   │ TEXT    │
│ age    │ INTEGER │
└────────┴─────────┘
```

Also supports `DESC users` as alias. Reads from `TableMetadata` in catalog — no storage access.

### 5. Additional Polish

- **Server startup banner:** `miniSQL v0.1.0 — http://localhost:8080`
- **Graceful shutdown:** catch SIGINT, flush buffer pool, close catalog
- **Request logging:** `[2026-07-24 14:32:05] POST /api/query — SELECT — 2ms`
- **SQL file encoding:** support semicolons between statements, ignore empty statements
- **String escaping:** `'it''s'` → `it's`, `'hello\nworld'` → literal newline

## Verification

Run all SQL statements from Step 1 via `curl` and the Web UI. Verify:

1. Every query returns correct results
2. Errors return JSON with `"success": false` and a useful message
3. `data/mydb/catalog.json` reflects CREATE/DROP TABLE
4. `data/mydb/users.dat` exists and contains proper 8KB pages
5. Server restart preserves all data (catalog loaded from JSON, heap files intact)
