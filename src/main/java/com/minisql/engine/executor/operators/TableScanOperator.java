package com.minisql.engine.executor.operators;

import com.minisql.engine.executor.Operator;
import com.minisql.storage.*;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Scans all live tuples from a heap file, deserializes each into a Row.
 */
public class TableScanOperator implements Operator {

    private final TableMetadata table;
    private final BufferPool pool;
    private HeapFile hf;
    private TupleDesc desc;
    private HeapFile.TupleIterator iterator;

    public TableScanOperator(TableMetadata table, BufferPool pool) {
        this.table = table;
        this.pool = pool;
    }

    @Override
    public void open() {
        hf = new HeapFile(Paths.get(table.heapFilePath()), table.tableName());
        desc = TupleDesc.fromTable(table);
        iterator = hf.tupleIterator();
    }

    @Override
    public Row next() {
        if (iterator == null) return null;
        try {
            if (iterator.hasNext()) {
                byte[] tuple = iterator.next();
                return TupleSerializer.deserialize(tuple, desc);
            }
        } catch (Exception e) {
            throw new RuntimeException("Table scan error", e);
        }
        return null;
    }

    @Override
    public void close() {
        // Nothing to release
    }
}
