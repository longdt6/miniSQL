package com.minisql.storage;

import com.minisql.types.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the schema of a tuple: column names, types, and positions.
 * Used by TupleSerializer to know what to expect when encoding/decoding.
 */
public class TupleDesc {

    private final List<ColumnMetadata> columns;

    public TupleDesc(List<ColumnMetadata> columns) {
        this.columns = new ArrayList<>(columns);
    }

    /** Create a TupleDesc from a TableMetadata snapshot. */
    public static TupleDesc fromTable(TableMetadata table) {
        return new TupleDesc(table.getColumns());
    }

    public List<ColumnMetadata> getColumns() {
        return new ArrayList<>(columns);
    }

    public int getColumnCount() {
        return columns.size();
    }

    public ColumnMetadata getColumn(int position) {
        return columns.get(position);
    }

    public DataType getColumnType(int position) {
        return columns.get(position).getDataType();
    }

    public String getColumnName(int position) {
        return columns.get(position).getName();
    }

    @Override
    public String toString() {
        return columns.toString();
    }
}
