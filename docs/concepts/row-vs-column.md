# Row-Oriented vs Column-Oriented Storage

Why PostgreSQL/MySQL store rows together. Why ClickHouse/Redshift store columns separately.

## Row-Oriented (OLTP)

Every column of a row is stored together as one contiguous chunk.

```
Page on disk:
┌─────────────────────────────────────────────────────┐
│ [id=1, name="Alice", age=30, email="alice@..."]    │
│ [id=2, name="Bob",   age=25, email="bob@..."]      │
│ [id=3, name="Carol", age=35, email="carol@..."]    │
│ [id=4, name="Dave",  age=28, email="dave@..."]     │
└─────────────────────────────────────────────────────┘
```

## Column-Oriented (OLAP)

Each column is stored separately across pages.

```
Page 1 (id):     [1, 2, 3, 4, ...]
Page 2 (name):   ["Alice", "Bob", "Carol", "Dave", ...]
Page 3 (age):    [30, 25, 35, 28, ...]
Page 4 (email):  ["alice@...", "bob@...", "carol@...", ...]
```

## The Trade-off

| Operation | Row-Oriented | Column-Oriented |
|-----------|-------------|-----------------|
| `SELECT * FROM users WHERE id = 42` | Read 1 row, all columns in one disk read. **Fast.** | Read 4 separate column files, stitch together. **Slow.** |
| `INSERT INTO users VALUES (...)` | Write 1 contiguous chunk. **Fast.** | Write to N separate files, sync all. **Slow.** |
| `UPDATE users SET age = 31 WHERE id = 1` | Find 1 row, overwrite in place. **Fast.** | Update 1 column file. **Slower but ok.** |
| `SELECT AVG(age) FROM users` | Read all rows (including name, email you don't need), extract age. **Wasteful.** | Read just the age column file, 1/4 the I/O. **Fast.** |
| `SELECT COUNT(*) WHERE status = 'active'` | Scan whole table including all unused columns. **Slow.** | Scan 1 column, count matches. **Fast.** |

## Which Database Uses Which

### Row-Oriented (OLTP)
```
PostgreSQL, MySQL (InnoDB), SQL Server, Oracle, SQLite
miniSQL ← we are here
```
Optimized for **transactions**: frequent reads/writes of whole rows, CRUD operations, small queries.

### Column-Oriented (OLAP)
```
ClickHouse, Redshift, Snowflake, Vertica, DuckDB, Apache Parquet
```
Optimized for **analytics**: scanning millions of rows but only a few columns, aggregations, GROUP BY.

## Why miniSQL Is Row-Oriented

We're building an OLTP-style system:
- `INSERT INTO users VALUES (...)` — write whole row
- `SELECT * FROM users WHERE id = X` — read whole row
- `UPDATE users SET ... WHERE id = X` — update whole row
- `DELETE FROM users WHERE id = X` — delete whole row

All of these work on **entire rows**. Row-oriented storage makes each operation a single page access. Column-oriented would scatter the data across multiple column files — slower for every operation we care about.

→ Previous: [Tuple Serialization](tuple-serialization.md)
→ Next: [B-tree & Fanout](btree.md)
