package com.minisql.storage;

import com.minisql.types.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Metadata for one table: its name, the list of columns, and the associated heap file path.
 * Also provides lookup helpers used by the binder and planner.
 */
public class TableMetadata {

    private final int tableId;
    private final String tableName;
    private final String heapFilePath;
    private final List<ColumnMetadata> columns;

    public TableMetadata(int tableId, String tableName, String heapFilePath, List<ColumnMetadata> columns) {
        this.tableId = tableId;
        this.tableName = tableName;
        this.heapFilePath = heapFilePath;
        this.columns = new ArrayList<>(columns);
    }

    public int getTableId() {
        return tableId;
    }

    public String getTableName() {
        return tableName;
    }

    public String getHeapFilePath() {
        return heapFilePath;
    }

    public List<ColumnMetadata> getColumns() {
        return new ArrayList<>(columns);
    }

    public int getColumnCount() {
        return columns.size();
    }

    /** Find a column by name (case-insensitive). */
    public Optional<ColumnMetadata> getColumn(String name) {
        return columns.stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /** Get the column at a given positional index. */
    public ColumnMetadata getColumn(int position) {
        return columns.get(position);
    }

    /** Get the index of a column by name, or -1 if not found. */
    public int getColumnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return tableName + "(" + columns + ")";
    }
}
