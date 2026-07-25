package com.minisql.engine.executor.operators;

import com.minisql.engine.binder.BoundColumn;
import com.minisql.engine.executor.Operator;
import com.minisql.storage.Row;

import java.util.List;

/**
 * Wraps a child operator, stripping each row to only the requested columns.
 */
public class ProjectOperator implements Operator {

    private final Operator child;
    private final List<BoundColumn> columns;

    public ProjectOperator(Operator child, List<BoundColumn> columns) {
        this.child = child;
        this.columns = columns;
    }

    @Override
    public void open() {
        child.open();
    }

    @Override
    public Row next() {
        Row row = child.next();
        if (row == null) return null;

        // Create a new row with only the projected columns
        Row projected = new Row();
        for (BoundColumn col : columns) {
            Object value = row.get(col.name());
            projected.set(col.name(), value);
        }
        return projected;
    }

    @Override
    public void close() {
        child.close();
    }
}
