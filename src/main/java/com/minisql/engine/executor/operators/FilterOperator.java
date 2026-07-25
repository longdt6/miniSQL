package com.minisql.engine.executor.operators;

import com.minisql.engine.binder.BoundExpression;
import com.minisql.engine.executor.Operator;
import com.minisql.storage.Row;

/**
 * Wraps a child operator, only emitting rows that satisfy a WHERE predicate.
 */
public class FilterOperator implements Operator {

    private final Operator child;
    private final BoundExpression predicate;

    public FilterOperator(Operator child, BoundExpression predicate) {
        this.child = child;
        this.predicate = predicate;
    }

    @Override
    public void open() { child.open(); }

    @Override
    public Row next() {
        while (true) {
            Row row = child.next();
            if (row == null) return null;
            if (evaluate(row)) return row;
        }
    }

    @Override
    public void close() { child.close(); }

    private boolean evaluate(Row row) {
        return evaluateNode(predicate, row);
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateNode(BoundExpression expr, Row row) {
        if (expr instanceof BoundExpression.Binary bin) {
            Object leftVal = evalOperand(bin.left(), row);
            Object rightVal = evalOperand(bin.right(), row);
            return compare(leftVal, rightVal, bin.operator());
        }
        if (expr instanceof BoundExpression.ColumnRef cr) {
            Object val = row.get(cr.name());
            return val instanceof Boolean b && b;
        }
        if (expr instanceof BoundExpression.Literal lit) {
            return lit.value() instanceof Boolean b && b;
        }
        return false;
    }

    private Object evalOperand(BoundExpression expr, Row row) {
        if (expr instanceof BoundExpression.Literal lit) return lit.value();
        if (expr instanceof BoundExpression.ColumnRef cr) return row.get(cr.name());
        if (expr instanceof BoundExpression.Binary bin) return evaluateNode(bin, row);
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean compare(Object left, Object right, String op) {
        if (left == null || right == null) return "!=".equals(op);
        if (left instanceof Comparable l && right instanceof Comparable r) {
            int cmp = l.compareTo(r);
            if ("=".equals(op))  return cmp == 0;
            if ("!=".equals(op)) return cmp != 0;
            if ("<".equals(op))  return cmp < 0;
            if (">".equals(op))  return cmp > 0;
            if ("<=".equals(op)) return cmp <= 0;
            if (">=".equals(op)) return cmp >= 0;
            if ("AND".equals(op)) return Boolean.TRUE.equals(left) && Boolean.TRUE.equals(right);
            if ("OR".equals(op))  return Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right);
        }
        return false;
    }
}
