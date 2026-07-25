# Heap File

The physical file on disk for one table — a sequence of 8KB pages.

## What Is a Heap File?

```
users.dat:
┌──────┬──────┬──────┬──────┬──────┐
│Page 0│Page 1│Page 2│Page 3│Page 4│
│ 8KB  │ 8KB  │ 8KB  │ 8KB  │ 8KB  │
└──────┴──────┴──────┴──────┴──────┘

File size = numPages × 8192 bytes
Page N starts at byte: N × 8192
```

One file = one table. `users.dat` for the `users` table. `orders.dat` for the `orders` table. This is the same model SQLite and PostgreSQL (single-table tablespace) use.

## Operations

```java
HeapFile hf = new HeapFile("data/mydb/users.dat");

// Read a page
Page page = hf.readPage(3);
// → Read 8192 bytes from file position 3 * 8192

// Write a page back
hf.writePage(3, page);
// → Write 8192 bytes to file position 3 * 8192

// Append a new page (file grows)
hf.appendPage();
// → File grows by 8192 bytes, returns the new page

// Get total pages
int numPages = hf.getNumPages();
// → File size / 8192
```

Under the hood: `RandomAccessFile` with `seek(pageNum * 8192)` then `read(bytes)` / `write(bytes)`.

## Row Iteration

```java
// Scan every row in the table:
for (int pageNum = 0; pageNum < hf.getNumPages(); pageNum++) {
    Page page = hf.readPage(pageNum);
    for (int slot = 0; slot < page.getNumSlots(); slot++) {
        if (!page.isDeleted(slot)) {
            byte[] tuple = page.getTuple(slot);
            // process row
        }
    }
}
```

This sequential scan is O(n) across the entire table. In Phase 1, every `SELECT` does this. Phase 6 adds B+tree indexes to make lookups O(log n).

## Why "Heap"?

A heap is an unordered pile. Rows are appended to wherever there's space — no sorting, no ordering. New rows go into the last page (or a new page if the last one is full). This makes `INSERT` fast (just append) but `SELECT WHERE id = 42` slow (scan everything).

PostgreSQL calls this a "heap table" — same concept, same name.

## File on Disk

```
$ ls -la data/mydb/
-rw-r--r--  1 user  staff   8192  Jul 24 14:00 catalog.json
-rw-r--r--  1 user  staff  40960  Jul 24 14:05 users.dat     ← 5 pages
-rw-r--r--  1 user  staff  16384  Jul 24 14:06 orders.dat    ← 2 pages
```

You can inspect it with `xxd` or a hex editor — every 8192 bytes is a page boundary.

## Connection to Buffer Pool

In production, you don't use `HeapFile.readPage()` directly for every access. You go through the buffer pool:

```
Executor                    BufferPool                  HeapFile
   │                            │                          │
   │ getPage("users.dat", 3)    │                          │
   │──────────────────────────►│                          │
   │                            │ Is page 3 cached?       │
   │                            │ No → read from disk     │
   │                            │───────────────────────►│
   │                            │ readPage(3)              │
   │                            │◄───────────────────────│
   │                            │ Cache it in LRU map     │
   │ return page                │                          │
   │◄──────────────────────────│                          │
```

The heap file is the **physical I/O layer**. The buffer pool sits on top, caching pages so repeated reads hit memory instead of disk.

→ Previous: [Slotted-Page Layout](slotted-page.md)
→ Next: [Buffer Pool](buffer-pool.md)
