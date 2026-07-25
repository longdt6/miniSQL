# B-tree & Fanout

Why databases use B+trees for indexes: low height, high fanout, lazy rebalancing.

## The Problem: Finding One Row in a Million

```
Without an index:
  SELECT * FROM users WHERE id = 42;
  → Scan all 1,000,000 rows. O(n). Could be millions of page reads.

With a B+tree index on id:
  SELECT * FROM users WHERE id = 42;
  → Walk the tree. O(log n). ~2-3 page reads.
```

## Binary Tree vs B-tree

```
Binary Tree (fanout = 2):            B-tree (fanout = 3, simplified):
       [100]                               [30 | 60]
      /     \                             /    |    \
    [50]   [150]                      [10,20][40,50][70,80]
    /  \    /  \
  [25][75][125][175]                Real B-tree: fanout = ~200
```

A binary tree with 1,000,000 nodes has height ~20 (20 levels deep → 20 disk reads per lookup).

A B-tree with fanout 200 and 1,000,000 keys has height ~3 (root → internal → leaf → 2 disk reads if root is cached).

## Fanout

**Fanout** = how many children each node has.

```
One B-tree node (8KB page):
┌─────────────────────────────────────────┐
│ [5] [50] [100] [150] [200] ... [950]   │  ← ~200 keys
│  │    │     │     │     │         │     │
│  ▼    ▼     ▼     ▼     ▼         ▼     │
│ Pointers to 200+ child pages            │
└─────────────────────────────────────────┘

Each entry: 4 bytes (INT key) + 4 bytes (page pointer) = 8 bytes
→ ~1000 entries per 8KB page → fanout ≈ 1000 for INT keys
→ 1M rows: root (1000 children) → internal (1M/1000 = 1000 pages) → leaf
→ Height = 2 (3 with root)
```

High fanout means the tree is wide and shallow. Each level of the tree is one page — one disk read. Shallow tree = few disk reads.

## B+tree Specifically

```
B-tree:                            B+tree:
Keys in internal nodes             Keys in internal nodes are guides only
AND leaf nodes                     All data lives in leaf nodes
                                   Leaves are linked in order

          [30, 60]                           [30, 60]
         /    |    \                         /    |    \
      [10,20][40,50][70,80]              [10,20]→[40,50]→[70,80]
                                        ↑ linked list of leaves
```

The linked leaf chain is what makes range scans fast:
```sql
SELECT * FROM users WHERE id BETWEEN 100 AND 200;
-- Find leaf page for id=100 (one B+tree lookup)
-- Walk the linked leaf chain forward until id > 200
-- No need to touch internal nodes again
```

## Lazy Rebalancing

Binary trees (AVL, Red-Black) rebalance on almost every insert — rotations, color flips, constant work.

B+tree: insert goes to the correct leaf. If the leaf has room (which it usually does — hundreds of free slots), just insert in sorted order. Zero rebalancing.

Only when a leaf is completely full do you split it into two pages and promote the middle key to the parent. Splits are rare (1 in every ~200 inserts with fanout 200). And they never cascade more than a few levels because the tree is so shallow.

## What Databases Use

| Database | Index type |
|----------|-----------|
| PostgreSQL | B+tree (default, all indexes) |
| MySQL InnoDB | B+tree (clustered PK + secondary) |
| SQLite | B+tree (tables and indexes both stored this way) |
| Oracle | B+tree |
| **miniSQL** (Phase 6) | B+tree |

## Two Types of B+tree Index

### Clustered (Primary Key)
Tuples live inside the B+tree leaves, physically ordered by PK. `SELECT * WHERE id = 42` reads one leaf page.

### Secondary (Non-PK)
B+tree on `age`. Leaf stores `(age → primary_key)`. Then lookup the PK in the clustered index for the full row. Two tree lookups, still O(log n).

→ Previous: [Row vs Column Storage](row-vs-column.md)
→ Next: [Volcano Iterator Model](volcano-model.md)
