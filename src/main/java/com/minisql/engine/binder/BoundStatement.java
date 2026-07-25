package com.minisql.engine.binder;

import com.minisql.storage.Catalog;
import com.minisql.storage.ColumnMetadata;
import com.minisql.storage.TableMetadata;
import com.minisql.types.DataType;

import java.util.List;

/**
 * Resolved AST produced by the Binder. Each variant carries type information
 * and verified references into the catalog.
 */
public abstract class BoundStatement {

    public abstract <T> T accept(Visitor<T> visitor);

    public interface Visitor<T> {
        T visit(Select stmt);
        T visit(Insert stmt);
        T visit(Update stmt);
        T visit(Delete stmt);
        T visit(CreateTable stmt);
        T visit(DropTable stmt);
        T visit(ShowTables stmt);
        T visit(Describe stmt);
    }

    // ── Concrete variants ──────────────────────────────────────

    public static class Select extends BoundStatement {
        private final TableMetadata table;
        private final List<BoundColumn> columns;
        private final BoundExpression where;
        private final List<com.minisql.engine.parser.ast.Statement.OrderBy> orderBy;
        private final int limit;
        private final int offset;

        public Select(TableMetadata table, List<BoundColumn> columns, BoundExpression where,
                      List<com.minisql.engine.parser.ast.Statement.OrderBy> orderBy, int limit, int offset) {
            this.table = table;
            this.columns = columns;
            this.where = where;
            this.orderBy = orderBy;
            this.limit = limit;
            this.offset = offset;
        }

        public TableMetadata table() { return table; }
        public List<BoundColumn> columns() { return columns; }
        public BoundExpression where() { return where; }
        public List<com.minisql.engine.parser.ast.Statement.OrderBy> orderBy() { return orderBy; }
        public int limit() { return limit; }
        public int offset() { return offset; }

        @Override public <T> T accept(Visitor<T> v) { return v.visit(this); }
    }

    public static class Insert extends BoundStatement {
        private final TableMetadata table;
        private final List<String> columnNames;
        private final List<List<Object>> rows;

        public Insert(TableMetadata table, List<String> columnNames, List<List<Object>> rows) {
            this.table = table;
            this.columnNames = columnNames;
            this.rows = rows;
        }

        public TableMetadata table() { return table; }
        public List<String> columnNames() { return columnNames; }
        public List<List<Object>> rows() { return rows; }

        @Override public <T> T accept(Visitor<T> v) { return v.visit(this); }
    }

    public static class Update extends BoundStatement {
        private final TableMetadata table;
        private final List<UpdateSet> setClauses;
        private final BoundExpression where;

        public Update(TableMetadata table, List<UpdateSet> setClauses, BoundExpression where) {
            this.table = table;
            this.setClauses = setClauses;
            this.where = where;
        }

        public TableMetadata table() { return table; }
        public List<UpdateSet> setClauses() { return setClauses; }
        public BoundExpression where() { return where; }

        @Override public <T> T accept(Visitor<T> v) { return v.visit(this); }
    }

    public static class Delete extends BoundStatement {
        private final TableMetadata table;
        private final BoundExpression where;

        public Delete(TableMetadata table, BoundExpression where) {
            this.table = table;
            this.where = where;
        }

        public TableMetadata table() { return table; }
        public BoundExpression where() { return where; }

        @Override public <T> T accept(Visitor<T> v) { return v.visit(this); }
    }

    public static class CreateTable extends BoundStatement {
        private final String tableName;
        private final List<Catalog.ColumnDef> columns;

        public CreateTable(String tableName, List<Catalog.ColumnDef> columns) {
            this.tableName = tableName;
            this.columns = columns;
        }

        public String tableName() { return tableName; }
        public List<Catalog.ColumnDef> columns() { return columns; }

        @Override public <T> T accept(Visitor<T> v) { return v.visit(this); }
    }

    public static class DropTable extends BoundStatement {
        private final String tableName;
        private final boolean ifExists;

        public DropTable(String tableName, boolean ifExists) {
            this.tableName = tableName;
            this.ifExists = ifExists;
        }

        public String tableName() { return tableName; }
        public boolean ifExists() { return ifExists; }

        @Override public <T> T accept(Visitor<T> v) { return v.visit(this); }
    }

    public static class ShowTables extends BoundStatement {
        @Override public <T> T accept(Visitor<T> v) { return v.visit(this); }
    }

    public static class Describe extends BoundStatement {
        private final TableMetadata table;

        public Describe(TableMetadata table) { this.table = table; }

        public TableMetadata table() { return table; }

        @Override public <T> T accept(Visitor<T> v) { return v.visit(this); }
    }

    public record UpdateSet(ColumnMetadata column, Object value) {}
}
