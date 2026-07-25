package com.minisql.engine;

import com.minisql.common.SqlException;
import com.minisql.engine.binder.Binder;
import com.minisql.engine.binder.BoundStatement;
import com.minisql.engine.executor.Executor;
import com.minisql.engine.executor.ResultSet;
import com.minisql.engine.lexer.Lexer;
import com.minisql.engine.lexer.Token;
import com.minisql.engine.parser.Parser;
import com.minisql.engine.parser.ast.Statement;
import com.minisql.engine.planner.PlanNode;
import com.minisql.engine.planner.Planner;
import com.minisql.storage.BufferPool;
import com.minisql.storage.Catalog;

import java.util.List;

/**
 * Facade for the entire SQL engine. A single entry point:
 * SQL string → ResultSet.
 *
 * Pipeline: Lexer → Parser → Binder → Planner → Executor.
 */
public class SqlEngine {

    private final Catalog catalog;
    private final BufferPool pool;
    private final Binder binder;
    private final Planner planner;
    private final Executor executor;

    public SqlEngine(Catalog catalog, BufferPool pool) {
        this.catalog = catalog;
        this.pool = pool;
        this.binder = new Binder(catalog);
        this.planner = new Planner();
        this.executor = new Executor(catalog, pool);
    }

    /**
     * Execute a single SQL statement and return the result.
     */
    public ResultSet execute(String sql) throws SqlException {
        // 1. Lex
        Lexer lexer = new Lexer(sql);
        List<Token> tokens = lexer.tokenize();

        if (tokens.size() <= 1) {
            // Empty statement
            return new ResultSet(List.of(), List.of(), 0);
        }

        // 2. Parse
        Parser parser = new Parser(new Lexer(sql)); // fresh lexer for parsing
        Statement stmt = parser.parseStatement();

        // 3. Bind
        BoundStatement bound = binder.bind(stmt);

        // 4. Plan
        PlanNode plan = planner.plan(bound);

        // 5. Execute
        return executor.execute(plan);
    }

    /**
     * Execute multiple semicolon-separated SQL statements.
     */
    public List<ResultSet> executeBatch(String sql) throws SqlException {
        String[] statements = sql.split(";");
        List<ResultSet> results = new java.util.ArrayList<>();
        for (String s : statements) {
            s = s.trim();
            if (!s.isEmpty()) {
                results.add(execute(s));
            }
        }
        return results;
    }
}
