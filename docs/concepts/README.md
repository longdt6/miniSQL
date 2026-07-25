# Concepts

Fundamental database internals concepts, explained before jumping into implementation. Read these first, then tackle the phases.

## Reading Order

| # | Concept | File | Why it matters |
|---|---------|------|---------------|
| 1 | Page | [page.md](page.md) | The fundamental unit of disk I/O. Every database operation starts here. |
| 2 | Slotted-Page Layout | [slotted-page.md](slotted-page.md) | How variable-size rows fit into fixed-size pages. Used by PostgreSQL. |
| 3 | Heap File | [heap-file.md](heap-file.md) | A table's physical file on disk — a sequence of pages. |
| 4 | Buffer Pool | [buffer-pool.md](buffer-pool.md) | LRU cache that keeps hot pages in memory, avoiding disk I/O. |
| 5 | DataType System | [datatype.md](datatype.md) | How types encode/decode bytes, compare values, parse literals. Why polymorphism. |
| 6 | Row | [row.md](row.md) | In-memory row representation. The currency flowing through all operators. |
| 7 | Tuple Serialization | [tuple-serialization.md](tuple-serialization.md) | How Row ↔ byte[] using the DataType system. Binary tuple layout. |
| 8 | Catalog | [catalog.md](catalog.md) | Schema metadata: what tables exist, what columns they have. JSON persistence. |
| 9 | Row vs Column Storage | [row-vs-column.md](row-vs-column.md) | Why PostgreSQL/MySQL store rows together (OLTP) vs ClickHouse (OLAP). |
| 10 | Lexer | [lexer.md](lexer.md) | Tokenizing SQL: keywords, identifiers, numbers, strings, operators. |
| 11 | Parser | [parser.md](parser.md) | Recursive descent parsing: grammar rules as methods, precedence, AST nodes. |
| 12 | Binder | [binder.md](binder.md) | Semantic analysis: name resolution, type checking, star expansion. |
| 13 | B-tree & Fanout | [btree.md](btree.md) | Why databases use B+trees — low height, high locality, lazy rebalancing. |
| 14 | B+tree Operations | [btree-operations.md](btree-operations.md) | Insert with split cascade, delete with borrow/merge, range scan via leaf chain. Phase 6 core algorithms. |
| 15 | Volcano Iterator Model | [volcano-model.md](volcano-model.md) | How query execution works: `open() → next() → close()` pipeline. |
| 16 | SQL Query Lifecycle | [query-lifecycle.md](query-lifecycle.md) | Full path: SQL string → Lexer → Parser → Binder → Planner → Executor → Result. |
| 17 | Wire Protocols | [wire-protocols.md](wire-protocols.md) | How applications talk to databases: HTTP/JSON vs binary protocols. |

## How This Connects to Implementation

```
Concepts              →     Implementation Phases
─────────────────────────────────────────────────
Page                  →     Phase 1, Step 4
Slotted-Page Layout   →     Phase 1, Step 4
Heap File             →     Phase 1, Step 5
Buffer Pool           →     Phase 1, Step 6
DataType System       →     Phase 1, Step 2
Row                   →     Phase 1, Step 3 + Phase 2
Tuple Serialization   →     Phase 1, Step 3
Catalog               →     Phase 1, Step 7
Row vs Column         →     Phase 1 design rationale
Lexer                 →     Phase 2, Step 1
Parser                →     Phase 2, Steps 2-3
Binder                →     Phase 2, Step 4
B-tree & Fanout       →     Phase 6 (future)
B+tree Operations     →     Phase 6 algorithms
Volcano Model         →     Phase 2, Step 6
Query Lifecycle       →     Phase 2 overview
Wire Protocols        →     Phase 3 (HTTP) / Phase 5 (binary, future)
```
