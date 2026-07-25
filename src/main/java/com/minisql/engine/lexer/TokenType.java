package com.minisql.engine.lexer;

/**
 * All token types the lexer can produce.
 */
public enum TokenType {
    // Keywords
    SELECT, FROM, WHERE, INSERT, INTO, VALUES, CREATE, TABLE, DROP,
    UPDATE, SET, DELETE, ORDER, BY, ASC, DESC, LIMIT,
    AND, OR, NOT, IF, EXISTS, SHOW, DESCRIBE, DESC_TABLE, TABLES,
    PRIMARY, KEY, INDEX, UNIQUE, ON, OFFSET,
    TRUE_KW, FALSE_KW, NULL_KW,

    // Identifiers and literals
    IDENTIFIER,
    INT_LITERAL,
    FLOAT_LITERAL,
    STRING_LITERAL,

    // Operators
    EQ, NEQ, LT, GT, LTE, GTE,  // = != <> < > <= >=
    PLUS, MINUS,
    STAR, SLASH,

    // Punctuation
    LPAREN, RPAREN, COMMA, SEMICOLON, DOT,

    // Special
    EOF
}
