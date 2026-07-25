package com.minisql.engine.executor.operators;

import com.minisql.engine.executor.Operator;
import com.minisql.storage.Row;

/**
 * Wraps a child operator, stops after emitting N rows.
 */
public class LimitOperator implements Operator {

    private final Operator child;
    private final int limit;
    private int count;

    public LimitOperator(Operator child, int limit) {
        this.child = child;
        this.limit = limit;
    }

    @Override
    public void open() {
        child.open();
        count = 0;
    }

    @Override
    public Row next() {
        if (count >= limit) return null;
        Row row = child.next();
        if (row != null) count++;
        return row;
    }

    @Override
    public void close() {
        child.close();
    }
}
