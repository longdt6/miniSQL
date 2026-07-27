package com.minisql.engine;

import com.minisql.common.SqlException;
import com.minisql.engine.executor.ResultSet;
import com.minisql.storage.BufferPool;
import com.minisql.storage.Catalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlEngineTest {

    private SqlEngine engine;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        Catalog catalog = new Catalog(dir.toString());
        BufferPool pool = new BufferPool(16);
        engine = new SqlEngine(catalog, pool);
    }

    @Test
    void fullLifecycleAcrossAllStatementTypes() throws SqlException {
        engine.execute("CREATE TABLE users (id INT, name TEXT, score FLOAT, active BOOLEAN)");

        ResultSet insertResult = engine.execute(
            "INSERT INTO users (id, name, score, active) VALUES " +
            "(1, 'alice', 9.5, TRUE), (2, 'bob', 5.0, FALSE), (3, 'carol', 7.5, TRUE)");
        assertThat(insertResult.getAffectedRows()).isEqualTo(3);

        ResultSet select = engine.execute(
            "SELECT id, name FROM users WHERE active = TRUE ORDER BY id DESC LIMIT 1");
        assertThat(select.isSelect()).isTrue();
        assertThat(select.getRows()).hasSize(1);
        Map<String, Object> row = select.getRows().get(0);
        assertThat(row.get("id")).isEqualTo(3);
        assertThat(row.get("name")).isEqualTo("carol");

        ResultSet updateResult = engine.execute("UPDATE users SET score = 10.0 WHERE id = 1");
        assertThat(updateResult.getAffectedRows()).isEqualTo(1);

        ResultSet verifyUpdate = engine.execute("SELECT score FROM users WHERE id = 1");
        assertThat(verifyUpdate.getRows().get(0).get("score")).isEqualTo(10.0);

        ResultSet deleteResult = engine.execute("DELETE FROM users WHERE id = 2");
        assertThat(deleteResult.getAffectedRows()).isEqualTo(1);

        ResultSet remaining = engine.execute("SELECT id FROM users");
        assertThat(remaining.getRows()).hasSize(2);

        ResultSet showTables = engine.execute("SHOW TABLES");
        assertThat(showTables.getRows()).extracting(r -> r.get("table_name")).containsExactly("users");

        ResultSet describe = engine.execute("DESCRIBE users");
        assertThat(describe.getRows()).extracting(r -> r.get("column"))
            .containsExactly("id", "name", "score", "active");
    }

    @Test
    void selectWithoutWhereReturnsAllRows() throws SqlException {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("INSERT INTO t (id) VALUES (1), (2), (3)");

        ResultSet result = engine.execute("SELECT * FROM t");

        assertThat(result.getRows()).hasSize(3);
    }

    @Test
    void createTableIfNotExistsIsIdempotent() throws SqlException {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE TABLE IF NOT EXISTS t (id INT)");

        assertThatThrownBy(() -> engine.execute("CREATE TABLE t (id INT)"))
            .isInstanceOf(SqlException.class);
    }

    @Test
    void dropTableThenSelectFails() throws SqlException {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("DROP TABLE t");

        assertThatThrownBy(() -> engine.execute("SELECT * FROM t"))
            .isInstanceOf(SqlException.class);
    }

    @Test
    void selectFromUnknownTableThrows() {
        assertThatThrownBy(() -> engine.execute("SELECT * FROM missing"))
            .isInstanceOf(SqlException.class);
    }

    @Test
    void executeBatchRunsMultipleStatements() throws SqlException {
        var results = engine.executeBatch(
            "CREATE TABLE t (id INT); INSERT INTO t (id) VALUES (1); SELECT * FROM t");

        assertThat(results).hasSize(3);
        assertThat(results.get(2).getRows()).hasSize(1);
    }
}
