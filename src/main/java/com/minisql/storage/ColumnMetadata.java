package com.minisql.storage;

import com.minisql.types.DataType;

/**
 * Metadata for one column in a table: its name, data type, and ordinal position.
 */
public class ColumnMetadata {

    private final String name;
    private final DataType dataType;
    private final int position;

    public ColumnMetadata(String name, DataType dataType, int position) {
        this.name = name;
        this.dataType = dataType;
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public DataType getDataType() {
        return dataType;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return name + ":" + dataType.getSqlName();
    }
}
