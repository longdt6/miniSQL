package com.minisql.engine.parser.ast;

import java.util.List;

/**
 * Base interface for all SQL statements.
 */
public sealed interface Statement
    permits Statement.CreateTable, Statement.DropTable,
            Statement.Insert, Statement.Select,
            Statement.Update, Statement.Delete,
            Statement.ShowTables, Statement.Describe {

    record CreateTable(String tableName, boolean ifNotExists, List<ColumnDef> columns) implements Statement {}
    record DropTable(String tableName, boolean ifExists) implements Statement {}
    record Insert(String tableName, List<String> columns, List<List<Expression>> rows) implements Statement {}
    record Select(List<SelectColumn> columns, String tableName, Expression where,
                  List<OrderBy> orderBy, int limit, int offset) implements Statement {}
    record Update(String tableName, List<UpdateSet> setClauses, Expression where) implements Statement {}
    record Delete(String tableName, Expression where) implements Statement {}
    record ShowTables() implements Statement {}
    record Describe(String tableName) implements Statement {}

    // ── Sub-records ──────────────────────────────────────

    record ColumnDef(String name, String typeName, boolean primaryKey, boolean unique) {
        public ColumnDef(String name, String typeName) {
            this(name, typeName, false, false);
        }
    }

    record SelectColumn(Expression expression, String alias) {
        public SelectColumn(Expression expression) { this(expression, null); }
        public boolean isStar() { return expression instanceof Expression.Star; }
    }

    record OrderBy(String columnName, boolean ascending) {
        public OrderBy(String columnName) { this(columnName, true); }
    }

    record UpdateSet(String columnName, Expression value) {}
}
