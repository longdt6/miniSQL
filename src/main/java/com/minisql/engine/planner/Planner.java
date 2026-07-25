package com.minisql.engine.planner;

import com.minisql.engine.binder.BoundStatement;

/**
 * Converts a bound statement into an execution plan tree.
 */
public class Planner {

    public PlanNode plan(BoundStatement stmt) {
        // Use the visitor pattern to dispatch
        return stmt.accept(new BoundStatement.Visitor<PlanNode>() {
            public PlanNode visit(BoundStatement.Select s)    { return planSelect(s); }
            public PlanNode visit(BoundStatement.Insert s)    { return planInsert(s); }
            public PlanNode visit(BoundStatement.Update s)    { return planUpdate(s); }
            public PlanNode visit(BoundStatement.Delete s)    { return planDelete(s); }
            public PlanNode visit(BoundStatement.CreateTable s) { return new PlanNode.CreateTable(s.tableName(), s.columns()); }
            public PlanNode visit(BoundStatement.DropTable s)   { return new PlanNode.DropTable(s.tableName(), s.ifExists()); }
            public PlanNode visit(BoundStatement.ShowTables s)  { return new PlanNode.ShowTables(); }
            public PlanNode visit(BoundStatement.Describe d)    { return new PlanNode.DescribeTable(d.table()); }
        });
    }

    private PlanNode planSelect(BoundStatement.Select stmt) {
        PlanNode node = new PlanNode.TableScan(stmt.table());

        if (stmt.where() != null) {
            node = new PlanNode.Filter(node, stmt.where());
        }

        node = new PlanNode.Project(node, stmt.columns());

        if (!stmt.orderBy().isEmpty()) {
            node = new PlanNode.Sort(node, stmt.orderBy());
        }

        if (stmt.limit() >= 0) {
            node = new PlanNode.Limit(node, stmt.limit());
        }

        return node;
    }

    private PlanNode planInsert(BoundStatement.Insert stmt) {
        return new PlanNode.Insert(stmt.table(), stmt.columnNames(), stmt.rows());
    }

    private PlanNode planUpdate(BoundStatement.Update stmt) {
        return new PlanNode.Update(stmt.table(), stmt.setClauses(), stmt.where());
    }

    private PlanNode planDelete(BoundStatement.Delete stmt) {
        return new PlanNode.Delete(stmt.table(), stmt.where());
    }
}
