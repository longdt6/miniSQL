package com.minisql.engine.planner;

import com.minisql.engine.binder.BoundColumn;
import com.minisql.engine.binder.BoundExpression;
import com.minisql.engine.binder.BoundStatement;
import com.minisql.engine.parser.ast.Statement;
import com.minisql.storage.Catalog;
import com.minisql.storage.TableMetadata;

import java.util.List;

/**
 * Execution plan node. Converted from a BoundStatement by the Planner,
 * consumed by the Executor to build operator trees.
 */
public abstract class PlanNode {

    // ── Concrete variants ──────────────────────────────────────

    public static class TableScan extends PlanNode {
        private final TableMetadata table;
        public TableScan(TableMetadata table) { this.table = table; }
        public TableMetadata table() { return table; }
    }

    public static class Filter extends PlanNode {
        private final PlanNode child;
        private final BoundExpression predicate;
        public Filter(PlanNode child, BoundExpression predicate) {
            this.child = child; this.predicate = predicate;
        }
        public PlanNode child() { return child; }
        public BoundExpression predicate() { return predicate; }
    }

    public static class Project extends PlanNode {
        private final PlanNode child;
        private final List<BoundColumn> columns;
        public Project(PlanNode child, List<BoundColumn> columns) {
            this.child = child; this.columns = columns;
        }
        public PlanNode child() { return child; }
        public List<BoundColumn> columns() { return columns; }
    }

    public static class Sort extends PlanNode {
        private final PlanNode child;
        private final List<Statement.OrderBy> orderBy;
        public Sort(PlanNode child, List<Statement.OrderBy> orderBy) {
            this.child = child; this.orderBy = orderBy;
        }
        public PlanNode child() { return child; }
        public List<Statement.OrderBy> orderBy() { return orderBy; }
    }

    public static class Limit extends PlanNode {
        private final PlanNode child;
        private final int limit;
        public Limit(PlanNode child, int limit) {
            this.child = child; this.limit = limit;
        }
        public PlanNode child() { return child; }
        public int limit() { return limit; }
    }

    public static class Insert extends PlanNode {
        private final TableMetadata table;
        private final List<String> columnNames;
        private final List<List<Object>> rows;
        public Insert(TableMetadata table, List<String> columnNames, List<List<Object>> rows) {
            this.table = table; this.columnNames = columnNames; this.rows = rows;
        }
        public TableMetadata table() { return table; }
        public List<String> columnNames() { return columnNames; }
        public List<List<Object>> rows() { return rows; }
    }

    public static class Update extends PlanNode {
        private final TableMetadata table;
        private final List<BoundStatement.UpdateSet> setClauses;
        private final BoundExpression where;
        public Update(TableMetadata table, List<BoundStatement.UpdateSet> setClauses, BoundExpression where) {
            this.table = table; this.setClauses = setClauses; this.where = where;
        }
        public TableMetadata table() { return table; }
        public List<BoundStatement.UpdateSet> setClauses() { return setClauses; }
        public BoundExpression where() { return where; }
    }

    public static class Delete extends PlanNode {
        private final TableMetadata table;
        private final BoundExpression where;
        public Delete(TableMetadata table, BoundExpression where) {
            this.table = table; this.where = where;
        }
        public TableMetadata table() { return table; }
        public BoundExpression where() { return where; }
    }

    public static class CreateTable extends PlanNode {
        private final String tableName;
        private final List<Catalog.ColumnDef> columns;
        public CreateTable(String tableName, List<Catalog.ColumnDef> columns) {
            this.tableName = tableName; this.columns = columns;
        }
        public String tableName() { return tableName; }
        public List<Catalog.ColumnDef> columns() { return columns; }
    }

    public static class DropTable extends PlanNode {
        private final String tableName;
        private final boolean ifExists;
        public DropTable(String tableName, boolean ifExists) {
            this.tableName = tableName; this.ifExists = ifExists;
        }
        public String tableName() { return tableName; }
        public boolean ifExists() { return ifExists; }
    }

    public static class ShowTables extends PlanNode {}

    public static class DescribeTable extends PlanNode {
        private final TableMetadata table;
        public DescribeTable(TableMetadata table) { this.table = table; }
        public TableMetadata table() { return table; }
    }
}
