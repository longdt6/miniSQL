package com.minisql.engine.executor;

import com.minisql.storage.Row;

/**
 * Volcano-style iterator for query execution.
 * Each operator implements open() → next() loop → close().
 */
public interface Operator {
    void open();
    Row next();
    void close();
}
