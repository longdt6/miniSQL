# Phase 5 (Future): PostgreSQL Wire Protocol

> **Status**: 📅 Future | **Decision**: [2026-07-24] Skipped for now to focus on database internals. HTTP + Web UI is the current protocol. Revisit once Phases 1–4 are stable.

## Why

Adding a binary wire protocol means you can connect to miniSQL using real PostgreSQL tools (`psql`, JDBC drivers, etc.). The engine stays unchanged — the wire protocol is just another I/O layer wrapping `SqlEngine.execute()`.

## Where the Complexity Lives

- **ByteBuffer encoding/decoding** — hand-serialize every message type, type OIDs, null bitmaps. Boring but necessary.
- **Message state machine** — Startup → Authentication → Idle → Query → Response → Idle. Genuinely educational about network protocols.
- **Two protocol paths** — keep HTTP for the Web UI, add TCP listener on port 5433 for `psql`. Each new feature needs both serialization paths.

## Minimal Scope

1. **Message framing**: read message type byte + 4-byte length + payload
2. **Startup message handler** — no auth, just accept any connection
3. **Simple query flow**: `Query` → `RowDescription` → `DataRow`(s) → `CommandComplete` → `ReadyForQuery`
4. **Column type OID mapping**: INT → OID 23 (INT4), TEXT → OID 25, FLOAT → OID 701, BOOL → OID 16
5. **Text-format results only** — skip binary format (text format is what `psql` defaults to)

## PostgreSQL Wire Protocol — Message Flow

### Connection startup
```
Client → Server:  StartupMessage (protocol 3.0, user, database)
Server → Client:  AuthenticationOk
Server → Client:  ParameterStatus ("server_version", "client_encoding", ...)
Server → Client:  BackendKeyData (pid + cancel key)
Server → Client:  ReadyForQuery (status: idle)
```

### Simple query
```
Client → Server:  Query ('Q') [length] ["SELECT * FROM users\0"]
                  ↓
Server → Client:  RowDescription ('T')
                    [col count: 2B]
                    [col name: "id\0"]
                    [table OID: 0]
                    [col attr: 0]
                    [type OID: 23 (INT4)]
                    [type size: 4]
                    [type modifier: -1]
                    [format: 0 (text)]
                    [col name: "name\0"] ...
                  ↓
Server → Client:  DataRow ('D')
                    [col count: 2B]
                    [col 0 len: 4B] [col 0 value: "1"]
                    [col 1 len: 4B] [col 1 value: "Alice"]
                  ... (one per row)
                  ↓
Server → Client:  CommandComplete ('C') ["SELECT 1\0"]
Server → Client:  ReadyForQuery ('Z') [status: 'I' (idle)]
```

## Connection Architecture

```
Port 8080 (HTTP)  ← Web UI / curl    ──→ SqlEngine.execute()
Port 5433 (TCP)   ← psql / JDBC      ──→ SqlEngine.execute()
                                          ↑ same engine,
                                          different I/O layer
```

A `PgProtocolHandler` runs in its own thread, accepting TCP connections on port 5433. Each connection gets a `Session` that wraps `SqlEngine`. The handler reads PostgreSQL messages, translates them to SQL strings, calls `engine.execute()`, and serializes `ResultSet` back as `RowDescription` + `DataRow` messages.

## Verification

```bash
psql -h localhost -p 5433 -U user mydb
mydb=> CREATE TABLE users (id INTEGER, name TEXT, age INTEGER);
CREATE TABLE
mydb=> INSERT INTO users VALUES (1, 'Alice', 30);
INSERT 0 1
mydb=> SELECT * FROM users;
 id | name  | age
----+-------+-----
  1 | Alice |  30
(1 row)

mydb=> SELECT * FROM users WHERE age > 25 ORDER BY name;
 id | name  | age
----+-------+-----
  1 | Alice |  30
(1 row)

mydb=> \dt
         List of relations
 Schema | Name  | Type  | Owner
--------+-------+-------+-------
 public | users | table | user
(1 row)
```
