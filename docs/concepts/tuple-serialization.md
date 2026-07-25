# Tuple Serialization

How a Row (in-memory, column names → Java objects) becomes bytes (on-disk, packed binary) and back.

## The Problem

In memory, a row is a `Map<String, Object>`:

```java
{"id": 42, "name": "Alice", "age": 30}
```

On disk, it must be a contiguous `byte[]` inside an 8KB page. The serializer bridges these two worlds.

## Tuple Binary Format

```
┌─ 4 bytes: Tuple Header ──────────────────┐
│  byte 0: flags (bit 0 = deleted)         │
│  byte 1: columnCount                     │
│  bytes 2-3: total tuple length           │
├─ ceil(N/8) bytes: Null Bitmap ───────────┤
│  bit 0 → column 0 is NULL?               │
│  bit 1 → column 1 is NULL?               │
│  ...                                     │
├─ 2 bytes per column: Column Offsets ─────┤
│  offset[0]: where column 0 data starts   │
│  offset[1]: where column 1 data starts   │
│  ...                                     │
├─ Variable: Column Data ──────────────────┤
│  INT:    4 bytes, big-endian             │
│  FLOAT:  8 bytes, IEEE 754               │
│  TEXT:   2B length + UTF-8 bytes         │
│  BOOL:   1 byte (0x00 or 0x01)           │
└──────────────────────────────────────────┘
```

### Example: `{id: 42, name: "Alice", age: 30}`

```
Offset 0:  0x03              flags=0 (alive), columnCount=3
Offset 1:  0x03              columnCount
Offset 2:  0x00 0x20         tupleLength = 32 bytes

Offset 4:  0x00              null bitmap, byte 0: no nulls

Offset 5:  0x00 0x0E         column 0 offset = 14 (starts at byte 14)
Offset 7:  0x00 0x12         column 1 offset = 18
Offset 9:  0x00 0x19         column 2 offset = 25

Offset 11: 0x00 0x00 0x00    padding (align to 4-byte boundary for first INT)

Offset 14: 0x00 0x00 0x00 0x2A   id = 42 (INT, 4 bytes)

Offset 18: 0x00 0x05              name length = 5
Offset 20: 0x41 0x6C 0x69         "Alice" in UTF-8
           0x63 0x65

Offset 25: 0x00 0x00 0x00 0x1E   age = 30 (INT, 4 bytes)

Total: 29 bytes + 3 padding = 32 bytes
```

Actually let me simplify. Without manual padding:

```
Byte 0:    0x03        (flags: alive, not deleted)
Byte 1:    0x03        (3 columns)
Bytes 2-3: 0x00 0x1D   (total length = 29)

Byte 4:    0x00        (null bitmap: no column is null)

Bytes 5-6:  0x00 0x0B  (col 0 offset: data starts at byte 11)
Bytes 7-8:  0x00 0x0F  (col 1 offset: data starts at byte 15)
Bytes 9-10: 0x00 0x16  (col 2 offset: data starts at byte 22)

Bytes 11-14: 0x00 0x00 0x00 0x2A   (id = 42)

Bytes 15-16: 0x00 0x05              (name length = 5)
Bytes 17-21: 0x41 0x6C 0x69 0x63 0x65  ("Alice")

Bytes 22-25: 0x00 0x00 0x00 0x1E   (age = 30)
```

## Why Column Offsets?

Without offsets, to read `age` (column 2) you'd need to:
1. Read `id` (4 bytes)
2. Read `name` length prefix (2 bytes)
3. Read `name` string (5 bytes)
4. Finally read `age`

With offsets: jump directly to byte 22 (offset from the offset table). O(1) access to any column without decompressing the whole row. This matters for `SELECT age FROM users` — you never touch the `id` or `name` bytes.

## Type-Aware Encoding

Each `DataType` knows how to encode/decode itself:

```java
interface DataType {
    byte[] encode(Object value);           // Java object → bytes
    Object decode(byte[] data, int offset, int length);  // bytes → Java object
}

// INT
IntegerType.encode(42)      → [0x00, 0x00, 0x00, 0x2A]
IntegerType.decode(bytes)   → 42

// TEXT
TextType.encode("Alice")    → [0x00, 0x05, 0x41, 0x6C, 0x69, 0x63, 0x65]
TextType.decode(bytes)      → "Alice"

// BOOL
BooleanType.encode(true)    → [0x01]
BooleanType.encode(false)   → [0x00]
```

The `TupleSerializer` delegates per-column encoding to the type system. It handles the row-level structure (header, offsets); each `DataType` handles value-to-bytes conversion.

→ Previous: [Buffer Pool](buffer-pool.md)
→ Next: [Row vs Column Storage](row-vs-column.md)
