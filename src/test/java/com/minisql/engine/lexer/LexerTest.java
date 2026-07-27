package com.minisql.engine.lexer;

import com.minisql.common.SqlException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexerTest {

    private List<Token> tokenize(String sql) throws SqlException {
        return new Lexer(sql).tokenize();
    }

    private List<TokenType> types(String sql) throws SqlException {
        return tokenize(sql).stream().map(Token::type).toList();
    }

    @Test
    void tokenizesKeywordsCaseInsensitively() throws SqlException {
        assertThat(types("select From where")).containsExactly(
            TokenType.SELECT, TokenType.FROM, TokenType.WHERE, TokenType.EOF);
    }

    @Test
    void tokenizesIdentifier() throws SqlException {
        List<Token> tokens = tokenize("my_column1");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.IDENTIFIER);
        assertThat(tokens.get(0).value()).isEqualTo("my_column1");
    }

    @Test
    void tokenizesIntegerLiteral() throws SqlException {
        List<Token> tokens = tokenize("42");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.INT_LITERAL);
        assertThat(tokens.get(0).value()).isEqualTo("42");
    }

    @Test
    void tokenizesFloatLiteral() throws SqlException {
        List<Token> tokens = tokenize("3.14");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.FLOAT_LITERAL);
        assertThat(tokens.get(0).value()).isEqualTo("3.14");
    }

    @Test
    void tokenizesStringLiteral() throws SqlException {
        List<Token> tokens = tokenize("'hello world'");
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.STRING_LITERAL);
        assertThat(tokens.get(0).value()).isEqualTo("hello world");
    }

    @Test
    void tokenizesStringLiteralWithEscapedQuote() throws SqlException {
        List<Token> tokens = tokenize("'it''s'");
        assertThat(tokens.get(0).value()).isEqualTo("it's");
    }

    @Test
    void unterminatedStringThrows() {
        assertThatThrownBy(() -> tokenize("'unterminated"))
            .isInstanceOf(SqlException.class);
    }

    @Test
    void tokenizesAllComparisonOperators() throws SqlException {
        assertThat(types("= != <> < > <= >=")).containsExactly(
            TokenType.EQ, TokenType.NEQ, TokenType.NEQ, TokenType.LT,
            TokenType.GT, TokenType.LTE, TokenType.GTE, TokenType.EOF);
    }

    @Test
    void tokenizesPunctuationAndArithmetic() throws SqlException {
        assertThat(types("(),;.  * + - /")).containsExactly(
            TokenType.LPAREN, TokenType.RPAREN, TokenType.COMMA, TokenType.SEMICOLON,
            TokenType.DOT, TokenType.STAR, TokenType.PLUS, TokenType.MINUS, TokenType.SLASH,
            TokenType.EOF);
    }

    @Test
    void skipsLineComments() throws SqlException {
        List<Token> tokens = tokenize("SELECT 1 -- this is a comment\nFROM t");
        assertThat(tokens.stream().map(Token::type).toList()).containsExactly(
            TokenType.SELECT, TokenType.INT_LITERAL, TokenType.FROM, TokenType.IDENTIFIER, TokenType.EOF);
    }

    @Test
    void unexpectedCharacterThrows() {
        assertThatThrownBy(() -> tokenize("SELECT @ FROM t"))
            .isInstanceOf(SqlException.class);
    }

    @Test
    void tokenizesTrueFalseNullKeywords() throws SqlException {
        assertThat(types("TRUE FALSE NULL")).containsExactly(
            TokenType.TRUE_KW, TokenType.FALSE_KW, TokenType.NULL_KW, TokenType.EOF);
    }

    @Test
    void tokenizesOffsetKeyword() throws SqlException {
        assertThat(types("LIMIT 10 OFFSET 5")).containsExactly(
            TokenType.LIMIT, TokenType.INT_LITERAL, TokenType.OFFSET, TokenType.INT_LITERAL, TokenType.EOF);
    }

    @Test
    void tokenizesFullSelectStatement() throws SqlException {
        List<Token> tokens = tokenize("SELECT id, name FROM users WHERE age >= 18");
        assertThat(tokens.stream().map(Token::type).toList()).containsExactly(
            TokenType.SELECT, TokenType.IDENTIFIER, TokenType.COMMA, TokenType.IDENTIFIER,
            TokenType.FROM, TokenType.IDENTIFIER, TokenType.WHERE, TokenType.IDENTIFIER,
            TokenType.GTE, TokenType.INT_LITERAL, TokenType.EOF);
    }

    @Test
    void peekTokenDoesNotConsume() throws SqlException {
        Lexer lexer = new Lexer("SELECT FROM");
        Token peeked = lexer.peekToken();
        Token next = lexer.nextToken();
        assertThat(peeked).isEqualTo(next);
        assertThat(lexer.nextToken().type()).isEqualTo(TokenType.FROM);
    }

    @Test
    void emptyInputProducesOnlyEof() throws SqlException {
        assertThat(types("")).containsExactly(TokenType.EOF);
    }
}
