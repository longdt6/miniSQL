# Phase 3: HTTP Server + REST API + Web UI

> **Status**: 🔜 Pending | **Depends on**: Phase 2 (SQL engine)

## Goal

Wrap the SQL engine in a Spring Boot HTTP server. Expose a REST API for executing SQL queries. Build a simple browser-based Web UI with a SQL editor and results table.

**Why Spring Boot?** The HTTP layer is just transport plumbing — it teaches nothing about databases. Spring Boot eliminates boilerplate (manual JSON parsing, routing, static file serving, error handling) so we focus on the SQL engine. Same `sqlEngine.execute(sql)` call on the inside — just cleaner wiring on the outside.

## Dependencies (added to pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.0</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

## Steps

### 1. Configuration — application.properties

```properties
# src/main/resources/application.properties
server.port=8080
spring.jackson.default-property-inclusion=non_null
```

### 2. QueryController — POST /api/query

```java
@RestController
@RequestMapping("/api")
public class QueryController {

    private final SqlEngine sqlEngine;

    public QueryController(SqlEngine sqlEngine) {
        this.sqlEngine = sqlEngine;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> executeQuery(@RequestBody QueryRequest request) {
        long start = System.currentTimeMillis();
        try {
            ResultSet rs = sqlEngine.execute(request.getSql());
            long elapsed = System.currentTimeMillis() - start;
            return ResponseEntity.ok(QueryResponse.from(rs, elapsed));
        } catch (SqlException e) {
            long elapsed = System.currentTimeMillis() - start;
            return ResponseEntity.badRequest()
                .body(QueryResponse.error(e.getMessage(), elapsed));
        }
    }
}

// DTOs
record QueryRequest(String sql) {}
record QueryResponse(
    boolean success,
    String type,              // "SELECT", "INSERT", etc.
    List<String> columns,
    List<List<Object>> rows,
    Integer rowCount,
    Integer affectedRows,
    String error,
    long elapsedMs
) { ... }
```

**Response (SELECT):**
```json
{
  "success": true,
  "type": "SELECT",
  "columns": ["id", "name", "age"],
  "rows": [
    [1, "Alice", 30],
    [3, "Carol", 35]
  ],
  "rowCount": 2,
  "elapsedMs": 2
}
```

**Response (INSERT/UPDATE/DELETE):**
```json
{
  "success": true,
  "type": "INSERT",
  "affectedRows": 1,
  "elapsedMs": 1
}
```

**Response (error):**
```json
{
  "success": false,
  "error": "Table 'users' not found",
  "elapsedMs": 0
}
```

### 3. TablesController

```java
@RestController
@RequestMapping("/api")
public class TablesController {

    private final Catalog catalog;

    @GetMapping("/tables")
    public ResponseEntity<Map<String, List<String>>> listTables() {
        return ResponseEntity.ok(Map.of("tables", catalog.getTableNames()));
    }

    @GetMapping("/tables/{name}")
    public ResponseEntity<?> describeTable(@PathVariable String name) {
        TableMetadata meta = catalog.getTable(name);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "name", meta.getName(),
            "columns", meta.getColumns().stream()
                .map(c -> Map.of("name", c.getName(), "type", c.getType().getSqlName()))
                .toList()
        ));
    }
}
```

### 4. Static Files — Web UI

Spring Boot serves static files from `src/main/resources/static/` automatically. Place the Web UI there:

```
src/main/resources/static/
├── index.html
├── style.css
└── app.js
```

No `StaticFileHandler` needed — Spring Boot handles it.

### 5. Web UI

Same design as before — single-page HTML + CSS + vanilla JS using `fetch()`:

```
┌─────────────────────────────────────────────────────┐
│  miniSQL                                  [Run ▶]   │
├─────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────┐   │
│  │ SELECT * FROM users WHERE age > 25           │   │
│  │ ORDER BY name LIMIT 10                       │   │
│  └──────────────────────────────────────────────┘   │
├────────────────────┬────────────────────────────────┤
│  Tables            │  Results                       │
│  ┌──────────────┐  │  ┌────┬───────┬─────┐         │
│  │ ▶ users      │  │  │ id │ name  │ age │         │
│  │ ▶ orders     │  │  ├────┼───────┼─────┤         │
│  └──────────────┘  │  │ 1  │ Alice │ 30  │         │
│                     │  │ 3  │ Carol │ 35  │         │
│                     │  └────┴───────┴─────┘         │
│                     │  2 rows in 2ms                 │
└─────────────────────┴────────────────────────────────┘
```

**Features:**
- SQL textarea with `Ctrl+Enter` to execute
- Results displayed as an HTML table
- Table list sidebar (refreshed from `GET /api/tables`)
- Click table name → auto-fills `SELECT * FROM <table> LIMIT 100`
- Error messages shown in red
- Timing display

**Tech:** HTML + CSS + vanilla JavaScript. No frontend framework. `fetch()` calls the REST API.

### 6. MiniSqlApplication — Entry Point

```java
@SpringBootApplication
public class MiniSqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniSqlApplication.class, args);
    }

    @Bean
    public Catalog catalog() {
        return new Catalog("data/mydb");
    }

    @Bean
    public BufferPool bufferPool() {
        return new BufferPool(200);
    }

    @Bean
    public SqlEngine sqlEngine(Catalog catalog, BufferPool bufferPool) {
        return new SqlEngine(catalog, bufferPool);
    }

    @PreDestroy
    public void shutdown(BufferPool bufferPool, Catalog catalog) {
        bufferPool.flushAll();
        catalog.close();
    }
}
```

That's it. Three beans wired via Spring DI. `@SpringBootApplication` handles everything: embedded Tomcat on port 8080, JSON via Jackson, static file serving, threading.

### 7. Package Structure (updated for Phase 3)

```
com.minisql
├── MiniSqlApplication.java       // @SpringBootApplication entry point
├── controller
│   ├── QueryController.java      // POST /api/query
│   └── TablesController.java     // GET /api/tables, /api/tables/{name}
├── dto
│   ├── QueryRequest.java         // { sql: "..." }
│   └── QueryResponse.java        // success, columns, rows, error, elapsedMs
├── engine/                       // (same as Phase 2)
├── storage/                      // (same as Phase 1)
├── types/                        // (same as Phase 1)
└── common/                       // (same as Phase 1)
```

## Verification

```bash
# Build and run
mvn package -DskipTests
java -jar target/minisql.jar
# → Tomcat started on port(s): 8080

# Test REST API
curl -X POST localhost:8080/api/query \
  -H 'Content-Type: application/json' \
  -d '{"sql": "CREATE TABLE users (id INTEGER, name TEXT, age INTEGER)"}'

curl -X POST localhost:8080/api/query \
  -H 'Content-Type: application/json' \
  -d "{\"sql\": \"INSERT INTO users VALUES (1, 'Alice', 30)\"}"

curl -X POST localhost:8080/api/query \
  -H 'Content-Type: application/json' \
  -d '{"sql": "SELECT * FROM users"}'

# List tables
curl localhost:8080/api/tables

# Describe table
curl localhost:8080/api/tables/users

# Open Web UI
open http://localhost:8080
```
