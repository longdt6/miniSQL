# B+tree Operations in Detail

The algorithms behind B+tree search, insert (with page split), delete (with borrow/merge), and range scan — all using the same 8KB Page infrastructure from Phase 1.

## Page Layout: Internal Nodes vs Leaf Nodes

Both are 8KB `Page` objects, but they use the page body differently:

### Internal Node Layout

```
┌────────────────────────────────────────────┐ byte 0
│  Page Header (24 bytes)                    │
│    pageNumber, numKeys (how many keys),    │
│    freeSpace (for new key-pointer pairs)   │
├────────────────────────────────────────────┤
│  Key-Pointer Array (sorted by key)         │
│  [Key 0: 4B] [Child 0 ptr: 4B]           │  ← child for keys < Key 0
│  [Key 1: 4B] [Child 1 ptr: 4B]           │  ← child for Key 0 ≤ k < Key 1
│  [Key 2: 4B] [Child 2 ptr: 4B]           │
│                              [Child N: 4B] │  ← extra pointer at the end!
├────────────────────────────────────────────┤
│  FREE SPACE                                │
└────────────────────────────────────────────┘ byte 8191

Rule: an internal node with N keys has N+1 child pointers.
Pointer[i] → child page for keys < Key[i] (when i=0)
Pointer[i] → child page for Key[i-1] ≤ keys < Key[i] (when 0 < i < N)
Pointer[N] → child page for keys ≥ Key[N-1]
```

### Leaf Node Layout

```
┌────────────────────────────────────────────┐ byte 0
│  Page Header                               │
│    pageNumber, numKeys, freeSpace          │
├────────────────────────────────────────────┤
│  Leaf metadata                             │
│    nextLeafPage: 4B  (linked list!)       │
│    prevLeafPage: 4B                       │
├────────────────────────────────────────────┤
│  Key-Value Array (sorted by key)           │
│  [Key 0: 4B] [Value 0: RowId]             │  ← RowId = (heapPage, slot)
│  [Key 1: 4B] [Value 1: RowId]             │
│  [Key 2: 4B] [Value 2: RowId]             │
├────────────────────────────────────────────┤
│  FREE SPACE                                │
└────────────────────────────────────────────┘ byte 8191
```

### RowId

```java
record RowId(int pageNum, int slotNum) {}
```

Points to exactly where the row lives in the heap file. An index stores `(Key → RowId)` pairs. To get the full row, use the RowId to read the tuple from the heap file.

---

## Operation 1: Search

```
search(key):
  node = root page
  while node is internal:
    find the child pointer
    node = bufferPool.getPage(childPageNum)
  // node is now a leaf
  scan leaf for exact key
  return RowId (or null if not found)
```

### Finding the Child Pointer (Internal Node)

```
For an internal node with keys [30, 60, 100]:

  key = 25  → 25 < 30         → Pointer[0] (first child)
  key = 45  → 30 ≤ 45 < 60    → Pointer[1]
  key = 75  → 60 ≤ 75 < 100   → Pointer[2]
  key = 150 → 100 ≤ 150       → Pointer[3] (last child)
```

Binary search within the page: `Arrays.binarySearch(keys, key)`. If exact match, go to `Pointer[index+1]`. If insertion point, go to `Pointer[insertionPoint]`.

This is O(log₂ N_keys_per_page) within a page plus O(height) page reads. Total: ~2-3 page reads for any lookup, regardless of table size.

---

## Operation 2: Insert (with Split)

```
insert(key, value):
  find leaf page where key belongs
  if leaf has room:
    insert key+value in sorted position
    done (common case, ~99%+ of inserts)
  else:
    split leaf into two pages
    promote middle key to parent
    if parent is full:
      split parent too (cascade up)
```

### Case A: Leaf Has Room (Common)

```
Before:  [10, 20, 30, 40]     (max 5 keys per leaf, 4 used)
Insert:  25

After:   [10, 20, 25, 30, 40]   ← just shift and insert. O(m) shift within one page.
```

No I/O beyond the leaf page. This is fast — the main reason B+trees work so well.

### Case B: Leaf Is Full — Split

```
Before:  [10, 20, 25, 30, 35]     (max 5 keys, all used)
Insert:  40

Step 1: Create a temp sorted list: [10, 20, 25, 30, 35, 40]
Step 2: Split in half:
           Left:  [10, 20, 25]    (3 keys — stays in original page)
           Right: [30, 35, 40]    (3 keys — new page)
Step 3: Promote the first key of the right side to parent: 30
Step 4: Update leaf linked list:
           [10,20,25] → [30,35,40]   (nextLeaf pointers)
```

The tree before the split:

```
          [50]
         /    \
    [10,20,25,30,35]  [60,70]
```

After splitting the left leaf:

```
          [50]
         /    \
    [10,20,25]  [30,35,40]  [60,70]
         ↑          ↑
       old leaf   new leaf

But wait — the parent only has one key [50] and two pointers.
We need to add the promoted key 30 and the new child pointer.
```

Parent after insertion:

```
       [30, 50]
      /    |    \
  [leaf] [new] [leaf]
```

### Case C: Parent Is Also Full — Cascade

```
Before:
            [30, 50, 70]          ← parent is full (max 3 keys)
           /    |    |    \
       ...    ...    ...    ...

Insert in a leaf causes a split, promotes key 80 to parent.
Parent temp list: [30, 50, 70, 80]

Split parent:
  Left parent:  [30]          (1 key, 2 children)
  Right parent: [70]          (1 key, 2 children, or is it?)
  Promote:      50             ← 50 goes UP

Wait — the middle key gets promoted, not kept in either child.
Internal node split: N keys → left gets ⌈(N-1)/2⌉, right gets ⌊(N-1)/2⌋.
The middle key moves to parent.

Temp: [30, 50, 70, 80] (4 keys, 5 children)
Split: Left gets keys [30], Right gets keys [70, 80]
Promote: key 50 to parent

Before parent split:               After:
    [30, 50, 70]                       [30, 50, 70]  ← old root (full)
   /    |    |    \                         ↑ add 50? STILL FULL!
  ...  ...  ...  ...                        |

Better example — let me be clearer:
```

Let me redo this with a real cascade:

```
Before any insert:

                [100]                        ← root (1 key, 2 children)
               /     \
     [30, 60]           [150, 200]            ← internal nodes (2 keys each)
    /    |    \         /    |    \
  [10,20][40,50][70,80][110,130][160,180][210,230]  ← leaves

Insert 25:
  → Leaf [10,20] is now [10,20,25] → room for more, no split. Done.

Insert 45:
  → Leaf [40,50] is now [40,45,50] → room. Done.

Insert 55:
  → Leaf [40,45,50] is full (max 3 keys in this example).
  → Split: left=[40,45], right=[50,55], promote 50 to parent [30,60]
  → Parent [30,60] now gets key 50: → [30,50,60]
  → Parent has room (max 3 keys, now 3 used). Done.

Keep inserting more until parent [30,50,60] is also full...

Insert 65, then 35 (triggers another leaf split, promotes 40):
  → Parent [30,50,60] temp: [30,40,50,60] → FULL!
  → Split parent: left=[30], right=[50,60], promote 40 to root.
  
  Root [100] now has [40,100]. Room. Done.

Eventually, when root is full AND splits:
  → New root created with just the promoted key.
  → Tree height increases by 1.
  → This is the ONLY way the tree gets taller.
```

### Summary: The Split Algorithm

```java
void insertIntoNode(Page node, int key, RowId value) {
    if (node.isLeaf()) {
        if (node.hasRoom(1)) {
            node.insertSorted(key, value);
        } else {
            Page newLeaf = new Page(indexFilePath, nextPageNum);
            redistributeAndSplit(node, newLeaf, key, value);
            insertIntoParent(node.getParent(), node.lastKey(), newLeaf.pageNum);
        }
    } else {
        int childIdx = node.findChildIndex(key);
        Page child = pool.getPage(node.getChild(childIdx));
        insertIntoNode(child, key, value);
        // Child may have split — check if parent needs the new key
    }
}
```

---

## Operation 3: Delete (with Borrow/Merge)

```
delete(key):
  find leaf page
  remove key from leaf
  if leaf is < 50% full:
    try to borrow a key from a sibling
    if sibling is also at minimum:
      merge with sibling
      remove separator key from parent
      if parent falls below minimum:
        cascade borrow/merge up the tree
```

### Case A: Simple Delete

```
Before:  [10, 20, 30, 40]
Delete:  20
After:   [10, 30, 40]     ← 3/5 slots used (60%). Above 50% threshold. Done.
```

### Case B: Borrow from Right Sibling

```
Before:
    Left: [10, 20]           (2 keys, 40% — below 50%)
    Right: [40, 50, 60, 70]  (4 keys, 80% — has keys to spare)
    Parent separator: 30

Step 1: Move parent separator 30 into left leaf:
    Left: [10, 20, 30]

Step 2: Move first key from right sibling (40) up to parent:
    Parent separator becomes: 40

Step 3: Remove 40 from right sibling:
    Right: [50, 60, 70]

After:
    Left: [10, 20, 30]       (3 keys, 60% — healthy!)
    Right: [50, 60, 70]      (3 keys, 60% — still healthy)
    Parent separator: 40     (updated)
```

### Case C: Merge with Sibling

```
Before:
    Left: [10, 20]           (40% — below 50%)
    Right: [40]               (20% — also at minimum, can't borrow)
    Parent separator: 30

Step 1: Pull parent separator 30 down:
    Temp: [10, 20, 30, 40]

Step 2: Write all keys into left leaf, delete right leaf:
    Left: [10, 20, 30, 40]   (4 keys — merged)

Step 3: Remove separator 30 and right child pointer from parent
    Parent now has one less key and one less child

Step 4: If parent is now < 50% full, cascade the same logic up
```

### Case D: Root Shrinks

```
Before:
    Root: [50]               (1 key, 2 children)
    Left child: [10, 20]
    Right child: [60, 70]

Delete many keys until right child is merged into left child.
Parent (root) now has 0 keys and 1 child.

After:
    Old root deleted.
    The single remaining child becomes the new root.
    Tree height decreases by 1.
```

---

## Operation 4: Range Scan

Uses the leaf linked list — the killer feature of B+tree vs regular B-tree.

```
searchRange(startKey, endKey):
  find leaf page for startKey
  while page != null:
    for each key in page:
      if key > endKey: return results
      if key >= startKey:
        results.add(rowId)
    page = pool.getPage(page.nextLeafPage)
  return results
```

```sql
SELECT * FROM users WHERE id BETWEEN 100 AND 500;

1. Search for id=100 → find leaf page at key 100 (3 page reads)
2. Walk forward through the linked leaf chain:
   Leaf 1: [100, 105, 110, ..., 250] → all match
   Leaf 2: [250, 260, ..., 400] → all match
   Leaf 3: [400, 420, ..., 550] → collect up to 500, then STOP
3. Never touch internal nodes again — just follow nextLeafPage pointers
```

Without the leaf chain, you'd re-traverse from the root for every page. With it, range scans are one lookup + sequential page reads.

---

## Max Keys Per Page

For INT keys with INT values (or 4-byte page pointers):

```
Internal node:  8 bytes per entry (4B key + 4B child pointer)
                ~(8192 - 24) / 8 ≈ 1020 keys per page
                Fanout ≈ 1021 (N keys = N+1 children)

Leaf node:      8 bytes per entry (4B key + 4B RowId)
                ~(8192 - 32) / 8 ≈ 1020 entries per leaf
```

With fanout ~1000:
- 1,000 rows → height 1 (root is leaf)
- 1,000,000 rows → height 2 (root + one leaf level)
- 1,000,000,000 rows → height 3
- 1,000,000,000,000 rows → height 4

At most 3-4 page reads to find any row in any realistic table.

---

## Reusing Phase 1

```
Phase 1 component          How Phase 6 uses it
─────────────────────      ──────────────────────
Page (byte[8192])          B+tree node pages. Same header, different body layout.
BufferPool (LRU cache)     Caches index pages alongside heap pages.
PageId (file + pageNum)    Identifies index pages. "idx_users_id.idx" : page 3.
HeapFile (RandomAccessFile) Not used directly. We write a similar IndexFile that wraps RAF.

New:
IndexFile.java             Like HeapFile but for .idx files. readPage/writePage/appendPage.
BTreeIndex.java            search(), insert(), delete(), searchRange().
                           Contains the split/merge logic.
```

The B+tree IS a storage engine feature — same pages, same pool, different file format inside the page.

---

→ Previous: [B-tree & Fanout](btree.md)
→ Related: [Page](page.md) — the underlying data structure reused by B+tree nodes
→ Related: [Buffer Pool](buffer-pool.md) — caches both heap and index pages
