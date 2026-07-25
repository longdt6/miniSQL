# Wire Protocols

How applications talk to databases: HTTP/JSON vs binary protocols (PostgreSQL, MySQL).

## Option A: HTTP/JSON (what miniSQL uses)

```
Client (browser / curl)              miniSQL Server
───────────────┬───                   ────┬───────────
               │  POST /api/query          │
               │  {"sql": "SELECT ..."}    │
               │ ─────────────────────────►│
               │                           │  → Parse JSON
               │                           │  → Lexer → Parser → ...
               │                           │  → Serialize result as JSON
               │  {"success": true,        │
               │   "rows": [[1,"Alice"]]}  │
               │ ◄─────────────────────────│
               │                           │
Each request is independent. No persistent state between them.
```

**Pros:** Universal (every language has `curl`/`fetch`), human-readable, firewall-friendly, no special driver needed.

**Cons:** Text bloat (integer `42` = 2 bytes as text vs 4 bytes as binary), JSON parsing overhead on both ends, no persistent session state, no prepared statements, no server push.

## Option B: Binary Wire Protocol (PostgreSQL, MySQL)

```
Client (JDBC driver / psql)           PostgreSQL Server
───────────────┬───                    ────┬───────────
               │  TCP connect              │
               │ ─────────────────────────►│
               │  StartupMessage            │
               │  (user, database)          │
               │ ◄─────────────────────────│  AuthenticationOk
               │  ReadyForQuery             │  (session ready)
               │                           │
               │  Query 'Q'                │
               │  [SQL bytes + params]     │
               │ ─────────────────────────►│
               │                           │  → Parse SQL (once)
               │  RowDescription 'T'      │     cache plan
               │  [col names, types]       │  → Execute
               │ ◄─────────────────────────│  → Send binary rows
               │  DataRow 'D'              │
               │  [raw binary row data]    │  ... more rows...
               │ ◄─────────────────────────│
               │  CommandComplete 'C'      │
               │ ◄─────────────────────────│
               │  ReadyForQuery 'Z'        │
               │     (ready for next query) │
               │                           │
Persistent TCP connection. Session state maintained.
```

**Pros:** 4x smaller on the wire (binary integers, floats), zero JSON parsing overhead, persistent connection with session state, prepared statements (parse SQL once, execute many times with different params), server-to-client push (LISTEN/NOTIFY), query cancellation.

**Cons:** Complex to implement, requires a custom driver, not human-readable for debugging.

## The Size Difference in Practice

`SELECT * FROM users LIMIT 1000` — 1000 rows × 10 columns:

| | HTTP/JSON | PostgreSQL Binary |
|---|---|---|
| Integer `42` | `"42"` (2B) + JSON overhead (~5B) ≈ 7B | `0x0000002A` (4B) |
| Float `3.14` | `"3.14"` (4B) + JSON ≈ 9B | 8B (IEEE 754) |
| String `"hello"` | `"hello"` (7B with quotes) | `0x0005` + `hello` (7B) |
| Boolean `true` | `true` (4B) | `0x01` (1B) |
| NULL | `null` (4B) | 1 bit in null bitmap |
| **Total for 100K values** | **~600 KB** | **~150 KB** |

4x difference. At 10,000 queries/second, that's gigabytes per hour saved.

## Why miniSQL Uses HTTP

We picked HTTP because:
1. **Focus on database internals** — not protocol engineering
2. **Browser Web UI** — browsers only speak HTTP
3. **Debug with curl** — no special tools needed
4. **Phase 5 adds binary protocol later** — the engine doesn't change, we add a different I/O layer

The `SqlEngine.execute(sql)` call on the server side is identical whether SQL arrived via HTTP or a binary protocol. The transport is just wrapping — the SQL engine is what matters.

## PostgreSQL Wire Protocol (Phase 5)

When we add this, the architecture becomes:

```
Port 8080 (HTTP)    ← Web UI / curl    ──┐
                                            ├──→ SqlEngine.execute(sql)
Port 5433 (TCP)     ← psql / JDBC      ──┘
                      PostgreSQL           same engine,
                      wire protocol        two I/O layers
```

Each is a thin adapter: parse the protocol, extract SQL, call `engine.execute()`, serialize result in the protocol's format.

→ Previous: [SQL Query Lifecycle](query-lifecycle.md)
