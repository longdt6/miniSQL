# Slotted-Page Layout

How variable-size rows fit into fixed-size 8KB pages. Used by PostgreSQL.

## The Problem

Rows have different sizes, but all pages are exactly 8192 bytes:

```
Row 1: 48 bytes  ──┐
Row 2: 120 bytes ──┤ How do you pack these
Row 3: 32 bytes  ──┤ into a fixed 8192-byte
Row 4: 95 bytes  ──┘ container?
```

If you just pack them sequentially and delete Row 2, you get a 120-byte hole. Over time: Swiss cheese — hundreds of tiny gaps too small for any new row. This is external fragmentation.

## The Solution: Two Regions, Opposite Growth

```
┌───────────────────────────────────────┐ byte 0
│                                       │
│  PAGE HEADER (24 bytes)               │
│    pageNumber, numSlots,              │
│    freeSpaceOffset, freeSpaceEnd      │
│                                       │
├───────────────────────────────────────┤ byte 24
│                                       │
│  ╔═══════════════════════════════════╗ │
│  ║       SLOT DIRECTORY             ║ │  ← grows downward ↓
│  ║  [slot 0] [slot 1] [slot 2]     ║ │
│  ║  fixed 6 bytes each             ║ │
│  ╚═══════════════════════════════════╝ │
│                                       │
│               ↓ ↓ ↓                   │
├───────────────────────────────────────┤  ← freeSpaceOffset
│                                       │
│            FREE SPACE                 │
│                                       │
├───────────────────────────────────────┤  ← freeSpaceEnd
│               ↑ ↑ ↑                   │
│                                       │
│  ╔═══════════════════════════════════╗ │
│  ║         TUPLE DATA               ║ │  ← grows upward ↑
│  ║  [row bytes] [row bytes]         ║ │
│  ║  variable sizes                  ║ │
│  ╚═══════════════════════════════════╝ │
│                                       │
└───────────────────────────────────────┘ byte 8191
```

Two regions growing toward each other. When they meet, the page is full.

## Slot Directory: The Index

An array of pointers, each 6 bytes: `(offset, flags, length)`.

```
Slot 0:  offset=8100, flags=0x0000, length=92   → alive, 92-byte row at byte 8100
Slot 1:  offset=8008, flags=0x0001, length=92   → DELETED, 92-byte row at byte 8008
Slot 2:  offset=7900, flags=0x0000, length=108  → alive, 108-byte row at byte 7900
```

Fixed-size slots = O(1) access to any tuple by slot number. Slot 3 is always at byte `24 + 3 * 6`.

## Tuple Data: The Actual Rows

The raw binary bytes of each row, packed at the bottom. Variable sizes, no fixed positions.

Think of it like a library: the slot directory is the **card catalog** (small, ordered, tells you where books are), the tuple data is the **bookshelves** (books anywhere, variable sizes).

## Five Core Operations

### INSERT
```
1. Check: freeSpaceOffset + 6 < freeSpaceEnd - tupleSize ?
   (room for one more slot + the row bytes)
2. Write tuple bytes at freeSpaceEnd - tupleSize
3. Write new slot at freeSpaceOffset: (offset, 0x0000, length)
4. freeSpaceOffset += 6   (directory grew down)
5. freeSpaceEnd -= tupleSize  (data grew up)
```

### READ (by slot number)
```
1. Read slot N: offset from bytes [24 + N*6], length from [24 + N*6 + 4]
2. Return bytes[offset ... offset + length]
O(1) — just follow the pointer.
```

### DELETE (soft delete)
```
1. Set slot flags bit 0 to 1 (deleted)
2. Row bytes stay where they are. Space is NOT reclaimed immediately.
```

Why not reclaim? Shifting rows would change their offsets, breaking external indexes that point to "page 5, slot 3." PostgreSQL does the same: deletes mark tuples, VACUUM reclaims later.

### UPDATE
```
New size ≤ old size: overwrite in place (rare — sizes usually differ)
New size > old size:
  1. Mark old slot deleted
  2. Write new bytes at freeSpaceEnd (like INSERT)
  3. Update slot to point to new location
```

### COMPACT (defragmentation)
```
When free space is low:
1. Walk all live slots, pack rows tightly at the bottom
2. Update each slot's offset to match new positions
3. freeSpaceEnd moves up (dead space reclaimed)
4. Remove deleted slot entries, renumber remaining

Expensive but rare. Same concept as PostgreSQL VACUUM.
```

## Why Slot Numbers Don't Change

After compaction, slot 3's offset changes (because rows shifted), but **slot 3 is still slot 3**. External indexes reference slots by number, not by byte offset. The slot directory is the stable identity layer.

## In Our Code

```java
public class Page {
    static final int PAGE_SIZE = 8192;
    static final int HEADER_SIZE = 24;
    static final int SLOT_SIZE = 6;    // 2B offset + 2B flags + 2B length

    private byte[] data = new byte[PAGE_SIZE];

    // Header fields
    int getPageNumber()         { return readInt(data, 0); }
    int getNumSlots()           { return readShort(data, 4); }
    int getFreeSpaceOffset()    { return readShort(data, 6); }
    int getFreeSpaceEnd()       { return readShort(data, 8); }

    // Slot N starts at byte: HEADER_SIZE + N * SLOT_SIZE
    int getSlotOffset(int slot) { return readShort(data, HEADER_SIZE + slot * 6); }
    int getSlotFlags(int slot)  { return readShort(data, HEADER_SIZE + slot * 6 + 2); }
    int getSlotLength(int slot) { return readShort(data, HEADER_SIZE + slot * 6 + 4); }

    boolean hasSpace(int byteCount) {
        return getFreeSpaceOffset() + SLOT_SIZE < getFreeSpaceEnd() - byteCount;
    }
}
```

→ Previous: [Page](page.md)
→ Next: [Heap File](heap-file.md)
