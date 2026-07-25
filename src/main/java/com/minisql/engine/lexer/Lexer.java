package com.minisql.engine.lexer;

import com.minisql.common.SqlException;

import java.util.*;

/**
 * Hand-written SQL tokenizer. Converts a raw SQL string into a stream of tokens.
 *
 * Supports: keywords, identifiers, integers, floats, single-quoted strings,
 * operators (= != <> < > <= >=), punctuation, line comments (--), and
 * case-insensitive keywords.
 */
public class Lexer {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "CREATE",
        "TABLE", "DROP", "UPDATE", "SET", "DELETE", "ORDER", "BY",
        "ASC", "DESC", "LIMIT", "AND", "OR", "NOT", "IF", "EXISTS",
        "SHOW", "DESCRIBE", "TABLES", "TRUE", "FALSE", "NULL",
        "PRIMARY", "KEY", "INDEX", "UNIQUE", "ON"
    ));

    private final String sql;
    private int pos;
    private int line;
    private int column;
    private Token peeked;

    public Lexer(String sql) {
        this.sql = sql;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
        this.peeked = null;
    }

    // ── Public API ─────────────────────────────────────────────────

    public Token nextToken() throws SqlException {
        if (peeked != null) {
            Token t = peeked;
            peeked = null;
            return t;
        }
        return scanToken();
    }

    public Token peekToken() throws SqlException {
        if (peeked == null) {
            peeked = scanToken();
        }
        return peeked;
    }

    public List<Token> tokenize() throws SqlException {
        List<Token> tokens = new ArrayList<>();
        Token t;
        while ((t = nextToken()).type() != TokenType.EOF) {
            tokens.add(t);
        }
        tokens.add(t); // EOF
        return tokens;
    }

    // ── Core scanner ───────────────────────────────────────────────

    private Token scanToken() throws SqlException {
        skipWhitespaceAndComments();

        if (pos >= sql.length()) {
            return Token.simple(TokenType.EOF, line, column);
        }

        char c = advance();

        // Single-character tokens
        return switch (c) {
            case '(' -> Token.simple(TokenType.LPAREN, line, column - 1);
            case ')' -> Token.simple(TokenType.RPAREN, line, column - 1);
            case ',' -> Token.simple(TokenType.COMMA, line, column - 1);
            case ';' -> Token.simple(TokenType.SEMICOLON, line, column - 1);
            case '.' -> Token.simple(TokenType.DOT, line, column - 1);
            case '*' -> Token.simple(TokenType.STAR, line, column - 1);
            case '+' -> Token.simple(TokenType.PLUS, line, column - 1);
            case '-' -> Token.simple(TokenType.MINUS, line, column - 1);
            case '/' -> Token.simple(TokenType.SLASH, line, column - 1);
            case '=' -> Token.simple(TokenType.EQ, line, column - 1);

            case '!', '<', '>' -> scanOperator(c);
            case '\'' -> scanString();
            default -> {
                if (Character.isDigit(c)) {
                    yield scanNumber(c);
                } else if (Character.isLetter(c) || c == '_') {
                    yield scanWord(c);
                } else {
                    throw new SqlException("Unexpected character '" + c + "' at line " + line + ", column " + (column - 1));
                }
            }
        };
    }

    // ── Sub-scanners ───────────────────────────────────────────────

    private Token scanOperator(char first) throws SqlException {
        int startCol = column - 1;
        if (pos < sql.length()) {
            char second = sql.charAt(pos);
            if (first == '!' && second == '=') { advance(); return Token.simple(TokenType.NEQ, line, startCol); }
            if (first == '<' && second == '=') { advance(); return Token.simple(TokenType.LTE, line, startCol); }
            if (first == '<' && second == '>') { advance(); return Token.simple(TokenType.NEQ, line, startCol); }
            if (first == '>' && second == '=') { advance(); return Token.simple(TokenType.GTE, line, startCol); }
        }
        return switch (first) {
            case '<' -> Token.simple(TokenType.LT, line, startCol);
            case '>' -> Token.simple(TokenType.GT, line, startCol);
            default -> throw new SqlException("Unexpected operator '" + first + "'");
        };
    }

    private Token scanString() throws SqlException {
        int startCol = column - 1;
        StringBuilder sb = new StringBuilder();
        while (pos < sql.length()) {
            char c = advance();
            if (c == '\'') {
                // Check for escaped quote ''
                if (pos < sql.length() && sql.charAt(pos) == '\'') {
                    advance();
                    sb.append('\'');
                } else {
                    return Token.stringLiteral(sb.toString(), line, startCol);
                }
            } else {
                sb.append(c);
            }
        }
        throw new SqlException("Unterminated string literal at line " + line);
    }

    private Token scanNumber(char first) {
        int startCol = column - 1;
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        boolean isFloat = false;

        while (pos < sql.length()) {
            char c = sql.charAt(pos);
            if (Character.isDigit(c)) {
                sb.append(advance());
            } else if (c == '.' && !isFloat) {
                isFloat = true;
                sb.append(advance());
            } else {
                break;
            }
        }

        if (isFloat) {
            return Token.floatLiteral(sb.toString(), line, startCol);
        }
        return Token.intLiteral(sb.toString(), line, startCol);
    }

    private Token scanWord(char first) {
        int startCol = column - 1;
        StringBuilder sb = new StringBuilder();
        sb.append(first);

        while (pos < sql.length()) {
            char c = sql.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(advance());
            } else {
                break;
            }
        }

        String word = sb.toString();
        String upper = word.toUpperCase();

        if (KEYWORDS.contains(upper)) {
            TokenType kwType = TokenType.valueOf(upper);
            // Special case: DESC is ambiguous (DESC for ORDER BY vs DESCRIBE alias)
            if (kwType == TokenType.DESC && peekKeyword("DESCRIBE", word)) {
                // Handled in parser by context, treat as keyword DESC for now
            }
            return Token.keyword(kwType, line, startCol);
        }

        return Token.identifier(word, line, startCol);
    }

    private boolean peekKeyword(String keyword, String current) {
        // Simple heuristic: if current word is "DESC" and it was lower/mixed case
        // and not followed by a column context, treat as DESCRIBE alias
        return false; // parser handles disambiguation
    }

    // ── Helpers ────────────────────────────────────────────────────

    private char advance() {
        char c = sql.charAt(pos);
        pos++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private void skipWhitespaceAndComments() {
        while (pos < sql.length()) {
            char c = sql.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                advance();
            } else if (c == '-' && pos + 1 < sql.length() && sql.charAt(pos + 1) == '-') {
                // Line comment: skip to end of line
                advance(); advance(); // skip --
                while (pos < sql.length() && sql.charAt(pos) != '\n') {
                    advance();
                }
            } else {
                break;
            }
        }
    }
}
