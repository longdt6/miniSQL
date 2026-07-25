package com.minisql.storage;

import java.util.*;

/**
 * In-memory representation of a database row. A mapping from column names to Java values.
 * Uses LinkedHashMap to preserve column order (matches CREATE TABLE definition).
 */
public class Row {

    private final Map<String, Object> values;

    public Row() {
        this.values = new LinkedHashMap<>();
    }

    public void set(String columnName, Object value) {
        values.put(columnName, value);
    }

    public Object get(String columnName) {
        return values.get(columnName);
    }

    public int getInt(String columnName) {
        return (int) values.get(columnName);
    }

    public String getString(String columnName) {
        return (String) values.get(columnName);
    }

    public double getFloat(String columnName) {
        return (double) values.get(columnName);
    }

    public boolean getBoolean(String columnName) {
        return (boolean) values.get(columnName);
    }

    public int size() {
        return values.size();
    }

    public Set<String> getColumnNames() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public List<Object> getValues() {
        return new ArrayList<>(values.values());
    }

    public boolean hasColumn(String name) {
        return values.containsKey(name);
    }

    @Override
    public String toString() {
        return values.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Row row)) return false;
        return values.equals(row.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
