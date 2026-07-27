package com.minisql.storage;

import com.minisql.types.DataType;

/**
 * Metadata for one column in a table: its name, data type, and ordinal position.
 */
public record ColumnMetadata(String name, DataType dataType, int position) {

    @Override
    public String toString() {
        return name + ":" + dataType.getSqlName();
    }
}
