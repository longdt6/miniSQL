package com.minisql.engine.binder;

import com.minisql.types.DataType;

/**
 * Resolved expression with type information attached.
 */
public abstract class BoundExpression {

    public abstract DataType type();

    // ── Concrete variants ──────────────────────────────────────

    public static class ColumnRef extends BoundExpression {
        private final String name;
        private final DataType type;
        private final int columnIndex;

        public ColumnRef(String name, DataType type, int columnIndex) {
            this.name = name;
            this.type = type;
            this.columnIndex = columnIndex;
        }

        public String name() { return name; }
        public DataType type() { return type; }
        public int columnIndex() { return columnIndex; }
    }

    public static class Literal extends BoundExpression {
        private final Object value;
        private final DataType type;

        public Literal(Object value, DataType type) {
            this.value = value;
            this.type = type;
        }

        public Object value() { return value; }
        public DataType type() { return type; }
    }

    public static class Binary extends BoundExpression {
        private final BoundExpression left;
        private final String operator;
        private final BoundExpression right;
        private final DataType type;

        public Binary(BoundExpression left, String operator, BoundExpression right, DataType type) {
            this.left = left;
            this.operator = operator;
            this.right = right;
            this.type = type;
        }

        public BoundExpression left() { return left; }
        public String operator() { return operator; }
        public BoundExpression right() { return right; }
        public DataType type() { return type; }
    }

    // ── Factory methods ────────────────────────────────────────

    public static BoundExpression columnRef(String name, DataType type, int columnIndex) {
        return new ColumnRef(name, type, columnIndex);
    }

    public static BoundExpression binary(BoundExpression left, String operator,
                                          BoundExpression right, DataType resultType) {
        return new Binary(left, operator, right, resultType);
    }
}
