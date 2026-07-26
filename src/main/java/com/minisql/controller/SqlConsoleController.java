package com.minisql.controller;

import com.minisql.common.SqlException;
import com.minisql.engine.SqlEngine;
import com.minisql.engine.executor.ResultSet;
import com.minisql.storage.Catalog;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

        long start = System.currentTimeMillis();
        try {
            ResultSet rs = sqlEngine.execute(sql);
            long elapsed = System.currentTimeMillis() - start;
            model.addAttribute("elapsedMs", elapsed);
            if (rs.isSelect()) {
                model.addAttribute("columns", rs.getColumns());
                model.addAttribute("rows", rs.getRows());
            } else {
                model.addAttribute("affectedRows", rs.getAffectedRows());
            }
        } catch (SqlException e) {
            model.addAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage() != null ? e.getMessage() : "Unexpected error");
        }

        model.addAttribute("tables", catalog.getTableNames());
        return "index";
    }
}
