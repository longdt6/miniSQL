package com.minisql.engine.parser;

import com.minisql.common.SqlException;
import com.minisql.engine.lexer.Lexer;
import com.minisql.engine.parser.ast.Expression;
import com.minisql.engine.parser.ast.Statement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserTest {

    private Statement parse(String sql) throws SqlException {
        return new Parser(new Lexer(sql)).parseStatement();
    }

    // ── SELECT ──────────────────────────────────────────────────

    @Test
    void parsesSelectStar() throws SqlException {
        Statement.Select stmt = (Statement.Select) parse("SELECT * FROM users");
        assertThat(stmt.tableName()).isEqualTo("users");
        assertThat(stmt.columns()).hasSize(1);
        assertThat(stmt.columns().get(0).isStar()).isTrue();
        assertThat(stmt.where()).isNull();
        assertThat(stmt.limit()).isEqualTo(-1);
    }

    @Test
    void parsesSelectColumnList() throws SqlException {
        Statement.Select stmt = (Statement.Select) parse("SELECT id, name FROM users");
        assertThat(stmt.columns()).hasSize(2);
        assertThat(((Expression.ColumnRef) stmt.columns().get(0).expression()).name()).isEqualTo("id");
        assertThat(((Expression.ColumnRef) stmt.columns().get(1).expression()).name()).isEqualTo("name");
    }

    @Test
    void parsesSelectWithWhere() throws SqlException {
        Statement.Select stmt = (Statement.Select) parse("SELECT * FROM users WHERE age > 18");
        Expression.Binary where = (Expression.Binary) stmt.where();
        assertThat(((Expression.ColumnRef) where.left()).name()).isEqualTo("age");
        assertThat(where.operator()).isEqualTo(">");
        assertThat(((Expression.Literal) where.right()).value().value()).isEqualTo(18);
    }

    @Test
    void parsesSelectWithOrderByAscDesc() throws SqlException {
        Statement.Select stmt = (Statement.Select) parse("SELECT * FROM users ORDER BY age DESC, name ASC");
        assertThat(stmt.orderBy()).hasSize(2);
        assertThat(stmt.orderBy().get(0).columnName()).isEqualTo("age");
        assertThat(stmt.orderBy().get(0).ascending()).isFalse();
        assertThat(stmt.orderBy().get(1).columnName()).isEqualTo("name");
        assertThat(stmt.orderBy().get(1).ascending()).isTrue();
    }

    @Test
    void parsesSelectWithLimitAndOffset() throws SqlException {
        Statement.Select stmt = (Statement.Select) parse("SELECT * FROM users LIMIT 10 OFFSET 5");
        assertThat(stmt.limit()).isEqualTo(10);
        assertThat(stmt.offset()).isEqualTo(5);
    }

    @Test
    void andBindsTighterThanOr() throws SqlException {
        // a OR b AND c  =>  a OR (b AND c)
        Statement.Select stmt = (Statement.Select) parse("SELECT * FROM t WHERE a = 1 OR b = 2 AND c = 3");
        Expression.Binary top = (Expression.Binary) stmt.where();
        assertThat(top.operator()).isEqualTo("OR");
        assertThat(((Expression.Binary) top.left()).operator()).isEqualTo("=");
        Expression.Binary right = (Expression.Binary) top.right();
        assertThat(right.operator()).isEqualTo("AND");
    }

    @Test
    void parsesParenthesizedExpression() throws SqlException {
        Statement.Select stmt = (Statement.Select) parse("SELECT * FROM t WHERE (a = 1)");
        Expression.Binary where = (Expression.Binary) stmt.where();
        assertThat(where.operator()).isEqualTo("=");
    }

    @Test
    void parsesNegativeNumberLiteral() throws SqlException {
        Statement.Select stmt = (Statement.Select) parse("SELECT * FROM t WHERE a = -5");
        Expression.Binary where = (Expression.Binary) stmt.where();
        Expression.Literal lit = (Expression.Literal) where.right();
        assertThat(lit.value().value()).isEqualTo(-5);
    }

    @Test
    void parsesNotOperand() throws SqlException {
        Statement.Select stmt = (Statement.Select) parse("SELECT * FROM t WHERE NOT a = 1");
        // NOT wraps into a Binary "=" false comparison per Parser.parseOperand
        Expression.Binary outer = (Expression.Binary) stmt.where();
        assertThat(outer.operator()).isEqualTo("=");
    }

    // ── INSERT ──────────────────────────────────────────────────

    @Test
    void parsesInsertWithoutColumnList() throws SqlException {
        Statement.Insert stmt = (Statement.Insert) parse("INSERT INTO users VALUES (1, 'alice')");
        assertThat(stmt.tableName()).isEqualTo("users");
        assertThat(stmt.columns()).isEmpty();
        assertThat(stmt.rows()).hasSize(1);
        assertThat(stmt.rows().get(0)).hasSize(2);
    }

    @Test
    void parsesInsertWithColumnList() throws SqlException {
        Statement.Insert stmt = (Statement.Insert) parse("INSERT INTO users (id, name) VALUES (1, 'alice')");
        assertThat(stmt.columns()).containsExactly("id", "name");
    }

    @Test
    void parsesInsertWithMultipleRows() throws SqlException {
        Statement.Insert stmt = (Statement.Insert) parse(
            "INSERT INTO users VALUES (1, 'alice'), (2, 'bob')");
        assertThat(stmt.rows()).hasSize(2);
    }

    // ── UPDATE ──────────────────────────────────────────────────

    @Test
    void parsesUpdateWithWhere() throws SqlException {
        Statement.Update stmt = (Statement.Update) parse("UPDATE users SET name = 'bob' WHERE id = 1");
        assertThat(stmt.tableName()).isEqualTo("users");
        assertThat(stmt.setClauses()).hasSize(1);
        assertThat(stmt.setClauses().get(0).columnName()).isEqualTo("name");
        assertThat(stmt.where()).isNotNull();
    }

    @Test
    void parsesUpdateWithMultipleSetClauses() throws SqlException {
        Statement.Update stmt = (Statement.Update) parse("UPDATE users SET name = 'bob', age = 30");
        assertThat(stmt.setClauses()).hasSize(2);
        assertThat(stmt.where()).isNull();
    }

    // ── DELETE ──────────────────────────────────────────────────

    @Test
    void parsesDeleteWithWhere() throws SqlException {
        Statement.Delete stmt = (Statement.Delete) parse("DELETE FROM users WHERE id = 1");
        assertThat(stmt.tableName()).isEqualTo("users");
        assertThat(stmt.where()).isNotNull();
    }

    @Test
    void parsesDeleteWithoutWhere() throws SqlException {
        Statement.Delete stmt = (Statement.Delete) parse("DELETE FROM users");
        assertThat(stmt.where()).isNull();
    }

    // ── CREATE TABLE ────────────────────────────────────────────

    @Test
    void parsesCreateTable() throws SqlException {
        Statement.CreateTable stmt = (Statement.CreateTable) parse(
            "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT UNIQUE)");
        assertThat(stmt.tableName()).isEqualTo("users");
        assertThat(stmt.ifNotExists()).isFalse();
        assertThat(stmt.columns()).hasSize(2);
        assertThat(stmt.columns().get(0).name()).isEqualTo("id");
        assertThat(stmt.columns().get(0).typeName()).isEqualTo("INTEGER");
        assertThat(stmt.columns().get(0).primaryKey()).isTrue();
        assertThat(stmt.columns().get(1).unique()).isTrue();
    }

    @Test
    void parsesCreateTableIfNotExists() throws SqlException {
        Statement.CreateTable stmt = (Statement.CreateTable) parse(
            "CREATE TABLE IF NOT EXISTS users (id INTEGER)");
        assertThat(stmt.ifNotExists()).isTrue();
    }

    // ── DROP TABLE ──────────────────────────────────────────────

    @Test
    void parsesDropTable() throws SqlException {
        Statement.DropTable stmt = (Statement.DropTable) parse("DROP TABLE users");
        assertThat(stmt.tableName()).isEqualTo("users");
        assertThat(stmt.ifExists()).isFalse();
    }

    @Test
    void parsesDropTableIfExists() throws SqlException {
        Statement.DropTable stmt = (Statement.DropTable) parse("DROP TABLE IF EXISTS users");
        assertThat(stmt.ifExists()).isTrue();
    }

    // ── SHOW / DESCRIBE ─────────────────────────────────────────

    @Test
    void parsesShowTables() throws SqlException {
        assertThat(parse("SHOW TABLES")).isInstanceOf(Statement.ShowTables.class);
    }

    @Test
    void parsesDescribe() throws SqlException {
        Statement.Describe stmt = (Statement.Describe) parse("DESCRIBE users");
        assertThat(stmt.tableName()).isEqualTo("users");
    }

    // ── Errors ──────────────────────────────────────────────────

    @Test
    void unexpectedStatementTokenThrows() {
        assertThatThrownBy(() -> parse("FOO BAR")).isInstanceOf(SqlException.class);
    }

    @Test
    void missingFromThrows() {
        assertThatThrownBy(() -> parse("SELECT * users")).isInstanceOf(SqlException.class);
    }

    @Test
    void missingExpressionThrows() {
        assertThatThrownBy(() -> parse("SELECT * FROM t WHERE")).isInstanceOf(SqlException.class);
    }
}
