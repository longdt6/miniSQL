# Row

The in-memory representation of a database row. A bridge between Java objects and disk bytes.

## What Is a Row?

A row is a mapping from column names to values:

```java
Row row = new Row();
row.set("id", 42);
row.set("name", "Alice");
row.set("age", 30);

int age = row.getInt("age");       // 30
String name = row.getString("name");  // "Alice"
Object id = row.get("id");         // 42
```

It's the currency that flows through the query engine. The `TableScanOperator` produces rows from disk bytes. The `FilterOperator` reads rows and decides which to pass through. The `ProjectOperator` creates new smaller rows. The HTTP handler serializes rows to JSON.

## Row vs Bytes

```
                     TupleSerializer
                          ⇄
┌──────────────┐                    ┌──────────────────┐
│  Row         │                    │  byte[] on disk   │
│  (in-memory) │                    │  (8KB page)       │
│              │                    │                   │
│  id: 42      │  ←── serialize ──  │  0x00 0x00 0x2A  │
│  name: Alice │  ── deserialize →  │  0x05 Alice...   │
│  age: 30     │                    │  0x00 0x00 0x1E  │
└──────────────┘                    └──────────────────┘
```

The Row is the **logical** view of data. The bytes are the **physical** view. `TupleSerializer` converts between them.

## What's Inside a Row

```java
public class Row {
    private final Map<String, Object> values;

    public Row() {
        this.values = new LinkedHashMap<>();  // preserves insertion order
    }

    public void set(String columnName, Object value) {
        values.put(columnName, value);
    }

    public Object get(String columnName) {
        return values.get(columnName);
    }

    public int getInt(String columnName) {
        return (int) values.get(columnName);
    }

    public String getString(String columnName) {
        return (String) values.get(columnName);
    }

    public double getFloat(String columnName) {
        return (double) values.get(columnName);
    }

    public boolean getBoolean(String columnName) {
        return (boolean) values.get(columnName);
    }

    public int size() {
        return values.size();
    }

    public Set<String> getColumnNames() {
        return values.keySet();
    }
}
```

A `LinkedHashMap` preserves the column order from `CREATE TABLE`. Column order matters for tuple serialization — column at position 0 is the first to be encoded, position 1 is second, etc.

## How Rows Move Through the System

```
1. Created by TableScanOperator (deserialized from disk bytes)
   Row {id: 42, name: "Alice", age: 30, email: "alice@..."}

2. Passed through FilterOperator (check WHERE clause)
   WHERE age > 25 → row passes, unchanged

3. Transformed by ProjectOperator (keep only requested columns)
   SELECT name, age → Row {name: "Alice", age: 30}
   (new smaller row — id and email are dropped)

4. Accumulated by SortOperator
   All rows collected in memory, sorted by Comparator

5. Emitted by LimitOperator
   After N rows, stops pulling

6. Collected by Executor into ResultSet
   List<Row> rows → serialized to JSON for HTTP response
```

A row gets smaller as it moves up the operator tree. `TableScan` produces the full row (all columns). `Project` strips it to only the requested columns. This saves memory: you don't carry unused data through the pipeline.

## Row vs TupleDesc

`TupleDesc` describes a row's structure — separate from the row data itself:

```java
// TupleDesc: the schema of the row (column names + types)
TupleDesc desc = new TupleDesc(List.of(
    new ColumnMetadata("id", IntegerType.INSTANCE, 0),
    new ColumnMetadata("name", TextType.INSTANCE, 1)
));

// Row: concrete data matching that schema
Row row = new Row();
row.set("id", 42);
row.set("name", "Alice");
```

The `TupleDesc` tells the serializer what to expect. Without it, you don't know which bytes correspond to which column, or what types they are. Every `Operator` exposes `getOutputSchema()` so the next operator in the chain knows what columns are coming.

## Why Not a Column-Indexed Array?

Some database engines use a `Object[]` indexed by column position instead of a `Map<String, Object>`:

```java
// Faster but less readable
Object[] row = new Object[3];
row[0] = 42;
row[1] = "Alice";
row[2] = 30;
```

Our map-based approach is slightly slower (hash lookups), but:
- **More readable** — `row.get("age")` vs `row[2]`
- **Safer** — reordering columns doesn't break things
- **Self-describing** — you can print a row as `{id=42, name=Alice, age=30}`

For a learning project, clarity beats micro-optimization. Real databases use column-indexed arrays for performance.

---

→ Related: [Tuple Serialization](tuple-serialization.md) — Row becomes bytes
→ Related: [Volcano Iterator Model](volcano-model.md) — Rows flow through operators
