package com.minisql.engine.lexer;

/**
 * A single token from the lexer: its type, optional value, and source position.
 */
public record Token(TokenType type, String value, int line, int column) {

    public static Token keyword(TokenType type, int line, int col) {
        return new Token(type, null, line, col);
    }

    public static Token identifier(String name, int line, int col) {
        return new Token(TokenType.IDENTIFIER, name, line, col);
    }

    public static Token intLiteral(String value, int line, int col) {
        return new Token(TokenType.INT_LITERAL, value, line, col);
    }

    public static Token floatLiteral(String value, int line, int col) {
        return new Token(TokenType.FLOAT_LITERAL, value, line, col);
    }

    public static Token stringLiteral(String value, int line, int col) {
        return new Token(TokenType.STRING_LITERAL, value, line, col);
    }

    public static Token simple(TokenType type, int line, int col) {
        return new Token(type, null, line, col);
    }

    @Override
    public String toString() {
        if (value != null) {
            return type + "(" + value + ")";
        }
        return type.toString();
    }
}
