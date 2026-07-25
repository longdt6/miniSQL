package com.minisql.engine.executor;

import com.minisql.common.SqlException;
import com.minisql.engine.binder.BoundColumn;
import com.minisql.engine.binder.BoundExpression;
import com.minisql.engine.binder.BoundStatement;
import com.minisql.engine.executor.operators.*;
import com.minisql.engine.planner.PlanNode;
import com.minisql.storage.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * Walks a plan tree and returns results. Builds operator trees for SELECT,
 * executes DML directly, and modifies the catalog for DDL.
 */
public class Executor {

    private final Catalog catalog;
    private final BufferPool pool;

    public Executor(Catalog catalog, BufferPool pool) {
        this.catalog = catalog;
        this.pool = pool;
    }

    public ResultSet execute(PlanNode plan) throws SqlException {
        if (plan instanceof PlanNode.TableScan)
            throw new SqlException("TableScan must be wrapped in other operators");
        if (plan instanceof PlanNode.Filter)
            throw new SqlException("Filter must have a child");

        if (plan instanceof PlanNode.Project p)    return executeSelect(buildTree(p));
        if (plan instanceof PlanNode.Sort s)       return executeSelect(buildTree(s));
        if (plan instanceof PlanNode.Limit l)      return executeSelect(buildTree(l));

        if (plan instanceof PlanNode.Insert ins)   return executeInsert(ins);
        if (plan instanceof PlanNode.Update upd)   return executeUpdate(upd);
        if (plan instanceof PlanNode.Delete del)   return executeDelete(del);

        if (plan instanceof PlanNode.CreateTable ct) return executeCreateTable(ct);
        if (plan instanceof PlanNode.DropTable dt)   return executeDropTable(dt);
        if (plan instanceof PlanNode.ShowTables)     return executeShowTables();
        if (plan instanceof PlanNode.DescribeTable dt) return executeDescribe(dt);

        throw new SqlException("Unknown plan node: " + plan);
    }

    private Operator buildTree(PlanNode node) {
        if (node instanceof PlanNode.TableScan ts)  return new TableScanOperator(ts.table(), pool);
        if (node instanceof PlanNode.Filter f)      return new FilterOperator(buildTree(f.child()), f.predicate());
        if (node instanceof PlanNode.Project p)     return new ProjectOperator(buildTree(p.child()), p.columns());
        if (node instanceof PlanNode.Sort s)        return new SortOperator(buildTree(s.child()), s.orderBy());
        if (node instanceof PlanNode.Limit l)       return new LimitOperator(buildTree(l.child()), l.limit());
        throw new IllegalStateException("Cannot build tree from: " + node);
    }

    private ResultSet executeSelect(Operator root) {
        List<String> resultColumns = new ArrayList<>();
        List<Map<String, Object>> resultRows = new ArrayList<>();

        root.open();
        try {
            Row row;
            while ((row = root.next()) != null) {
                if (resultColumns.isEmpty()) {
                    resultColumns.addAll(row.getColumnNamesAsList());
                }
                resultRows.add(row.toMap());
            }
        } finally {
            root.close();
        }

        return new ResultSet(resultColumns, resultRows, -1);
    }

    private ResultSet executeInsert(PlanNode.Insert plan) throws SqlException {
        TableMetadata table = plan.table();
        HeapFile hf = new HeapFile(Paths.get(table.getHeapFilePath()), table.getTableName());
        TupleDesc desc = TupleDesc.fromTable(table);

        int inserted = 0;
        try {
            for (List<Object> rowData : plan.rows()) {
                Row row = new Row();
                List<String> colNames = plan.columnNames();
                if (colNames.isEmpty()) {
                    for (int i = 0; i < table.getColumnCount(); i++) {
                        row.set(table.getColumn(i).getName(), rowData.get(i));
                    }
                } else {
                    for (int i = 0; i < colNames.size(); i++) {
                        row.set(colNames.get(i), rowData.get(i));
                    }
                    for (ColumnMetadata col : table.getColumns()) {
                        if (!row.hasColumn(col.getName())) {
                            row.set(col.getName(), null);
                        }
                    }
                }

                byte[] tuple = TupleSerializer.serialize(row, desc);
                int pageNum = hf.findOrCreatePageForInsert(tuple.length);
                Page page = hf.readPage(pageNum);
                page.insert(tuple);
                hf.writePage(page);
                inserted++;
            }
        } catch (IOException e) {
            throw new SqlException("Insert failed: " + e.getMessage(), e);
        }

        return new ResultSet(List.of(), List.of(), inserted);
    }

    private ResultSet executeUpdate(PlanNode.Update plan) throws SqlException {
        TableMetadata table = plan.table();
        HeapFile hf = new HeapFile(Paths.get(table.getHeapFilePath()), table.getTableName());
        TupleDesc desc = TupleDesc.fromTable(table);

        int affected = 0;
        try {
            for (int p = 0; p < hf.getNumPages(); p++) {
                Page page = hf.readPage(p);
                boolean dirty = false;
                for (int s = 0; s < page.getNumSlots(); s++) {
                    if (page.isSlotDeleted(s)) continue;
                    byte[] tuple = page.getTuple(s);
                    Row row = TupleSerializer.deserialize(tuple, desc);

                    if (plan.where() != null && !evaluatePredicate(plan.where(), row)) continue;

                    for (BoundStatement.UpdateSet set : plan.setClauses()) {
                        row.set(set.column().getName(), set.value());
                    }

                    byte[] newTuple = TupleSerializer.serialize(row, desc);
                    if (newTuple.length <= tuple.length) {
                        int off = page.getSlotTupleOffset(s);
                        System.arraycopy(newTuple, 0, page.getRawData(), off, newTuple.length);
                        dirty = true;
                    } else {
                        page.deleteTuple(s);
                        if (page.hasSpace(newTuple.length)) {
                            page.insert(newTuple);
                        } else {
                            hf.writePage(page);
                            int newPage = hf.findOrCreatePageForInsert(newTuple.length);
                            page = hf.readPage(newPage);
                            page.insert(newTuple);
                        }
                        dirty = true;
                    }
                    affected++;
                }
                if (dirty) hf.writePage(page);
            }
        } catch (IOException e) {
            throw new SqlException("Update failed: " + e.getMessage(), e);
        }
        return new ResultSet(List.of(), List.of(), affected);
    }

    private ResultSet executeDelete(PlanNode.Delete plan) throws SqlException {
        TableMetadata table = plan.table();
        HeapFile hf = new HeapFile(Paths.get(table.getHeapFilePath()), table.getTableName());
        TupleDesc desc = TupleDesc.fromTable(table);

        int affected = 0;
        try {
            for (int p = 0; p < hf.getNumPages(); p++) {
                Page page = hf.readPage(p);
                boolean dirty = false;
                for (int s = 0; s < page.getNumSlots(); s++) {
                    if (page.isSlotDeleted(s)) continue;
                    byte[] tuple = page.getTuple(s);
                    Row row = TupleSerializer.deserialize(tuple, desc);

                    if (plan.where() != null && !evaluatePredicate(plan.where(), row)) continue;

                    page.deleteTuple(s);
                    dirty = true;
                    affected++;
                }
                if (dirty) hf.writePage(page);
            }
        } catch (IOException e) {
            throw new SqlException("Delete failed: " + e.getMessage(), e);
        }
        return new ResultSet(List.of(), List.of(), affected);
    }

    private ResultSet executeCreateTable(PlanNode.CreateTable plan) throws SqlException {
        catalog.createTable(plan.tableName(), plan.columns());
        return new ResultSet(List.of(), List.of(), 0);
    }

    private ResultSet executeDropTable(PlanNode.DropTable plan) throws SqlException {
        catalog.dropTable(plan.tableName(), plan.ifExists());
        return new ResultSet(List.of(), List.of(), 0);
    }

    private ResultSet executeShowTables() {
        List<String> names = catalog.getTableNames();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("table_name", name);
            rows.add(row);
        }
        return new ResultSet(List.of("table_name"), rows, -1);
    }

    private ResultSet executeDescribe(PlanNode.DescribeTable plan) {
        TableMetadata table = plan.table();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ColumnMetadata col : table.getColumns()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("column", col.getName());
            row.put("type", col.getDataType().getSqlName());
            rows.add(row);
        }
        return new ResultSet(List.of("column", "type"), rows, -1);
    }

    boolean evaluatePredicate(BoundExpression expr, Row row) {
        if (expr instanceof BoundExpression.Binary bin) return evaluateBinary(bin, row);
        if (expr instanceof BoundExpression.ColumnRef cr) {
            Object val = row.get(cr.name());
            return val != null && !Boolean.FALSE.equals(val);
        }
        if (expr instanceof BoundExpression.Literal lit) {
            return lit.value() != null && !Boolean.FALSE.equals(lit.value());
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateBinary(BoundExpression.Binary bin, Row row) {
        Object leftVal = evaluateOperand(bin.left(), row);
        Object rightVal = evaluateOperand(bin.right(), row);

        if (leftVal == null || rightVal == null) return "!=".equals(bin.operator());
        if (leftVal instanceof Comparable l && rightVal instanceof Comparable r) {
            int cmp = l.compareTo(r);
            String op = bin.operator();
            if ("=".equals(op)) return cmp == 0;
            if ("!=".equals(op)) return cmp != 0;
            if ("<".equals(op)) return cmp < 0;
            if (">".equals(op)) return cmp > 0;
            if ("<=".equals(op)) return cmp <= 0;
            if (">=".equals(op)) return cmp >= 0;
            if ("AND".equals(op)) return Boolean.TRUE.equals(leftVal) && Boolean.TRUE.equals(rightVal);
            if ("OR".equals(op))  return Boolean.TRUE.equals(leftVal) || Boolean.TRUE.equals(rightVal);
        }
        throw new IllegalStateException("Cannot compare " + leftVal + " and " + rightVal);
    }

    private Object evaluateOperand(BoundExpression expr, Row row) {
        if (expr instanceof BoundExpression.Literal lit) return lit.value();
        if (expr instanceof BoundExpression.ColumnRef cr) return row.get(cr.name());
        if (expr instanceof BoundExpression.Binary bin) return evaluateBinary(bin, row);
        return null;
    }
}
