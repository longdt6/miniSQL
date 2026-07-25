# Buffer Pool

An LRU (Least Recently Used) cache that keeps hot pages in memory, avoiding disk I/O.

## The Problem

Reading an 8KB page from disk takes ~100µs (SSD) or ~10ms (HDD). A query that touches 100 pages would take 10ms (SSD) or 1 second (HDD). If those same pages are accessed again, you're paying the same cost for no reason.

## The Solution

Cache pages in memory. A `Map<PageId, Page>` with LRU eviction when the cache is full.

```
DISK (users.dat)                MEMORY (BufferPool, max 200 pages)

┌──────┐                        ┌──────────────────────────┐
│Page 0│ ←──── read ─────────── │ P5 │ P2 │ P9 │ P3 │ ... │
├──────┤                        └──────────────────────────┘
│Page 1│                            MRU ←────────→ LRU
├──────┤                                evicted when full
│Page 2│
├──────┤
│Page 3│ ←── in cache, no disk read needed
├──────┤
│ ...  │
└──────┘
```

## How LRU Works

```
Cache state (max 4 pages):

Access Page 7 → not in cache, read from disk
  [ P7 ]                                     ← P7 is MRU

Access Page 3 → not in cache, read from disk
  [ P3 | P7 ]

Access Page 7 → HIT! Move to MRU
  [ P7 | P3 ]

Access Page 1 → not in cache, read from disk
  [ P1 | P7 | P3 ]

Access Page 9 → not in cache, cache NOT full, read from disk
  [ P9 | P1 | P7 | P3 ]

Access Page 5 → not in cache, cache FULL
  → evict P3 (LRU), read P5 from disk, insert at MRU
  [ P5 | P9 | P1 | P7 ]                     ← P3 evicted
```

The most recently used page stays at the front. The least recently used gets evicted when space is needed.

## Implementation

```java
public class BufferPool {
    private final int maxPages;
    private final LinkedHashMap<PageId, Page> cache;

    public BufferPool(int maxPages) {
        this.maxPages = maxPages;
        // accessOrder=true → reorders on get() for LRU
        this.cache = new LinkedHashMap<>(maxPages, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<PageId, Page> eldest) {
                if (size() > maxPages) {
                    flushPage(eldest.getValue());  // write dirty page to disk
                    return true;
                }
                return false;
            }
        };
    }

    public Page getPage(PageId pageId) {
        Page page = cache.get(pageId);
        if (page == null) {
            page = heapFile.readPage(pageId.getPageNum());
            cache.put(pageId, page);
        }
        return page;
    }
}
```

`LinkedHashMap` with `accessOrder=true` and `removeEldestEntry` gives us LRU with zero effort.

## PageId

A simple record identifying a page uniquely:

```java
record PageId(String heapFilePath, int pageNumber) {}

// Example:
new PageId("data/mydb/users.dat", 3)  → page 3 of users table
```

The pool is shared across all tables — one cache for the whole database.

## Dirty Pages

A page is "dirty" when modified in memory but not yet written to disk:

```
1. pool.getPage(id)   → page in memory
2. page.insert(tuple) → page is now dirty (disk has old version)
3. pool.flushPage(id)  → write dirty page to disk (sync)
```

The buffer pool tracks which pages are dirty. On eviction, dirty pages are flushed to disk. On server shutdown, `flushAll()` writes all dirty pages.

## Why a Buffer Pool (Not Just File I/O)

| Without buffer pool | With buffer pool |
|--------------------|-----------------|
| Every page access = disk read | Hot pages = memory access (100× faster) |
| No shared cache across queries | All queries share the same cache |
| Can't control memory usage | Fixed-size pool = bounded memory |

→ Previous: [Heap File](heap-file.md)
→ Next: [Tuple Serialization](tuple-serialization.md)
