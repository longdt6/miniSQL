# miniSQL — Architecture & Implementation Plan

Build a mini SQL database server in **Java** for learning how relational databases work internally (like PostgreSQL/MySQL). The server exposes SQL via a **REST API** and includes a simple **browser-based Web UI**. Start with **Core DML**: CREATE TABLE, INSERT, SELECT, UPDATE, DELETE, DROP TABLE.

## 📚 Prerequisites

Before reading the implementation phases, start with the concept docs:

→ **[docs/concepts/README.md](docs/concepts/README.md)** — Index of all concept docs with recommended reading order.

These explain **what** each component is and **why** it works that way. The phase docs explain **how** to build it.

## System Architecture

```
┌──────────────────────────────────────────────────────┐
│                    Browser (Web UI)                   │
│              HTML + CSS + vanilla JS                  │
│              /api/query  POST {sql}                   │
└───────────────────────┬──────────────────────────────┘
                        │ HTTP
┌───────────────────────▼──────────────────────────────┐
│  HTTP Server (Spring Boot + embedded Tomcat)        │
│    GET  /                → index.html                 │
│    POST /api/query       → SQL execution              │
│    GET  /api/tables      → list tables                │
│    GET  /api/tables/{n}  → describe table             │
└───────────────────────┬──────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────┐
│                  SQL Engine                           │
│   Lexer → Parser → Binder → Planner → Executor       │
│   (Volcano-style iterator model)                      │
└───────────────────────┬──────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────┐
│                Storage Engine                          │
│   Catalog ← BufferPool ← HeapFile ← Page (8KB)        │
│   TupleSerializer ← DataType hierarchy                │
└───────────────────────────────────────────────────────┘
```

## Package Structure

```
com.minisql
├── MiniSqlApplication.java       // @SpringBootApplication entry point
├── controller/
│   ├── QueryController.java      // POST /api/query
│   └── TablesController.java     // GET /api/tables, /api/tables/{name}
├── dto/
│   ├── QueryRequest.java         // { sql: "..." }
│   └── QueryResponse.java        // success, columns, rows, error, elapsedMs
├── engine/
│   ├── SqlEngine.java          // Facade
│   ├── lexer/                  // Tokenizer
│   ├── parser/ast/             // Parser + AST nodes
│   ├── binder/                 // Semantic analysis
│   ├── planner/                // AST → plan tree
│   └── executor/operators/     // Volcano operators
├── storage/                    // Page, HeapFile, BufferPool, Catalog
├── types/                      // DataType hierarchy
└── common/                     // SqlException, Constants
```

## Data Flow: Example SELECT

```
SQL: "SELECT name, age FROM users WHERE age > 25 ORDER BY name LIMIT 10"

1. Lexer → 18 tokens
2. Parser → SelectStmt { columns, table, where, orderBy, limit }
3. Binder → resolve table/columns, type-check
4. Planner → Limit → Sort → Project → Filter → TableScan
5. Executor → open/next/close pipeline, rows flow through operators
6. ResultSet → JSON → HTTP response
```

## REST API Design

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Web UI |
| `POST` | `/api/query` | Execute SQL `{"sql": "..."}` |
| `GET` | `/api/tables` | List tables |
| `GET` | `/api/tables/{name}` | Describe table |

## Implementation Phases

**Storage engine first (complete storage before SQL):**
| Phase | File | Status |
|-------|------|--------|
| **Phase 1** | [docs/phase1-storage.md](docs/phase1-storage.md) | ✅ Heap files, pages, buffer pool, catalog |
| **Phase 6** | [docs/phase6-btree-indexes.md](docs/phase6-btree-indexes.md) | 🔜 B+tree indexes |
| **Phase 2** | [docs/phase2-sql-engine.md](docs/phase2-sql-engine.md) | 📅 SQL engine (on top of complete storage) |

**UI & polish (after engine works):**
| Phase | File | Status |
|-------|------|--------|
| **Phase 3** | [docs/phase3-http-server.md](docs/phase3-http-server.md) | 📅 Web UI + HTTP API |
| **Phase 4** | [docs/phase4-integration.md](docs/phase4-integration.md) | 📅 Tests, error polish |
| **Phase 5** | [docs/phase5-wire-protocol.md](docs/phase5-wire-protocol.md) | 📅 PostgreSQL wire protocol (skip) |

> **Decision [2026-07-25]**: Order is 1 → 6 → 2. Complete the full storage engine (heap files + B+tree indexes) first, then build the SQL engine. Understanding how data is physically stored, indexed, and searched makes the SQL layer much clearer.

## Key Design Decisions

1. **Row-oriented storage (OLTP)** — contiguous tuples on disk. Optimized for whole-row reads/writes (`INSERT`, `SELECT * WHERE id = X`, `UPDATE`, `DELETE`). Models PostgreSQL/MySQL InnoDB.
2. **Hand-written parser** — more educational than ANTLR, you see every token and production rule.
3. **Volcano iterator model** — `open() → next() → close()` pipeline. Classic database execution model.
4. **Slotted-page storage** — 8KB pages, slot directory + tuple data growing from opposite ends. Same layout as PostgreSQL.
5. **Spring Boot + Jackson** — HTTP layer is just transport plumbing. Spring Boot handles routing, JSON, static files, threading. Focus remains on the SQL engine. Database internals are pure Java; only the HTTP wrapper uses a framework.
6. **Buffer pool with LRU eviction** — caches hot pages in memory, evicts cold pages.
7. **Catalog as JSON** — schema metadata in `data/<db>/catalog.json`. Simple to inspect and debug.

## Verification

```bash
java -jar target/minisql.jar                         # Start server
curl -X POST localhost:8080/api/query \
  -d '{"sql":"SELECT 1"}'                            # HTTP query test
```

```sql
-- Web UI at http://localhost:8080
CREATE TABLE users (id INTEGER, name TEXT, age INTEGER);
INSERT INTO users VALUES (1, 'Alice', 30);
SELECT * FROM users;
SELECT name, age FROM users WHERE age > 25 ORDER BY age DESC LIMIT 2;
UPDATE users SET age = 31 WHERE id = 1;
DELETE FROM users WHERE name = 'Bob';
DROP TABLE users;
```
