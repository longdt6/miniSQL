package com.minisql.engine.parser;

import com.minisql.common.SqlException;
import com.minisql.engine.lexer.Lexer;
import com.minisql.engine.lexer.Token;
import com.minisql.engine.lexer.TokenType;
import com.minisql.engine.parser.ast.Expression;
import com.minisql.engine.parser.ast.Statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent SQL parser. Converts a token stream into an AST.
 *
 * Grammar (subset of SQL):
 *   statement     → select_stmt | insert_stmt | update_stmt | delete_stmt
 *                  | create_table_stmt | drop_table_stmt
 *                  | show_tables_stmt | describe_stmt
 *
 *   select_stmt   → SELECT column_list FROM table_name
 *                   [WHERE expr] [ORDER BY order_list] [LIMIT int [OFFSET int]]
 *
 *   insert_stmt   → INSERT INTO table_name [(col_list)] VALUES (value_list)
 *                    [, (value_list), ...]
 *
 *   update_stmt   → UPDATE table_name SET col = value [, col = value, ...]
 *                    [WHERE expr]
 *
 *   delete_stmt   → DELETE FROM table_name [WHERE expr]
 *
 *   create_table  → CREATE TABLE [IF NOT EXISTS] table_name (col_def [, ...])
 *
 *   drop_table    → DROP TABLE [IF EXISTS] table_name
 *
 *   expression    → and_expr (OR and_expr)*
 *   and_expr      → comparison (AND comparison)*
 *   comparison    → operand ( (= | != | <> | < | > | <= | >=) operand )*
 *   operand       → literal | column_ref | '(' expression ')'
 */
public class Parser {

    private final Lexer lexer;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
    }

    // ── Statement dispatch ───────────────────────────────────────

    public Statement parseStatement() throws SqlException {
        Token first = lexer.peekToken();
        return switch (first.type()) {
            case SELECT     -> parseSelect();
            case INSERT     -> parseInsert();
            case UPDATE     -> parseUpdate();
            case DELETE     -> parseDelete();
            case CREATE     -> parseCreate();
            case DROP       -> parseDrop();
            case SHOW       -> parseShowTables();
            case DESCRIBE, DESC_TABLE -> parseDescribe();
            default -> {
                throw error("Unexpected token: " + first.type() + " at line " + first.line());
            }
        };
    }

    // ── SHOW TABLES ───────────────────────────────────────────────

    private Statement.ShowTables parseShowTables() throws SqlException {
        consume(TokenType.SHOW);
        consume(TokenType.TABLES);
        return new Statement.ShowTables();
    }

    // ── DESCRIBE ──────────────────────────────────────────────────

    private Statement.Describe parseDescribe() throws SqlException {
        lexer.nextToken(); // consume DESCRIBE or DESC_TABLE
        String tableName = parseIdentifier();
        return new Statement.Describe(tableName);
    }

    // ── SELECT ────────────────────────────────────────────────────

    private Statement.Select parseSelect() throws SqlException {
        consume(TokenType.SELECT);

        List<Statement.SelectColumn> columns = parseSelectColumns();
        consume(TokenType.FROM);
        String tableName = parseIdentifier();

        Expression where = null;
        if (match(TokenType.WHERE)) {
            where = parseExpression();
        }

        List<Statement.OrderBy> orderBy = List.of();
        if (match(TokenType.ORDER)) {
            consume(TokenType.BY);
            orderBy = parseOrderBy();
        }

        int limit = -1;
        int offset = -1;
        if (match(TokenType.LIMIT)) {
            limit = parseIntLiteral();
            if (match(TokenType.OFFSET)) {
                offset = parseIntLiteral();
            }
        }

        return new Statement.Select(columns, tableName, where, orderBy, limit, offset);
    }

    private List<Statement.SelectColumn> parseSelectColumns() throws SqlException {
        List<Statement.SelectColumn> columns = new ArrayList<>();
        if (peekType() == TokenType.STAR) {
            lexer.nextToken();
            columns.add(new Statement.SelectColumn(new Expression.Star()));
            return columns;
        }
        columns.add(new Statement.SelectColumn(parseExpression()));
        while (match(TokenType.COMMA)) {
            columns.add(new Statement.SelectColumn(parseExpression()));
        }
        return columns;
    }

    private List<Statement.OrderBy> parseOrderBy() throws SqlException {
        List<Statement.OrderBy> list = new ArrayList<>();
        String col = parseIdentifier();
        boolean asc = true;
        if (peekType() == TokenType.ASC) { lexer.nextToken(); }
        else if (peekType() == TokenType.DESC) { lexer.nextToken(); asc = false; }
        list.add(new Statement.OrderBy(col, asc));

        while (match(TokenType.COMMA)) {
            col = parseIdentifier();
            asc = true;
            if (peekType() == TokenType.ASC) { lexer.nextToken(); }
            else if (peekType() == TokenType.DESC) { lexer.nextToken(); asc = false; }
            list.add(new Statement.OrderBy(col, asc));
        }
        return list;
    }

    // ── INSERT ────────────────────────────────────────────────────

    private Statement.Insert parseInsert() throws SqlException {
        consume(TokenType.INSERT);
        consume(TokenType.INTO);
        String tableName = parseIdentifier();

        // Optional column list
        List<String> columns = List.of();
        if (peekType() == TokenType.LPAREN) {
            lexer.nextToken();
            columns = new ArrayList<>();
            columns.add(parseIdentifier());
            while (match(TokenType.COMMA)) {
                columns.add(parseIdentifier());
            }
            consume(TokenType.RPAREN);
        }

        consume(TokenType.VALUES);

        List<List<Expression>> rows = new ArrayList<>();
        rows.add(parseValueList());
        while (match(TokenType.COMMA)) {
            rows.add(parseValueList());
        }

        return new Statement.Insert(tableName, columns, rows);
    }

    private List<Expression> parseValueList() throws SqlException {
        consume(TokenType.LPAREN);
        List<Expression> values = new ArrayList<>();
        values.add(parseExpression());
        while (match(TokenType.COMMA)) {
            values.add(parseExpression());
        }
        consume(TokenType.RPAREN);
        return values;
    }

    // ── UPDATE ────────────────────────────────────────────────────

    private Statement.Update parseUpdate() throws SqlException {
        consume(TokenType.UPDATE);
        String tableName = parseIdentifier();
        consume(TokenType.SET);

        List<Statement.UpdateSet> sets = new ArrayList<>();
        sets.add(parseUpdateSet());
        while (match(TokenType.COMMA)) {
            sets.add(parseUpdateSet());
        }

        Expression where = null;
        if (match(TokenType.WHERE)) {
            where = parseExpression();
        }

        return new Statement.Update(tableName, sets, where);
    }

    private Statement.UpdateSet parseUpdateSet() throws SqlException {
        String col = parseIdentifier();
        consume(TokenType.EQ);
        Expression value = parseExpression();
        return new Statement.UpdateSet(col, value);
    }

    // ── DELETE ────────────────────────────────────────────────────

    private Statement.Delete parseDelete() throws SqlException {
        consume(TokenType.DELETE);
        consume(TokenType.FROM);
        String tableName = parseIdentifier();

        Expression where = null;
        if (match(TokenType.WHERE)) {
            where = parseExpression();
        }

        return new Statement.Delete(tableName, where);
    }

    // ── CREATE TABLE ──────────────────────────────────────────────

    private Statement.CreateTable parseCreate() throws SqlException {
        consume(TokenType.CREATE);
        consume(TokenType.TABLE);

        boolean ifNotExists = false;
        if (match(TokenType.IF)) {
            consume(TokenType.NOT);
            consume(TokenType.EXISTS);
            ifNotExists = true;
        }

        String tableName = parseIdentifier();
        consume(TokenType.LPAREN);

        List<Statement.ColumnDef> columns = new ArrayList<>();
        columns.add(parseColumnDef());
        while (match(TokenType.COMMA)) {
            columns.add(parseColumnDef());
        }

        consume(TokenType.RPAREN);
        return new Statement.CreateTable(tableName, ifNotExists, columns);
    }

    private Statement.ColumnDef parseColumnDef() throws SqlException {
        String colName = parseIdentifier();
        String typeName = parseIdentifier().toUpperCase();
        boolean pk = false, unique = false;

        // Optional: PRIMARY KEY, UNIQUE
        while (true) {
            if (match(TokenType.PRIMARY)) {
                consume(TokenType.KEY);
                pk = true;
            } else if (match(TokenType.UNIQUE)) {
                unique = true;
            } else {
                break;
            }
        }

        return new Statement.ColumnDef(colName, typeName, pk, unique);
    }

    // ── DROP TABLE ────────────────────────────────────────────────

    private Statement.DropTable parseDrop() throws SqlException {
        consume(TokenType.DROP);
        consume(TokenType.TABLE);

        boolean ifExists = false;
        if (match(TokenType.IF)) {
            consume(TokenType.EXISTS);
            ifExists = true;
        }

        String tableName = parseIdentifier();
        return new Statement.DropTable(tableName, ifExists);
    }

    // ── Expression parsing (precedence climbing) ──────────────────

    /** Lowest precedence: OR */
    Expression parseExpression() throws SqlException {
        Expression left = parseAnd();
        while (match(TokenType.OR)) {
            Expression right = parseAnd();
            left = new Expression.Binary(left, "OR", right);
        }
        return left;
    }

    /** AND binds tighter than OR */
    private Expression parseAnd() throws SqlException {
        Expression left = parseComparison();
        while (match(TokenType.AND)) {
            Expression right = parseComparison();
            left = new Expression.Binary(left, "AND", right);
        }
        return left;
    }

    /** Comparisons: = != <> < > <= >= */
    private Expression parseComparison() throws SqlException {
        Expression left = parseOperand();
        TokenType opType = peekType();
        if (isComparisonOp(opType)) {
            lexer.nextToken();
            String op = tokToOpString(opType);
            Expression right = parseOperand();
            return new Expression.Binary(left, op, right);
        }
        return left;
    }

    /** Atoms: literals, column refs, parenthesized expressions, NOT */
    private Expression parseOperand() throws SqlException {
        // NOT unary
        if (match(TokenType.NOT)) {
            Expression operand = parseOperand();
            return new Expression.Binary(operand, "=", new Expression.Literal(Expression.TokenValue.boolValue(false)));
        }

        // Parenthesized expression
        if (match(TokenType.LPAREN)) {
            Expression inner = parseExpression();
            consume(TokenType.RPAREN);
            return inner;
        }

        // Literals
        Token t = lexer.peekToken();
        return switch (t.type()) {
            case INT_LITERAL -> { lexer.nextToken(); yield new Expression.Literal(Expression.TokenValue.intValue(t.value())); }
            case FLOAT_LITERAL -> { lexer.nextToken(); yield new Expression.Literal(Expression.TokenValue.floatValue(t.value())); }
            case STRING_LITERAL -> { lexer.nextToken(); yield new Expression.Literal(Expression.TokenValue.stringValue(t.value())); }
            case TRUE_KW -> { lexer.nextToken(); yield new Expression.Literal(Expression.TokenValue.boolValue(true)); }
            case FALSE_KW -> { lexer.nextToken(); yield new Expression.Literal(Expression.TokenValue.boolValue(false)); }
            case NULL_KW -> { lexer.nextToken(); yield new Expression.Literal(Expression.TokenValue.nullValue()); }
            case IDENTIFIER -> { lexer.nextToken(); yield new Expression.ColumnRef(t.value()); }
            case MINUS -> {
                // Negative number: -42 or -3.14
                lexer.nextToken();
                Token num = lexer.nextToken();
                yield switch (num.type()) {
                    case INT_LITERAL -> new Expression.Literal(Expression.TokenValue.intValue("-" + num.value()));
                    case FLOAT_LITERAL -> new Expression.Literal(Expression.TokenValue.floatValue("-" + num.value()));
                    default -> throw error("Expected number after '-'");
                };
            }
            default -> throw error("Unexpected token in expression: " + t.type());
        };
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void consume(TokenType expected) throws SqlException {
        Token t = lexer.nextToken();
        if (t.type() != expected) {
            throw error("Expected " + expected + " but got " + t.type() + " at line " + t.line());
        }
    }

    private boolean match(TokenType type) throws SqlException {
        if (lexer.peekToken().type() == type) {
            lexer.nextToken();
            return true;
        }
        return false;
    }

    private TokenType peekType() throws SqlException {
        return lexer.peekToken().type();
    }

    private String parseIdentifier() throws SqlException {
        Token t = lexer.nextToken();
        if (t.type() != TokenType.IDENTIFIER) {
            throw error("Expected identifier, got " + t.type());
        }
        return t.value();
    }

    private int parseIntLiteral() throws SqlException {
        Token t = lexer.nextToken();
        if (t.type() != TokenType.INT_LITERAL) {
            throw error("Expected integer literal, got " + t.type());
        }
        return Integer.parseInt(t.value());
    }

    private boolean isComparisonOp(TokenType t) {
        return t == TokenType.EQ || t == TokenType.NEQ || t == TokenType.LT
            || t == TokenType.GT || t == TokenType.LTE || t == TokenType.GTE;
    }

    private String tokToOpString(TokenType t) {
        return switch (t) {
            case EQ -> "=";
            case NEQ -> "!=";
            case LT -> "<";
            case GT -> ">";
            case LTE -> "<=";
            case GTE -> ">=";
            default -> "=";
        };
    }

    private SqlException error(String msg) {
        return new SqlException(msg);
    }
}
