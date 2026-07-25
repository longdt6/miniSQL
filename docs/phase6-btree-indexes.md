# Phase 6 (Future): B+tree Indexes

> **Status**: 📅 Future | **Decision**: [2026-07-24] Phase 1 uses heap files with linear table scans (O(n)). B+trees come later as an index layer on top of the same storage engine.

## Why B+tree

Matches PostgreSQL, MySQL InnoDB, and SQLite:

| Database | Index type |
|----------|-----------|
| PostgreSQL | B+tree (default) |
| MySQL InnoDB | B+tree (clustered PK) |
| SQLite | B+tree (tables + indexes) |
| **miniSQL** | B+tree |

## B+tree Structure

```
Internal nodes (guide keys only):
          [30 | 60]
         /    |    \
        /     |     \
Leaf nodes (actual data, linked in order):
  [5|10|20] → [30|40|50] → [60|70|80]
  │ keys       │ keys       │ keys
  │ + values   │ + values   │ + values
  └─ linked ───┴─ linked ───┘
```

- **All data lives in leaf nodes** — internal nodes are guides only
- **Leaves are linked** — makes range scans (`BETWEEN`, `ORDER BY`, `>`, `<`) fast
- **High fanout** — hundreds of keys per 8KB page → shallow tree → few disk reads
- **Reuses `Page` and `BufferPool`** — same 8KB page infrastructure

## Two Index Types

### 1. Primary Key Index (Clustered)

Tuples live inside B+tree leaves, physically ordered by primary key (like InnoDB).

```sql
CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, age INTEGER);
```

```
B+tree on id:
      [100 | 200]
     /     |      \
Leaf pages with actual tuples:
Page 1:  id=1, name="Alice"  →  id=42, name="Bob"  →  ...
Page 2:  id=100, ... → id=150, ...
Page 3:  id=200, ... → id=250, ...
```

`SELECT * FROM users WHERE id = 42` → walk B+tree in O(log n), read 1 leaf page. No more O(n) table scan.

### 2. Secondary Index

B+tree on non-PK columns. Leaf stores the primary key value, not the full row.

```sql
CREATE INDEX idx_users_age ON users (age);
```

```
B+tree on age:
Leaf: age=25 → [id=2, id=7]     (multiple users can be 25)
      age=30 → [id=1]
      age=35 → [id=3, id=9]
```

`SELECT * FROM users WHERE age = 30` → find leaf page for age=30, get id=1, then look up id=1 in the primary key B+tree to get the full row. Two B+tree lookups, still O(log n).

## B+tree Operations

### Search
```
find(key):
  node = root
  while node is internal:
    find child pointer for key range
    node = bufferPool.getPage(childPageId)
  scan leaf for exact key
  return value (tuple or PK pointer)
```

### Insert
```
insert(key, value):
  find leaf page where key belongs
  if leaf has space:
    insert key+value in sorted position
  else:
    split leaf into two pages:
      half the entries stay, half move to new page
      promote middle key to parent
    if parent is full → split parent too (cascade up)
```

### Delete
```
delete(key):
  find leaf page
  mark entry deleted (soft delete, lazy compaction)
  if leaf is < 50% full:
    try to borrow from sibling, or merge with sibling
    update parent separator key
```

## SQL Syntax (Future)

```sql
-- Explicit primary key
CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, age INTEGER);

-- Secondary index
CREATE INDEX idx_users_name ON users (name);
CREATE INDEX idx_users_age_name ON users (age, name);  -- composite

-- Unique constraint (backed by B+tree)
CREATE TABLE users (id INTEGER UNIQUE, name TEXT);

-- Drop index
DROP INDEX idx_users_name;
```

## Planner Changes

With indexes, the planner chooses between:

```
Query: SELECT * FROM users WHERE age = 30

Option A (no index):               Option B (index on age):
  TableScan(users)                   IndexScan(idx_users_age, key=30)
    └─ Filter(age = 30)                └─ PKLookup(users)
       ↑ O(n) scan                       ↑ O(log n) + 1 lookup
```

The planner picks the index when the WHERE clause matches an indexed column with `=` or range comparisons.

## What Doesn't Change

- **SQL engine** — lexer/parser/binder/executor are unchanged. The binder resolves `CREATE INDEX`, the planner adds `IndexScanPlan`, the executor gets an `IndexScanOperator`.
- **Page infrastructure** — B+tree nodes are just `Page` objects with a different internal layout (key-pointers instead of tuples). Same `BufferPool`, same `HeapFile` (renamed to `IndexFile` or stored in `HeapFile` with a different suffix).
- **Catalog** — same JSON catalog, with an additional `"indexes": [...]` array in `catalog.json`.

## Verification

```sql
CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, age INTEGER);
CREATE INDEX idx_users_age ON users (age);

INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25), (3, 'Carol', 35);

-- These should use the index (fast, O(log n)):
SELECT * FROM users WHERE id = 2;
SELECT * FROM users WHERE age = 30;
SELECT * FROM users WHERE age > 25;

-- This still does a full scan:
SELECT * FROM users WHERE name = 'Alice';
-- (until we create an index on name)
```
