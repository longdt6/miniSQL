package com.minisql.engine.executor.operators;

import com.minisql.engine.executor.Operator;
import com.minisql.storage.Row;

import java.util.List;

/** Test double backed by a fixed list of rows, for isolating operator logic from real storage. */
class FakeOperator implements Operator {

    private final List<Row> rows;
    private int index;
    private boolean opened;

    FakeOperator(List<Row> rows) {
        this.rows = rows;
    }

    @Override
    public void open() {
        opened = true;
        index = 0;
    }

    @Override
    public Row next() {
        if (index < rows.size()) {
            return rows.get(index++);
        }
        return null;
    }

    @Override
    public void close() {
        opened = false;
    }

    boolean isOpened() {
        return opened;
    }
}
