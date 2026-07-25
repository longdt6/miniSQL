package com.minisql.engine.executor;

import java.util.*;

/**
 * Result of a SQL query. Contains column names, rows (as ordered maps),
 * and optionally an affected row count for DML.
 */
public class ResultSet {

    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final int affectedRows;

    public ResultSet(List<String> columns, List<Map<String, Object>> rows, int affectedRows) {
        this.columns = columns;
        this.rows = rows;
        this.affectedRows = affectedRows;
    }

    public List<String> getColumns() { return columns; }
    public List<Map<String, Object>> getRows() { return rows; }
    public int getRowCount() { return rows.size(); }
    public int getAffectedRows() { return affectedRows; }
    public boolean isSelect() { return affectedRows < 0; }

    @Override
    public String toString() {
        if (isSelect()) {
            StringBuilder sb = new StringBuilder();
            sb.append(columns).append("\n");
            for (Map<String, Object> row : rows) {
                sb.append("  ").append(row).append("\n");
            }
            sb.append("(").append(rows.size()).append(" row");
            if (rows.size() != 1) sb.append("s");
            sb.append(")");
            return sb.toString();
        }
        return affectedRows + " row" + (affectedRows != 1 ? "s" : "") + " affected";
    }
}
