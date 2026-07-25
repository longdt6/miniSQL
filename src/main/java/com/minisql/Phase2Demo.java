package com.minisql;

import com.minisql.engine.SqlEngine;
import com.minisql.engine.executor.ResultSet;
import com.minisql.storage.BufferPool;
import com.minisql.storage.Catalog;

import java.util.Map;

/**
 * End-to-end demonstration of Phase 2: the SQL engine.
 *
 * Lexer → Parser → Binder → Planner → Executor → ResultSet.
 */
public class Phase2Demo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== miniSQL Phase 2: SQL Engine Demo ===\n");

        String dataDir = "data/mydb";
        Catalog catalog = new Catalog(dataDir);
        BufferPool pool = new BufferPool(200);
        SqlEngine engine = new SqlEngine(catalog, pool);

        // ── DDL ──────────────────────────────────────────────────
        System.out.println("1. CREATE TABLE users (id INTEGER, name TEXT, age INTEGER)");
        engine.execute("CREATE TABLE users (id INTEGER, name TEXT, age INTEGER)");
        System.out.println("   -> Table created\n");

        // ── INSERT ──────────────────────────────────────────────
        System.out.println("2. INSERT 3 rows");
        ResultSet r = engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
        System.out.println("   " + r);
        r = engine.execute("INSERT INTO users VALUES (2, 'Bob', 25)");
        System.out.println("   " + r);
        r = engine.execute("INSERT INTO users VALUES (3, 'Carol', 35)");
        System.out.println("   " + r);
        System.out.println();

        // ── SELECT * ────────────────────────────────────────────
        System.out.println("3. SELECT * FROM users");
        r = engine.execute("SELECT * FROM users");
        printResult(r);

        // ── SELECT with WHERE ───────────────────────────────────
        System.out.println("4. SELECT name, age FROM users WHERE age > 25");
        r = engine.execute("SELECT name, age FROM users WHERE age > 25");
        printResult(r);

        // ── SELECT with ORDER BY + LIMIT ────────────────────────
        System.out.println("5. SELECT * FROM users ORDER BY age DESC LIMIT 2");
        r = engine.execute("SELECT * FROM users ORDER BY age DESC LIMIT 2");
        printResult(r);

        // ── UPDATE ──────────────────────────────────────────────
        System.out.println("6. UPDATE users SET age = 31 WHERE id = 1");
        r = engine.execute("UPDATE users SET age = 31 WHERE id = 1");
        System.out.println("   " + r + "\n");

        // ── DELETE ──────────────────────────────────────────────
        System.out.println("7. DELETE FROM users WHERE name = 'Bob'");
        r = engine.execute("DELETE FROM users WHERE name = 'Bob'");
        System.out.println("   " + r + "\n");

        // ── Final scan ──────────────────────────────────────────
        System.out.println("8. SELECT * FROM users (final)");
        r = engine.execute("SELECT * FROM users");
        printResult(r);

        // ── SHOW TABLES ─────────────────────────────────────────
        System.out.println("9. SHOW TABLES");
        r = engine.execute("SHOW TABLES");
        printResult(r);

        // ── DESCRIBE ────────────────────────────────────────────
        System.out.println("10. DESCRIBE users");
        r = engine.execute("DESCRIBE users");
        printResult(r);

        // ── Error handling ──────────────────────────────────────
        System.out.println("11. Error handling:");
        try { engine.execute("SELECT * FROM nonexistent"); }
        catch (Exception e) { System.out.println("   ✗ " + e.getMessage()); }

        try { engine.execute("SELECT bad_column FROM users"); }
        catch (Exception e) { System.out.println("   ✗ " + e.getMessage()); }

        try { engine.execute("INSERT INTO users VALUES (1)"); }
        catch (Exception e) { System.out.println("   ✗ " + e.getMessage()); }

        // ── DROP ────────────────────────────────────────────────
        System.out.println("\n12. DROP TABLE users");
        r = engine.execute("DROP TABLE users");
        System.out.println("   -> " + r);

        pool.flushAll();
        System.out.println("\n=== Phase 2 Demo Complete ===");
    }

    private static void printResult(ResultSet r) {
        if (!r.isSelect()) {
            System.out.println("   " + r + "\n");
            return;
        }

        // Print header
        for (String col : r.getColumns()) {
            System.out.printf("%-10s", col);
        }
        System.out.println();

        // Print separator
        for (int i = 0; i < r.getColumns().size(); i++) {
            System.out.print("----------");
        }
        System.out.println();

        // Print rows
        for (Map<String, Object> row : r.getRows()) {
            for (String col : r.getColumns()) {
                Object val = row.get(col);
                System.out.printf("%-10s", val != null ? val.toString() : "NULL");
            }
            System.out.println();
        }
        System.out.printf("(%d row%s)\n\n", r.getRowCount(), r.getRowCount() != 1 ? "s" : "");
    }
}
