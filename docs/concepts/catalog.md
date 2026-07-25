# Catalog

The database's schema brain. Tracks what tables exist, what columns they have, and where their data files live.

## What the Catalog Tracks

```java
Catalog
  └── Map<String, TableMetadata> tables    // table name → schema

TableMetadata
  ├── id: int                             // unique ID for this table
  ├── name: String                         // "users"
  ├── heapFilePath: String                 // "data/mydb/users.dat"
  └── columns: List<ColumnMetadata>

ColumnMetadata
  ├── name: String                         // "age"
  ├── dataType: DataType                   // IntegerType
  └── position: int                        // 0, 1, 2 (order in CREATE TABLE)
```

## Two Kinds of Operations

### DDL (Data Definition Language) — change the catalog

```
CREATE TABLE users (id INTEGER, name TEXT, age INTEGER);
  → Catalog.addTable("users", [Column(id, INTEGER, 0), ...])
  → Writes catalog.json to disk
  → Creates empty users.dat heap file

DROP TABLE users;
  → Catalog.removeTable("users")
  → Writes catalog.json to disk
  → (Optionally) deletes users.dat
```

DDL operations modify the catalog and the set of heap files. They're infrequent.

### DML (Data Manipulation Language) — read the catalog, don't modify it

```
SELECT * FROM users WHERE age > 25;
  → Catalog.getTable("users") → TableMetadata
  → TableMetadata.getColumns() → [id:INTEGER, name:TEXT, age:INTEGER]
  → Binder uses this to resolve column references
  → TableScan uses heapFilePath to open the right file
```

DML uses the catalog to understand what to read. It never changes the catalog.

## How It's Persisted: catalog.json

```json
{
  "tables": [
    {
      "id": 1,
      "name": "users",
      "heapFile": "data/mydb/users.dat",
      "columns": [
        {"name": "id", "type": "INTEGER", "position": 0},
        {"name": "name", "type": "TEXT", "position": 1},
        {"name": "age", "type": "INTEGER", "position": 2}
      ]
    },
    {
      "id": 2,
      "name": "orders",
      "heapFile": "data/mydb/orders.dat",
      "columns": [
        {"name": "id", "type": "INTEGER", "position": 0},
        {"name": "user_id", "type": "INTEGER", "position": 1},
        {"name": "amount", "type": "FLOAT", "position": 2}
      ]
    }
  ]
}
```

**On startup:** load JSON into memory. Parse type strings via `DataType.fromSqlName()`.
**On DDL change:** rewrite the entire JSON file. Simple read-modify-write.

## Why JSON (Not System Tables)

Real databases store catalog metadata in **system tables** — special hidden tables that live in the storage engine itself:

```sql
-- PostgreSQL system catalogs:
SELECT * FROM pg_class WHERE relname = 'users';     -- table info
SELECT * FROM pg_attribute WHERE attrelid = ...;     -- column info
SELECT * FROM pg_type WHERE typname = 'int4';        -- type info
```

This is elegant (the catalog is just another table — same storage, same access patterns) but complex to bootstrap. At startup, you need the catalog to know where the catalog tables are stored. Circular dependency. PostgreSQL solves this with hardcoded OIDs and a bootstrap sequence.

For a learning project, JSON is the right call:
- Human-readable — you can inspect the schema with a text editor
- Simple to load/save — standard library, ~20 lines of code
- Easy to version — works with git

Production databases store metadata in system tables; we store it in JSON. Same information, different persistence layer.

## In-Memory Structure

The catalog is loaded once at startup and kept entirely in memory:

```java
class Catalog {
    private final Map<String, TableMetadata> tables = new HashMap<>();
    private final Path basePath;  // "data/mydb"

    Catalog(String basePath) {
        this.basePath = Path.of(basePath);
        load();  // read catalog.json → populate tables map
    }

    void addTable(TableMetadata meta) {
        tables.put(meta.getName(), meta);
        save();  // rewrite catalog.json
    }

    void removeTable(String name) {
        TableMetadata meta = tables.remove(name);
        if (meta == null) throw new SqlException("Table '" + name + "' not found");
        save();
    }

    TableMetadata getTable(String name) {
        TableMetadata meta = tables.get(name);
        if (meta == null) throw new SqlException("Table '" + name + "' not found");
        return meta;
    }

    List<String> getTableNames() {
        return new ArrayList<>(tables.keySet());
    }
}
```

Fast (hash map lookup), simple (no queries needed), and the entire schema fits in RAM (even a database with 10,000 tables takes kilobytes of metadata).

## Catalog + HeapFile + BufferPool: The Complete Picture

```
DDL: CREATE TABLE users (...)
  ↓
Catalog.addTable("users", columns)
  ├──→ Writes users entry to catalog.json
  └──→ Creates users.dat (empty heap file)

DML: SELECT * FROM users
  ↓
Catalog.getTable("users") → TableMetadata
  ↓
TableMetadata.getHeapFilePath() → "data/mydb/users.dat"
  ↓
BufferPool.getPage(new PageId("users.dat", 0)) → Page
  ↓
Page.getTuple(0) → byte[] → TupleSerializer.deserialize() → Row
```

The catalog is the glue: it connects a table name in SQL to the physical file on disk.

---

→ Related: [Heap File](heap-file.md) — the physical file the catalog references
→ Related: [DataType System](datatype.md) — what ColumnMetadata.dataType refers to
