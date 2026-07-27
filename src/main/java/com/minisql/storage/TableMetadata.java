package com.minisql.storage;

import java.util.List;
import java.util.Optional;

/**
 * Metadata for one table: its name, the list of columns, and the associated heap file path.
 * Also provides lookup helpers used by the binder and planner.
 */
public record TableMetadata(int tableId, String tableName, String heapFilePath, List<ColumnMetadata> columns) {

    public TableMetadata(int tableId, String tableName, String heapFilePath, List<ColumnMetadata> columns) {
        this.tableId = tableId;
        this.tableName = tableName;
        this.heapFilePath = heapFilePath;
        this.columns = List.copyOf(columns);
    }

    /** Find a column by name (case-insensitive). */
    public Optional<ColumnMetadata> column(String name) {
        return columns.stream()
                .filter(c -> c.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /** Get the column at a given positional index. */
    public ColumnMetadata column(int position) {
        return columns.get(position);
    }

    /** Get the index of a column by name, or -1 if not found. */
    public int columnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    public int columnCount() {
        return columns.size();
    }

    @Override
    public String toString() {
        return tableName + "(" + columns + ")";
    }
}
