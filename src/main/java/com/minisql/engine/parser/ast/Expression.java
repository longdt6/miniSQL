package com.minisql.engine.parser.ast;

/**
 * Base for all expressions (WHERE clause, values, etc.).
 */
public abstract class Expression {

    public static class ColumnRef extends Expression {
        private final String name;
        public ColumnRef(String name) { this.name = name; }
        public String name() { return name; }
    }

    public static class Literal extends Expression {
        private final TokenValue value;
        public Literal(TokenValue value) { this.value = value; }
        public TokenValue value() { return value; }
    }

    public static class Binary extends Expression {
        private final Expression left;
        private final String operator;
        private final Expression right;

        public Binary(Expression left, String operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        public Expression left() { return left; }
        public String operator() { return operator; }
        public Expression right() { return right; }
    }

    public static class Star extends Expression {
        public Star() {}
    }

    /**
     * Typed literal value wrapper.
     */
    public static class TokenValue {
        private final Object value;
        private final TokenValueType type;

        public enum TokenValueType { INT, FLOAT, STRING, BOOLEAN, NULL }

        public TokenValue(Object value, TokenValueType type) {
            this.value = value;
            this.type = type;
        }

        public Object value() { return value; }
        public TokenValueType type() { return type; }

        public static TokenValue intValue(String s) { return new TokenValue(Integer.parseInt(s), TokenValueType.INT); }
        public static TokenValue floatValue(String s) { return new TokenValue(Double.parseDouble(s), TokenValueType.FLOAT); }
        public static TokenValue stringValue(String s) { return new TokenValue(s, TokenValueType.STRING); }
        public static TokenValue boolValue(boolean b) { return new TokenValue(b, TokenValueType.BOOLEAN); }
        public static TokenValue nullValue() { return new TokenValue(null, TokenValueType.NULL); }

        @Override
        public String toString() {
            return type == TokenValueType.NULL ? "NULL" : String.valueOf(value);
        }
    }
}
