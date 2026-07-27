package com.minisql.engine.binder;

import com.minisql.common.SqlException;
import com.minisql.engine.parser.ast.Expression;
import com.minisql.engine.parser.ast.Statement;
import com.minisql.storage.Catalog;
import com.minisql.types.BooleanType;
import com.minisql.types.FloatType;
import com.minisql.types.IntegerType;
import com.minisql.types.TextType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinderTest {

    private Catalog catalog;
    private Binder binder;

    @BeforeEach
    void setUp(@TempDir Path dir) throws SqlException, IOException {
        catalog = new Catalog(dir.toString());
        catalog.createTable("users", List.of(
            new Catalog.ColumnDef("id", IntegerType.INSTANCE),
            new Catalog.ColumnDef("name", TextType.INSTANCE),
            new Catalog.ColumnDef("score", FloatType.INSTANCE),
            new Catalog.ColumnDef("active", BooleanType.INSTANCE)
        ));
        binder = new Binder(catalog);
    }

    private Expression.Literal intLit(int i) {
        return new Expression.Literal(Expression.TokenValue.intValue(Integer.toString(i)));
    }

    private Expression.Literal strLit(String s) {
        return new Expression.Literal(Expression.TokenValue.stringValue(s));
    }

    // ---- SELECT ----

    @Test
    void bindSelectStarExpandsToAllColumns() throws SqlException {
        Statement.Select select = new Statement.Select(
            List.of(new Statement.SelectColumn(new Expression.Star())),
            "users", null, List.of(), -1, -1);

        BoundStatement.Select bound = (BoundStatement.Select) binder.bind(select);

        assertThat(bound.columns()).extracting(BoundColumn::name)
            .containsExactly("id", "name", "score", "active");
    }

    @Test
    void bindSelectResolvesNamedColumns() throws SqlException {
        Statement.Select select = new Statement.Select(
            List.of(new Statement.SelectColumn(new Expression.ColumnRef("name"))),
            "users", null, List.of(), -1, -1);

        BoundStatement.Select bound = (BoundStatement.Select) binder.bind(select);

        assertThat(bound.columns()).hasSize(1);
        assertThat(bound.columns().get(0).name()).isEqualTo("name");
        assertThat(bound.columns().get(0).dataType()).isEqualTo(TextType.INSTANCE);
    }

    @Test
    void bindSelectUnknownColumnThrows() {
        Statement.Select select = new Statement.Select(
            List.of(new Statement.SelectColumn(new Expression.ColumnRef("nope"))),
            "users", null, List.of(), -1, -1);

        assertThatThrownBy(() -> binder.bind(select))
            .isInstanceOf(SqlException.class)
            .hasMessageContaining("nope");
    }

    @Test
    void bindSelectUnknownTableThrows() {
        Statement.Select select = new Statement.Select(
            List.of(new Statement.SelectColumn(new Expression.Star())),
            "missing", null, List.of(), -1, -1);

        assertThatThrownBy(() -> binder.bind(select)).isInstanceOf(SqlException.class);
    }

    // ---- INSERT ----

    @Test
    void bindInsertWithoutColumnListUsesTableOrder() throws SqlException {
        Statement.Insert insert = new Statement.Insert("users", List.of(),
            List.of(List.of(intLit(1), strLit("alice"),
                new Expression.Literal(Expression.TokenValue.floatValue("9.5")),
                new Expression.Literal(Expression.TokenValue.boolValue(true)))));

        BoundStatement.Insert bound = (BoundStatement.Insert) binder.bind(insert);

        assertThat(bound.columnNames()).containsExactly("id", "name", "score", "active");
        assertThat(bound.rows()).hasSize(1);
        assertThat(bound.rows().get(0)).containsExactly(1, "alice", 9.5, true);
    }

    @Test
    void bindInsertCoercesTypesForNamedColumns() throws SqlException {
        Statement.Insert insert = new Statement.Insert("users", List.of("id", "name"),
            List.of(List.of(intLit(2), strLit("bob"))));

        BoundStatement.Insert bound = (BoundStatement.Insert) binder.bind(insert);

        assertThat(bound.rows().get(0)).containsExactly(2, "bob");
    }

    @Test
    void bindInsertColumnCountMismatchThrows() {
        Statement.Insert insert = new Statement.Insert("users", List.of("id", "name"),
            List.of(List.of(intLit(1))));

        assertThatThrownBy(() -> binder.bind(insert))
            .isInstanceOf(SqlException.class)
            .hasMessageContaining("Expected 2 values, got 1");
    }

    @Test
    void bindInsertUnknownColumnThrows() {
        Statement.Insert insert = new Statement.Insert("users", List.of("nope"),
            List.of(List.of(intLit(1))));

        assertThatThrownBy(() -> binder.bind(insert)).isInstanceOf(SqlException.class);
    }

    @Test
    void bindInsertTypeMismatchThrows() {
        Statement.Insert insert = new Statement.Insert("users", List.of("id"),
            List.of(List.of(strLit("not-an-int"))));

        assertThatThrownBy(() -> binder.bind(insert))
            .isInstanceOf(SqlException.class)
            .hasMessageContaining("INTEGER");
    }

    // ---- UPDATE / DELETE ----

    @Test
    void bindUpdateBindsSetClausesAndWhere() throws SqlException {
        Statement.Update update = new Statement.Update("users",
            List.of(new Statement.UpdateSet("name", strLit("carol"))),
            new Expression.Binary(new Expression.ColumnRef("id"), "=", intLit(1)));

        BoundStatement.Update bound = (BoundStatement.Update) binder.bind(update);

        assertThat(bound.setClauses()).hasSize(1);
        assertThat(bound.setClauses().get(0).column().name()).isEqualTo("name");
        assertThat(bound.setClauses().get(0).value()).isEqualTo("carol");
        assertThat(bound.where()).isNotNull();
    }

    @Test
    void bindDeleteBindsWhere() throws SqlException {
        Statement.Delete delete = new Statement.Delete("users",
            new Expression.Binary(new Expression.ColumnRef("id"), "=", intLit(1)));

        BoundStatement.Delete bound = (BoundStatement.Delete) binder.bind(delete);

        assertThat(bound.where()).isNotNull();
    }

    @Test
    void bindDeleteWithoutWhereIsAllowed() throws SqlException {
        Statement.Delete delete = new Statement.Delete("users", null);

        BoundStatement.Delete bound = (BoundStatement.Delete) binder.bind(delete);

        assertThat(bound.where()).isNull();
    }

    // ---- CREATE TABLE ----

    @Test
    void bindCreateTableDuplicateColumnNameThrows() {
        Statement.CreateTable create = new Statement.CreateTable("orders", false, List.of(
            new Statement.ColumnDef("id", "INT"),
            new Statement.ColumnDef("id", "TEXT")
        ));

        assertThatThrownBy(() -> binder.bind(create))
            .isInstanceOf(SqlException.class)
            .hasMessageContaining("Duplicate column name");
    }

    @Test
    void bindCreateTableUnknownTypeThrows() {
        Statement.CreateTable create = new Statement.CreateTable("orders", false, List.of(
            new Statement.ColumnDef("id", "NOTATYPE")
        ));

        assertThatThrownBy(() -> binder.bind(create))
            .isInstanceOf(SqlException.class)
            .hasMessageContaining("Unknown type");
    }

    @Test
    void bindCreateTableExistingTableWithIfNotExistsIsNoop() throws SqlException {
        Statement.CreateTable create = new Statement.CreateTable("users", true, List.of(
            new Statement.ColumnDef("id", "INT")
        ));

        BoundStatement.CreateTable bound = (BoundStatement.CreateTable) binder.bind(create);

        assertThat(bound.columns()).isEmpty();
    }

    @Test
    void bindCreateTableExistingTableWithoutIfNotExistsThrows() {
        Statement.CreateTable create = new Statement.CreateTable("users", false, List.of(
            new Statement.ColumnDef("id", "INT")
        ));

        assertThatThrownBy(() -> binder.bind(create))
            .isInstanceOf(SqlException.class)
            .hasMessageContaining("already exists");
    }

    // ---- Comparison type-mismatch ----

    @Test
    void bindWhereComparingTextToIntegerThrows() {
        Statement.Select select = new Statement.Select(
            List.of(new Statement.SelectColumn(new Expression.Star())),
            "users",
            new Expression.Binary(new Expression.ColumnRef("name"), "=", new Expression.ColumnRef("id")),
            List.of(), -1, -1);

        assertThatThrownBy(() -> binder.bind(select))
            .isInstanceOf(SqlException.class)
            .hasMessageContaining("Cannot compare");
    }
}
