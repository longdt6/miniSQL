# Volcano Iterator Model

How query execution works: `open() → next() → close()` pipeline. The classic database execution model.

## The Idea

Every query becomes a **tree of operators**. Each operator is an **iterator** with three methods:

```java
interface Operator {
    void open();        // Initialize, acquire resources
    Row next();         // Return next row, or null if done
    void close();       // Release resources
}
```

The operator at the top of the tree calls `next()` on its child, which calls `next()` on its child, etc. Rows **flow upward** through the pipeline, one at a time.

## Example: SELECT with WHERE and ORDER BY

```sql
SELECT name, age FROM users WHERE age > 25 ORDER BY name LIMIT 10
```

Plan tree:
```
          Limit(10)
              │
          Sort(name ASC)
              │
          Project([name, age])
              │
          Filter(age > 25)
              │
          TableScan(users)
```

Execution:
```
Limit.next()
  → Sort.next()
      Sort accumulates ALL rows from child first (needed for sorting)
      → Project.next()
          → Filter.next()
              → TableScan.next() → row 1: {id=1, name="Alice", age=30}
              Filter: age > 25? Yes → return row
          Project: keep only [name, age] → {name="Alice", age=30}
      Sort: collect all projected rows, sort by name in memory
  Sort: emit first sorted row → {name="Alice", age=30}
Limit: count=1, return row

Limit.next()
  → Sort.next() → {name="Carol", age=35}
Limit: count=2, return row

... up to 10 rows
```

## Key Properties

### 1. Pipelined (mostly)
Data flows through operators without materializing intermediate results. `Filter` emits a row as soon as it gets one — doesn't wait for all rows.

Exception: `Sort` is a **blocking operator** — it must consume ALL rows before emitting any, because you can't know the first sorted row until you've seen them all.

### 2. Pull-based
The top operator pulls from its child. The child doesn't push. This is called the **Volcano model** (also known as the iterator model). Every real database uses some variant of this.

### 3. Each operator has one job

| Operator | Responsibility |
|----------|---------------|
| `TableScan` | Read every row from a heap file, deserialize |
| `Filter` | Evaluate a WHERE expression, pass through or skip |
| `Project` | Strip row to only requested columns |
| `Sort` | Accumulate all rows, sort in memory, emit sorted |
| `Limit` | Count rows, stop after N |
| `Insert` | Serialize rows, write to heap file pages |
| `Update` | Scan + filter → overwrite matching rows |
| `Delete` | Scan + filter → mark matched rows deleted |

### 4. Operators compose
The planner builds the tree. New operators can be added without changing existing ones. An `IndexScan` operator (Phase 6) just needs to implement `Operator` — everything above it works unchanged.

## Why Not "Generate a Loop"?

A simpler approach would be to generate code for each query:

```java
// SELECT name, age FROM users WHERE age > 25
for (Page page : heapFile.getPages()) {
    for (byte[] tuple : page.getTuples()) {
        Row row = deserialize(tuple);
        if (row.getInt("age") > 25) {
            emit(row.get("name"), row.get("age"));
        }
    }
}
```

This is actually faster (no virtual dispatch on `next()`). Some databases do this — it's called query compilation.

But the iterator model is:
- **Simpler to build** — each operator is independent
- **Easier to extend** — add operators without changing the executor
- **Easier to debug** — you can inspect each operator separately
- **The classic approach** — every database textbook uses it

## In Our Code

```java
class FilterOperator implements Operator {
    private final Operator child;
    private final Expression predicate;

    public void open() { child.open(); }

    public Row next() {
        while (true) {
            Row row = child.next();
            if (row == null) return null;           // child exhausted
            if (predicate.evaluate(row).isTrue()) {  // WHERE clause check
                return row;
            }
            // Skip this row, try next one
        }
    }

    public void close() { child.close(); }
}
```

That's the entire operator. It wraps another operator, pulls rows, evaluates an expression, and only passes through the ones that match.

→ Previous: [B-tree & Fanout](btree.md)
→ Next: [SQL Query Lifecycle](query-lifecycle.md)
