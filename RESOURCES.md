# Learning Resources for Building a Database

Curated roadmap of the best resources for understanding how relational databases work under the hood — sorted by depth and topic.

---

## Books (ordered by relevance to this project)

### 1. Database Internals — Alex Petrov (2019)
The single best book for this project. It covers exactly what we're building:
- **Part 1: Storage Engines** — B-trees, LSM trees, slotted pages, write-ahead logs, buffer pools. Maps directly to our `storage/` and `types/` packages.
- **Part 2: Distributed Systems** — replication, consensus, partitioning. Save for later.

> Start with chapters 1–5 (storage engine).

### 2. Designing Data-Intensive Applications — Martin Kleppmann (2017)
Broader in scope but incredibly well-written. Chapters relevant to us:
- **Ch 3: Storage and Retrieval** — hash indexes, SSTables, B-trees, OLTP vs OLAP. Explains *why* databases organize data the way they do.
- **Ch 4: Encoding and Evolution** — how data gets serialized (Avro, Parquet, etc.). Complements our TupleSerializer.

### 3. Database System Concepts — Silberschatz, Korth, Sudarshan (7th ed)
The classic "dinosaur book" used in most university database courses. Dense but authoritative:
- **Ch 12–14**: Query processing, query optimization, physical storage
- **Ch 15–16**: Transactions, concurrency control

> Use as a reference, not a cover-to-cover read.

### 4. SQLite Database System: Design and Implementation — Sibsankar Haldar
Deep dive into SQLite's actual source code. Architecturally very similar to what we're building (embedded, single-file storage, slotted pages, B-trees). Great for seeing how a real production DB handles the same problems.

---

## Video Courses

### CMU 15-445/645: Intro to Database Systems — Andy Pavlo
The gold standard. All lectures free on YouTube. Pavlo is an incredible teacher.

- [CMU Database Group YouTube](https://www.youtube.com/@CMUDatabaseGroup)

| Lecture | Topic | Maps to our project |
|---------|-------|---------------------|
| 2–3 | Storage, buffer pool, page layout | `storage/` package |
| 5–6 | Hash tables, B+ trees | Phase 2 (indexes) |
| 7–8 | Query execution, Volcano model | `executor/operators/` |
| 9–10 | Query optimization, cost models | `planner/` |
| 11–12 | Joins algorithms | Phase 2 (joins) |

### CMU 15-721: Advanced Database Systems
For later — deeper query optimization, compilation, modern storage models.

---

## Hands-On Tutorials

### Let's Build a Simple Database — cstack
Builds a SQLite clone in C step-by-step. Concepts translate perfectly to Java:
REPL → lexer/parser → virtual machine → B-tree → disk persistence.
Walks through the exact same pipeline we're building.

- [cstack.github.io/db_tutorial](https://cstack.github.io/db_tutorial/)

### CodeCrafters: Build Your Own SQLite
Guided, test-driven approach to building SQLite features. They provide the tests; you write the code. Available in Java.

- [codecrafters.io](https://codecrafters.io/)

---

## Papers (free, short, high-impact)

### Architecture of a Database System — Hellerstein, Stonebraker, Hamilton (2007)
The single most important paper. Explains how components fit together: process model, query optimizer, executor, storage, buffer manager, transactions. We're modeling our architecture directly on this.

> ~20 pages. Find on Google Scholar.

### What Goes Around Comes Around — Stonebraker, Hellerstein (2005)
History of data models from IMS to XML to RDF. Not essential for building, but helps you understand *why* SQL won.

### SQLite Architecture — sqlite.org
A detailed walkthrough of SQLite's internals written by its creator (D. Richard Hipp). Covers the bytecode VM, B-tree layer, pager, and OS interface. Short and practical.

- [sqlite.org/arch.html](https://www.sqlite.org/arch.html)

---

## Source Code to Study

### H2 Database (Java — most relevant)
Pure Java embedded database, widely used. Small enough to understand (~150k lines), complete enough to study:
- `org.h2.store` — page store, file store
- `org.h2.command.dml` — SELECT, INSERT, UPDATE, DELETE
- `org.h2.command.ddl` — CREATE TABLE, etc.
- `org.h2.expression` — WHERE clause evaluation

- [github.com/h2database/h2database](https://github.com/h2database/h2database)

### SQLite (C — gold standard for clarity)
~150k lines of exceptionally clean C. The code *is* the documentation:
- `src/vdbe.c` — virtual machine / query execution
- `src/btree.c` — B-tree implementation
- `src/pager.c` — page cache / buffer pool
- `src/where.c` — query planner

- [sqlite.org/src](https://www.sqlite.org/src)

---

## Recommended Learning Path

```
Before Phase 1 (types + storage):
├── Database Internals, Chapters 1–3
├── CMU 15-445 Lectures 2–3
└── cstack's tutorial Parts 1–7

Before Phase 2 (lexer/parser/executor):
├── Database Internals, Chapters 4–5
├── CMU 15-445 Lectures 7–8
└── "Architecture of a Database System" paper

Before Phase 3 (HTTP + Web UI):
├── cstack's tutorial Parts 8–13
└── Skim H2 Database source code

Ongoing reference:
└── SQLite architecture docs
```

> Bottom line: read **Database Internals** ch 1–5 + watch first 8 CMU lectures. That covers every component in our plan.
