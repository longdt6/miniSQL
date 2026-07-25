# Page

The fundamental unit of disk I/O in every database.

## What Is a Page?

A page is exactly **8192 bytes (8KB)**. Nothing more, nothing less. It's the database's "minimum transaction size" with the disk.

```
users.dat on disk:

┌──────┬──────┬──────┬──────┬──────┬──────┐
│ 0KB  │ 8KB  │ 16KB │ 24KB │ 32KB │ 40KB │
└──────┴──────┴──────┴──────┴──────┴──────┘
 Page 0 Page 1 Page 2 Page 3 Page 4 Page 5
```

When the database needs data, it never reads individual bytes. It reads **one entire page** into memory, then picks out what it needs. When it writes, it writes an entire page back.

## Why 8KB?

Disks read in blocks, not bytes. Asking for "4 bytes at position 42" actually reads an entire hardware block (512B–4KB), throws away what you didn't need, and gives you 4 bytes. Next query: "8 bytes at position 46" — same block, another full read.

Databases fix this by saying: **the unit of I/O is the page**. Read it once, cache it in the buffer pool, reuse it across many queries.

**Why not bigger?** A 1MB page would waste memory — you might only need one row (200 bytes) but load 1MB. 8KB is the sweet spot.

**Why not smaller?** A 256B page would mean many more disk reads. Pages are too small to hold enough rows for good locality.

| Database | Page size |
|----------|-----------|
| PostgreSQL | 8KB (configurable) |
| MySQL InnoDB | 16KB (default) |
| SQLite | 4KB (default) |
| Oracle | 8KB (default) |
| **miniSQL** | 8KB |

## A Page Is Just a `byte[]`

```java
public class Page {
    static final int PAGE_SIZE = 8192;

    private final byte[] data;    // data.length == 8192, always

    public Page() {
        this.data = new byte[PAGE_SIZE];
    }

    // All operations read/write into data[]
    // getInt(offset), getShort(offset), getBytes(offset, len), ...
}
```

No database-specific hardware. No mmap trick. Just a `byte[8192]`.

## Page on Disk vs Page in Memory

```
DISK                          MEMORY
────                          ──────

users.dat                     BufferPool
┌──────┐                      ┌──────────────┐
│Page 0│ ───── read ────────► │ Page 0        │
├──────┤                      │  byte[8192]   │
│Page 1│                      ├──────────────┤
├──────┤                      │ Page 3        │
│Page 2│                      │  byte[8192]   │
├──────┤                      ├──────────────┤
│Page 3│ ◄── write back ───── │ Page 7        │
├──────┤    (flush)           │  byte[8192]   │
│ ...  │                      └──────────────┘
└──────┘
```

- Reading: `byte[8192]` is read from `users.dat` at position `pageNumber * 8192`
- Writing: `byte[8192]` is written to `users.dat` at position `pageNumber * 8192`
- The buffer pool caches hot pages. If page 0 is in the pool, no disk read.

## What's Inside a Page?

A page contains rows (tuples), but they're not just packed sequentially — that would cause fragmentation. The page uses a **slotted-page layout** to manage variable-size rows inside the fixed 8192 bytes.

→ Next: [Slotted-Page Layout](slotted-page.md)

## Key Takeaways

1. A page is the atomic unit of I/O — always 8192 bytes, always read/written as a whole
2. 8KB balances memory efficiency (not too big) with locality (not too small)
3. A `Page` object is literally `byte[8192]` — no magic, no special hardware
4. Pages live on disk (in heap files) and in memory (in the buffer pool)
5. Rows inside a page use a slotted-page layout to handle variable sizes
