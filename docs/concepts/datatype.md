# DataType System

The interface that makes every SQL type behave the same way: encode to bytes, decode from bytes, compare values, parse literals.

## Why a Type Hierarchy

Without types, your database can't:
- Know how many bytes a value takes on disk (INT = 4, TEXT = variable)
- Compare values correctly (`age > 25` must do numeric comparison, not string comparison)
- Validate input (`'hello'` can't go into an INTEGER column)
- Parse SQL literals (`42`, `'Alice'`, `TRUE` mean different things)

Every database has a type system — PostgreSQL has ~40 types, ours has 4:

```
DataType (interface)
├── IntegerType    → 4 bytes, big-endian int32
├── FloatType      → 8 bytes, IEEE 754 double
├── TextType       → 2B length prefix + UTF-8 bytes
└── BooleanType    → 1 byte (0x00 or 0x01)
```

## The Interface

```java
interface DataType {
    byte[] encode(Object value);                    // Java object → disk bytes
    Object decode(byte[] data, int offset, int length);  // disk bytes → Java object
    int compare(Object a, Object b);                // for WHERE clauses, ORDER BY
    int getSize();                                  // fixed size, or -1 for variable
    Object parse(String literal);                   // "42" → 42, "'hello'" → "hello"
    String getSqlName();                            // "INTEGER", "TEXT", "FLOAT", "BOOLEAN"
    boolean isFixedSize();                          // true for INT/FLOAT/BOOL, false for TEXT
}
```

Four concerns, one interface:
- **Serialization** (`encode`/`decode`) — used by `TupleSerializer` for disk I/O
- **Comparison** (`compare`) — used by `FilterOperator` for WHERE clauses and `SortOperator` for ORDER BY
- **Parsing** (`parse`) — used by the lexer/parser to interpret SQL literals
- **Metadata** (`getSize`, `isFixedSize`, `getSqlName`) — used by the catalog and page allocator

## Each Type in Detail

### IntegerType — 4 bytes, big-endian

```
Java:   42
Bytes:  [0x00, 0x00, 0x00, 0x2A]
         ↑ big-endian: most significant byte first

Java:   -1
Bytes:  [0xFF, 0xFF, 0xFF, 0xFF]  (two's complement)
```

- Fixed 4 bytes. No length prefix needed — the type system knows the size.
- Compare: standard integer comparison. `42 > 25 → true`.
- Parse: `"42"` → `42`, `"-1"` → `-1`.

### FloatType — 8 bytes, IEEE 754

```
Java:   3.14
Bytes:  [0x40, 0x09, 0x1E, 0xB8, 0x51, 0xEB, 0x85, 0x1F]

Java:   -0.5
Bytes:  [0xBF, 0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]
```

- Fixed 8 bytes. `Double.doubleToLongBits()` for encoding, `Double.longBitsToDouble()` for decoding.
- Compare: standard double comparison.
- Parse: `"3.14"` → `3.14`, `"1e10"` → `10000000000.0`.

### TextType — 2B length + UTF-8 bytes (variable)

```
Java:   "Alice"
Bytes:  [0x00, 0x05, 0x41, 0x6C, 0x69, 0x63, 0x65]
          length=5  "A"   "l"   "i"   "c"   "e"

Java:   ""  (empty string)
Bytes:  [0x00, 0x00]  ← 2-byte length = 0, no data
```

- Variable size. `getSize()` returns `-1` (variable). `isFixedSize()` returns `false`.
- The 2-byte length limits TEXT to 65535 bytes. For now, that's plenty.
- Compare: Java `String.compareTo()` — lexicographic order.
- Parse: strip single quotes and unescape: `'hello'` → `"hello"`, `'it''s'` → `"it's"`.

### BooleanType — 1 byte

```
Java:   true    →  [0x01]
Java:   false   →  [0x00]
```

- Fixed 1 byte.
- Compare: `true > false`.
- Parse: `TRUE` → `true`, `FALSE` → `false`. Also accept `true`, `false`, `1`, `0`.

## Why Fixed Size Matters

Fixed-size types (INT, FLOAT, BOOL) always occupy the same number of bytes per row. Variable-size types (TEXT) need a length prefix.

This affects page layout: with only fixed-size columns, you don't need a column offset table in the tuple — the offset of column N is always `headerSize + N * columnSize`. But real tables always have TEXT or VARCHAR, so we include the offset table for all tuples regardless of column types. Simpler code, slightly more bytes per row.

## Why Compare() Lives in the Type System

Not in the expression evaluator. Not in a utility class. In the type.

```
WHERE age > 25  → compare(age_value, parse("25"))
WHERE name = 'Alice'  → compare(name_value, parse("'Alice'"))
```

The expression evaluator doesn't know what types it's comparing — it just calls `leftType.compare(a, b)`. This means adding a new type (DATE, DECIMAL) adds one new class that implements `DataType`. Nothing else changes.

## The Static Factory

```java
// Map SQL type names to instances at startup
DataType.fromSqlName("INTEGER")  → IntegerType instance
DataType.fromSqlName("INT")      → IntegerType instance  (alias)
DataType.fromSqlName("TEXT")     → TextType instance
DataType.fromSqlName("VARCHAR")  → TextType instance     (alias)
DataType.fromSqlName("FLOAT")    → FloatType instance
DataType.fromSqlName("BOOLEAN")  → BooleanType instance
DataType.fromSqlName("BOOL")     → BooleanType instance  (alias)
```

Used by the catalog (when loading `catalog.json`) and the parser (when processing `CREATE TABLE`).

---

→ Related: [Tuple Serialization](tuple-serialization.md) — uses encode/decode per column
→ Related: [Catalog](catalog.md) — stores type names, looked up via `fromSqlName()`
