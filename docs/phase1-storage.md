# Phase 1: Storage Engine Foundation

> **Status**: 🔜 Pending | **Depends on**: Maven project skeleton

## Goal

Build the bottom of the architecture stack — the storage engine. When done, you can write a Java program that opens a table, inserts rows as binary tuples into 8KB slotted pages on disk, reads them back via a buffer pool, and manages schema in a JSON catalog. No SQL yet — that comes in Phase 2.

## Steps

### 1. Maven Project Skeleton

```
miniSQL/
├── pom.xml                          # Java 17+, no external deps
├── src/main/java/com/minisql/...    # All source packages
├── src/main/resources/web/          # Web UI files (Phase 3)
├── src/test/java/com/minisql/...    # Unit tests
└── data/mydb/                       # Runtime database files
```

### 2. DataType Hierarchy

Each SQL type handles its own byte-level encoding, decoding, comparison, and parsing.

```
DataType (interface)
├── IntegerType    → 4 bytes, big-endian int32
├── FloatType      → 8 bytes, IEEE 754 double
├── TextType       → 2B length prefix + UTF-8 bytes (variable)
└── BooleanType    → 1 byte (0 or 1)
```

**Methods per type:**
- `byte[] encode(Object value)` — in-memory value → bytes
- `Object decode(byte[] data, int offset, int length)` — bytes → in-memory value
- `int compare(Object a, Object b)` — for WHERE clause evaluation
- `int getSize()` — fixed size in bytes, or `-1` for variable-length (TEXT)
- `Object parse(String literal)` — `"42"` → `42`, `'hello'` → `"hello"`, `TRUE` → `true`
- `String getSqlName()` — `"INTEGER"`, `"TEXT"`, `"FLOAT"`, `"BOOLEAN"`

**Static factory:** `DataType.fromSqlName("INTEGER")` → `IntegerType` instance. Used by the parser when processing `CREATE TABLE`.

### 3. TupleSerializer — Row ↔ byte[]

Converts between in-memory `Row` objects and the on-disk binary tuple format.

```java
byte[] bytes = TupleSerializer.serialize(row, tableDesc);
Row row = TupleSerializer.deserialize(bytes, tableDesc);
```

**Tuple binary format:**

```
┌─ 4B header ────────────────────────────┐
│  flags: 1B (bit 0 = deleted)           │
│  columnCount: 1B                       │
│  tupleLength: 2B (total bytes)         │
├─ ceil(cols/8)B ────────────────────────┤
│  Null bitmap (1 bit per column)        │
├─ 2B per column ────────────────────────┤
│  Column offsets (offset from byte 0)   │
├─ Variable length ──────────────────────┤
│  Column data:                          │
│    INT:    4 bytes big-endian          │
│    FLOAT:  8 bytes IEEE 754            │
│    TEXT:   2B length + UTF-8 bytes     │
│    BOOL:   1 byte (0 or 1)             │
└────────────────────────────────────────┘
```

**Key design:** column offsets let us read individual columns without deserializing the entire row. A `SELECT name` query only touches the `name` bytes — no need to decode `id` and `age`.

### 4. Page — 8KB Slotted-Page Layout

One `Page` = 8192 bytes. Tuples grow upward from the bottom; the slot directory grows downward from the header. When they meet, the page is full.

```
┌───────────────────────────────────────┐ byte 0
│  Page Header (24 bytes)               │
│    pageNumber (4B), numSlots (2B),    │
│    freeSpaceOffset (2B),              │
│    freeSpaceEnd (2B),                 │
│    slotDirectoryOffset (2B),          │
│    nextPageId (4B), checksum (4B)     │
├───────────────────────────────────────┤ byte 24
│  Slot Directory (grows ↓)             │
│    [slot 0: offset, flags, length]    │
│    [slot 1: offset, flags, length]    │
│    ... (6 bytes per slot)             │
├~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~┤
│         FREE SPACE                    │
├~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~┤
│  Tuple Data (grows ↑)                 │
│    [tuple 1 bytes ................]   │
│    [tuple 0 bytes ................]   │
└───────────────────────────────────────┘ byte 8191
```

**Operations:**
- `int insert(byte[] tuple)` → assign slot, write tuple at `freeSpaceEnd`, return slot number
- `byte[] getTuple(int slot)` → read tuple at slot's offset
- `void deleteTuple(int slot)` → mark slot as deleted (flag bit), don't shift bytes
- `void updateTuple(int slot, byte[] newTuple)` → if same size overwrite; else delete old + insert new
- `int getFreeSpace()` → bytes still available
- `void compact()` → remove deleted tuples, shift remaining, reclaim space

Soft-deletes + lazy compaction = same strategy PostgreSQL uses with VACUUM.

> **Limitation: Oversized rows.** A row larger than ~8161 bytes cannot fit in any single 8KB page and will be rejected. PostgreSQL solves this with TOAST (The Oversized-Attribute Storage Technique): large values are split into chunks across separate overflow pages, and the main row stores a pointer instead. For now, we keep it simple — TEXT columns exceeding 8KB are a rare edge case for a learning project. Add overflow page support later if needed.

### 5. HeapFile — Per-Table File I/O

One `HeapFile` per table (`data/mydb/users.dat`). Manages a sequence of 8KB pages on disk using `RandomAccessFile`.

```java
HeapFile hf = new HeapFile("data/mydb/users.dat");

Page p = hf.readPage(3);         // read page at offset 3 * 8192
p.insert(tupleBytes);
hf.writePage(3, p);              // write back

Page newPage = hf.appendPage();   // extend file by one page

// Iterate all live tuples
for (int i = 0; i < hf.getNumPages(); i++) {
    for (byte[] tuple : hf.readPage(i).getLiveTuples()) { ... }
}
```

### 6. BufferPool — LRU Page Cache

A single cache for all pages across all tables. Avoids reading from disk on every access.

```java
BufferPool pool = new BufferPool(100);  // max 100 pages

Page p = pool.getPage(new PageId("users.dat", 0));  // read if not cached
// ... second call to getPage with same PageId hits cache, no disk I/O

pool.flushAll();  // write all dirty pages (shutdown)
```

**Internals:** `LinkedHashMap<PageId, Page>` with access-order eviction. On `getPage()`, move to MRU. If cache is full, evict LRU (flushing dirty pages first).

### 7. Catalog — Schema Metadata Registry

In-memory registry of all tables and their schemas. Persisted to `data/mydb/catalog.json`.

**Classes:**
- `ColumnMetadata { name, DataType, position }`
- `TableMetadata { id, name, List<ColumnMetadata>, heapFilePath }`
- `Catalog { Map<String, TableMetadata> tables }`

**Persistence:** on startup, load JSON. On `CREATE TABLE` / `DROP TABLE`, rewrite entire JSON file. Keep-it-simple approach — production databases use system tables, not flat JSON.

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
    }
  ]
}
```

## How They Connect

Phase 1 end state — you can do this without SQL:

```java
Catalog catalog = new Catalog("data/mydb");
BufferPool pool = new BufferPool(100);
TableMetadata users = catalog.getTable("users");

// Insert a row
Map<String, Object> row = Map.of("id", 1, "name", "Alice", "age", 30);
byte[] tuple = TupleSerializer.serialize(new Row(row), users);

HeapFile hf = new HeapFile(users.getHeapFilePath());
Page lastPage = pool.getPage(new PageId(hf.getPath(), hf.getNumPages() - 1));
if (!lastPage.hasSpace(tuple.length)) {
    lastPage = pool.getPage(new PageId(hf.getPath(), hf.appendPage().getPageNum()));
}
lastPage.insert(tuple);
pool.flushAll();

// Scan all rows
for (Page page : allPages) {
    for (byte[] t : page.getLiveTuples()) {
        Row r = TupleSerializer.deserialize(t, users);
        System.out.println(r);
    }
}
```

No SQL. Just a working storage engine. Phase 2 builds the SQL engine on top.
