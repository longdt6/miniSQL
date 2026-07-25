package com.minisql.engine.executor.operators;

import com.minisql.engine.executor.Operator;
import com.minisql.engine.parser.ast.Statement;
import com.minisql.storage.Row;

import java.util.*;

/**
 * Wraps a child operator, accumulates all rows, sorts them in memory, then emits.
 * Blocking operator: must consume ALL rows before emitting any.
 */
public class SortOperator implements Operator {

    private final Operator child;
    private final List<Statement.OrderBy> orderBy;
    private List<Row> sortedRows;
    private int index;

    public SortOperator(Operator child, List<Statement.OrderBy> orderBy) {
        this.child = child;
        this.orderBy = orderBy;
    }

    @Override
    public void open() {
        child.open();

        // Consume all rows
        sortedRows = new ArrayList<>();
        Row row;
        while ((row = child.next()) != null) {
            sortedRows.add(row);
        }

        // Sort in memory
        if (!orderBy.isEmpty()) {
            Comparator<Row> comparator = null;
            for (Statement.OrderBy ob : orderBy) {
                Comparator<Row> colCmp = (a, b) -> {
                    Object va = a.get(ob.columnName());
                    Object vb = b.get(ob.columnName());
                    if (va == null && vb == null) return 0;
                    if (va == null) return -1;
                    if (vb == null) return 1;
                    @SuppressWarnings("unchecked")
                    int c = ((Comparable<Object>) va).compareTo(vb);
                    return ob.ascending() ? c : -c;
                };
                comparator = (comparator == null) ? colCmp : comparator.thenComparing(colCmp);
            }
            sortedRows.sort(comparator);
        }

        index = 0;
    }

    @Override
    public Row next() {
        if (index < sortedRows.size()) {
            return sortedRows.get(index++);
        }
        return null;
    }

    @Override
    public void close() {
        sortedRows = null;
        index = 0;
    }
}
