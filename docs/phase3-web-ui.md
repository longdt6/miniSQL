# Phase 3: Web UI (Thymeleaf)

> **Status**: 🔜 Pending | **Depends on**: Phase 2 (SQL engine)

> **Decision [2026-07-26]**: Replaces the earlier REST API + vanilla-JS plan (former Phase 3) and folds in what used to be Phase 4 (error handling, `SHOW TABLES`/`DESCRIBE`, polish). Those Phase 4 extras are dropped from scope for now, not carried into this phase — revisit later if needed. Phase 5 (PostgreSQL wire protocol) is unaffected and stays skipped/future.

## Goal

A single server-rendered web page where a user types SQL, submits it, and sees a results table — driven directly by `SqlEngine.execute()`. No REST API, no separate JSON layer, no frontend JavaScript framework.

**Why Thymeleaf instead of REST + JS?** Thymeleaf renders HTML on the server: a controller returns a view name plus a `Model`, and Spring fills in the template before sending it to the browser. That means one code path (Java) owns both the logic and the page — no DTOs to keep in sync with a JS renderer, no `fetch()`/JSON contract to maintain.

**Tradeoff accepted:** with no JavaScript, running a query is a normal HTML form `POST` — the whole page reloads each time (this decision was made explicitly; an alternative using HTMX for partial updates was considered and declined in favor of simplicity). This means:
- No `Ctrl+Enter`-to-run shortcut (that needs JS to intercept the keypress).
- The SQL textarea contents must be echoed back into the re-rendered form (from the submitted value), or the user's query text disappears on submit.
- Every query is a full page load — fine for a demo, noticeably slower than the JS version for rapid iteration.

## Dependencies (pom.xml)

Add the Thymeleaf starter alongside the existing web starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

`spring-boot-starter-web`, `spring-boot-starter-parent`, and `spring-boot-maven-plugin` are already present in `pom.xml` — no changes needed there beyond the `mainClass` pointing at `MiniSqlApplication` (see Step 4).

## Steps

### 1. SqlConsoleController — GET / and POST /query

One controller, two handlers, both returning the same view:

```java
@Controller
public class SqlConsoleController {

    private final SqlEngine sqlEngine;
    private final Catalog catalog;

    public SqlConsoleController(SqlEngine sqlEngine, Catalog catalog) {
        this.sqlEngine = sqlEngine;
        this.catalog = catalog;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("tables", catalog.getTableNames());
        return "index";
    }

    @PostMapping("/query")
    public String runQuery(@RequestParam String sql, Model model) {
        model.addAttribute("sql", sql);
        model.addAttribute("tables", catalog.getTableNames());

        long start = System.currentTimeMillis();
        try {
            ResultSet rs = sqlEngine.execute(sql);
            model.addAttribute("elapsedMs", System.currentTimeMillis() - start);
            if (rs.isSelect()) {
                model.addAttribute("columns", rs.getColumns());
                model.addAttribute("rows", rs.getRows());
            } else {
                model.addAttribute("affectedRows", rs.getAffectedRows());
            }
        } catch (SqlException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "index";
    }
}
```

No DTOs, no JSON — `ResultSet.getRows()` (`List<Map<String,Object>>`) is passed straight into the model; Thymeleaf iterates the map directly in the template.

### 2. Template — src/main/resources/templates/index.html

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>miniSQL</title>
    <link rel="stylesheet" th:href="@{/style.css}">
</head>
<body>
    <header><h1>miniSQL</h1></header>
    <main>
        <form method="post" th:action="@{/query}">
            <textarea name="sql" th:text="${sql}" placeholder="SELECT * FROM users"></textarea>
            <button type="submit">Run</button>
        </form>

        <div th:if="${error}" class="error" th:text="${error}"></div>

        <div th:if="${columns}">
            <table>
                <thead><tr><th th:each="col : ${columns}" th:text="${col}"></th></tr></thead>
                <tbody>
                    <tr th:each="row : ${rows}">
                        <td th:each="col : ${columns}" th:text="${row.get(col)}"></td>
                    </tr>
                </tbody>
            </table>
            <p th:text="${rows.size()} + ' row(s) in ' + ${elapsedMs} + 'ms'"></p>
        </div>

        <div th:if="${affectedRows != null}">
            <p th:text="${affectedRows} + ' row(s) affected in ' + ${elapsedMs} + 'ms'"></p>
        </div>

        <aside>
            <h2>Tables</h2>
            <ul>
                <li th:each="t : ${tables}" th:text="${t}"></li>
            </ul>
        </aside>
    </main>
</body>
</html>
```

Static assets (`style.css`) still live in `src/main/resources/static/` — Spring Boot serves `templates/` and `static/` side by side without conflict.

### 3. Package structure (updated)

```
com.minisql
├── MiniSqlApplication.java
├── controller/
│   └── SqlConsoleController.java   // GET /, POST /query — replaces QueryController/TablesController
├── engine/                          // (same as Phase 2)
├── storage/                         // (same as Phase 1)
├── types/                           // (same as Phase 1)
└── common/                          // (same as Phase 1)
```

No `dto/` package — Thymeleaf renders directly from `ResultSet`/`Catalog`, so `QueryRequest`/`QueryResponse` records are unnecessary and should be removed.

### 4. MiniSqlApplication — Entry Point

Unchanged from the original plan: `@SpringBootApplication` with three `@Bean`s (`Catalog`, `BufferPool`, `SqlEngine`) and a `@PreDestroy` shutdown hook calling `bufferPool.flushAll()` / `catalog.close()`. `pom.xml`'s `spring-boot-maven-plugin` `mainClass` must point at `com.minisql.MiniSqlApplication`.

## Verification

```bash
mvn package -DskipTests
java -jar target/minisql.jar
open http://localhost:8080
```

1. Load `/` — empty form, table sidebar reflects current `data/mydb` state.
2. Submit `CREATE TABLE users (id INTEGER, name TEXT, age INTEGER)` — page reloads, sidebar now shows `users`.
3. Submit an `INSERT`, then a `SELECT * FROM users` — confirm results table renders, submitted SQL text is preserved in the textarea.
4. Submit an intentionally bad statement (e.g. `SELECT * FROM nope`) — confirm the error message renders (not a raw stack trace / Spring error page).
5. Restart the server — confirm `data/mydb/catalog.json` and table data persisted across restart.
