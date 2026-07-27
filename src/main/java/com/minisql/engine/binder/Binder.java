package com.minisql.engine.binder;

import com.minisql.common.SqlException;
import com.minisql.engine.parser.ast.Expression;
import com.minisql.engine.parser.ast.Statement;
import com.minisql.storage.Catalog;
import com.minisql.storage.ColumnMetadata;
import com.minisql.storage.TableMetadata;
import com.minisql.types.*;

import java.util.*;

/**
 * Semantic analysis: resolves table/column names against the catalog,
 * type-checks expressions and values, expands SELECT * to actual columns.
 */
public class Binder {

    private final Catalog catalog;

    public Binder(Catalog catalog) {
        this.catalog = catalog;
    }

    // ── Bind statement ──────────────────────────────────────────

    /**
     * Bind a parsed statement, resolving names and type-checking.
     * Returns a BoundStatement with type information attached.
     */
    public BoundStatement bind(Statement stmt) throws SqlException {
        if (stmt instanceof Statement.Select s) return bindSelect(s);
        if (stmt instanceof Statement.Insert s)  return bindInsert(s);
        if (stmt instanceof Statement.Update s)  return bindUpdate(s);
        if (stmt instanceof Statement.Delete s)  return bindDelete(s);
        if (stmt instanceof Statement.CreateTable s) return bindCreateTable(s);
        if (stmt instanceof Statement.DropTable s)   return bindDropTable(s);
        if (stmt instanceof Statement.ShowTables)     return new BoundStatement.ShowTables();
        if (stmt instanceof Statement.Describe d)      return bindDescribe(d);
        throw new SqlException("Unknown statement type");
    }

    // ── SELECT ──────────────────────────────────────────────────

    private BoundStatement bindSelect(Statement.Select stmt) throws SqlException {
        TableMetadata table = catalog.getTable(stmt.tableName());

        // Expand * and resolved columns
        List<BoundColumn> selectColumns;
        if (stmt.columns().size() == 1 && stmt.columns().get(0).isStar()) {
            // SELECT *
            selectColumns = new ArrayList<>();
            for (ColumnMetadata col : table.columns()) {
                selectColumns.add(new BoundColumn(col.name(), col.dataType(), col.position()));
            }
        } else {
            selectColumns = new ArrayList<>();
            for (Statement.SelectColumn sc : stmt.columns()) {
                if (sc.expression() instanceof Expression.ColumnRef cr) {
                    ColumnMetadata col = table.column(cr.name())
                        .orElseThrow(() -> new SqlException(
                            "Column '" + cr.name() + "' not found in table '" + table.tableName() + "'"));
                    selectColumns.add(new BoundColumn(col.name(), col.dataType(), col.position()));
                } else if (sc.expression() instanceof Expression.Literal lit) {
                    DataType type = tokenValueToType(lit.value());
                    selectColumns.add(new BoundColumn(lit.value().toString(), type, -1));
                } else {
                    throw new SqlException("Unsupported expression in SELECT column list");
                }
            }
        }

        // Bind WHERE
        BoundExpression where = null;
        if (stmt.where() != null) {
            where = bindExpression(stmt.where(), table);
        }

        // Verify ORDER BY columns
        for (Statement.OrderBy ob : stmt.orderBy()) {
            table.column(ob.columnName())
                .orElseThrow(() -> new SqlException(
                    "Column '" + ob.columnName() + "' not found in ORDER BY"));
        }

        return new BoundStatement.Select(table, selectColumns, where,
            stmt.orderBy(), stmt.limit(), stmt.offset());
    }

    // ── INSERT ──────────────────────────────────────────────────

    private BoundStatement bindInsert(Statement.Insert stmt) throws SqlException {
        TableMetadata table = catalog.getTable(stmt.tableName());

        // If no column list specified, use all columns
        List<String> columnNames = stmt.columns().isEmpty()
            ? table.columns().stream().map(ColumnMetadata::name).toList()
            : stmt.columns();

        // Validate columns exist
        List<ColumnMetadata> colTypes = new ArrayList<>();
        for (String colName : columnNames) {
            ColumnMetadata col = table.column(colName)
                .orElseThrow(() -> new SqlException(
                    "Column '" + colName + "' not found in table '" + table.tableName() + "'"));
            colTypes.add(col);
        }

        if (colTypes.size() != table.columnCount() && !stmt.columns().isEmpty()) {
            // Partial insert — columns not specified will be NULL
        }

        // Check row counts and types
        List<List<Object>> boundRows = new ArrayList<>();
        for (int ri = 0; ri < stmt.rows().size(); ri++) {
            List<Expression> row = stmt.rows().get(ri);
            if (row.size() != columnNames.size()) {
                throw new SqlException(
                    "Expected " + columnNames.size() + " values, got " + row.size()
                    + " (row " + (ri + 1) + ")");
            }

            List<Object> boundRow = new ArrayList<>();
            for (int ci = 0; ci < row.size(); ci++) {
                Object value = evaluateLiteral(row.get(ci), colTypes.get(ci).dataType());
                boundRow.add(value);
            }
            boundRows.add(boundRow);
        }

        return new BoundStatement.Insert(table, columnNames, boundRows);
    }

    // ── UPDATE ──────────────────────────────────────────────────

    private BoundStatement bindUpdate(Statement.Update stmt) throws SqlException {
        TableMetadata table = catalog.getTable(stmt.tableName());

        List<BoundStatement.UpdateSet> sets = new ArrayList<>();
        for (Statement.UpdateSet us : stmt.setClauses()) {
            ColumnMetadata col = table.column(us.columnName())
                .orElseThrow(() -> new SqlException(
                    "Column '" + us.columnName() + "' not found"));
            Object value = evaluateLiteral(us.value(), col.dataType());
            sets.add(new BoundStatement.UpdateSet(col, value));
        }

        BoundExpression where = null;
        if (stmt.where() != null) {
            where = bindExpression(stmt.where(), table);
        }

        return new BoundStatement.Update(table, sets, where);
    }

    // ── DELETE ──────────────────────────────────────────────────

    private BoundStatement bindDelete(Statement.Delete stmt) throws SqlException {
        TableMetadata table = catalog.getTable(stmt.tableName());

        BoundExpression where = null;
        if (stmt.where() != null) {
            where = bindExpression(stmt.where(), table);
        }

        return new BoundStatement.Delete(table, where);
    }

    // ── CREATE TABLE ────────────────────────────────────────────

    private BoundStatement bindCreateTable(Statement.CreateTable stmt) throws SqlException {
        if (catalog.tableExists(stmt.tableName())) {
            if (stmt.ifNotExists()) return new BoundStatement.CreateTable(stmt.tableName(), List.of());
            throw new SqlException("Table '" + stmt.tableName() + "' already exists");
        }

        // Resolve column types — reuse our DataType.fromSqlName
        List<Catalog.ColumnDef> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Statement.ColumnDef cd : stmt.columns()) {
            if (!seen.add(cd.name().toLowerCase())) {
                throw new SqlException("Duplicate column name: " + cd.name());
            }
            DataType dt;
            try {
                dt = DataType.fromSqlName(cd.typeName());
            } catch (IllegalArgumentException e) {
                throw new SqlException("Unknown type: " + cd.typeName());
            }
            columns.add(new Catalog.ColumnDef(cd.name(), dt));
        }

        return new BoundStatement.CreateTable(stmt.tableName(), columns);
    }

    // ── DROP TABLE ──────────────────────────────────────────────

    private BoundStatement bindDropTable(Statement.DropTable stmt) throws SqlException {
        return new BoundStatement.DropTable(stmt.tableName(), stmt.ifExists());
    }

    // ── DESCRIBE ────────────────────────────────────────────────

    private BoundStatement bindDescribe(Statement.Describe stmt) throws SqlException {
        TableMetadata table = catalog.getTable(stmt.tableName());
        return new BoundStatement.Describe(table);
    }

    // ── Expression binding ──────────────────────────────────────

    private BoundExpression bindExpression(Expression expr, TableMetadata table) throws SqlException {
        if (expr instanceof Expression.Binary bin) {
            BoundExpression left = bindExpression(bin.left(), table);
            BoundExpression right = bindExpression(bin.right(), table);

            // Type check comparison operators
            if (isComparisonOp(bin.operator()) && left.type() != null && right.type() != null) {
                if (left.type() != right.type()) {
                    throw new SqlException(
                        "Cannot compare " + left.type().getSqlName() + " with " + right.type().getSqlName());
                }
            }

            return BoundExpression.binary(left, bin.operator(), right,
                left.type()); // result type = left type for now
        }

        if (expr instanceof Expression.ColumnRef cr) {
            ColumnMetadata col = table.column(cr.name())
                .orElseThrow(() -> new SqlException(
                    "Column '" + cr.name() + "' not found in table '" + table.tableName() + "'"));
            return BoundExpression.columnRef(col.name(), col.dataType(), col.position());
        }

        if (expr instanceof Expression.Literal lit) {
            DataType type = tokenValueToType(lit.value());
            Object value = lit.value().value();
            return new BoundExpression.Literal(value, type);
        }

        throw new SqlException("Unsupported expression type: " + expr.getClass().getSimpleName());
    }

    // ── Helpers ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object evaluateLiteral(Expression expr, DataType targetType) throws SqlException {
        if (expr instanceof Expression.Literal lit) {
            Object value = lit.value().value();
            // Coerce to target type
            if (targetType instanceof IntegerType) {
                if (value instanceof Integer i) return i;
                if (value instanceof String s) {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        throw new SqlException("Expected INTEGER, got '" + s + "'");
                    }
                }
                throw new SqlException("Expected INTEGER, got " + value.getClass().getSimpleName());
            }
            if (targetType instanceof FloatType) {
                if (value instanceof Double d) return d;
                if (value instanceof Integer i) return (double) i;
                throw new SqlException("Expected FLOAT, got " + value.getClass().getSimpleName());
            }
            if (targetType instanceof TextType) {
                return value.toString();
            }
            if (targetType instanceof BooleanType) {
                if (value instanceof Boolean b) return b;
                throw new SqlException("Expected BOOLEAN, got " + value.getClass().getSimpleName());
            }
            return value;
        }
        throw new SqlException("Expected literal value");
    }

    private DataType tokenValueToType(Expression.TokenValue tv) {
        return switch (tv.type()) {
            case INT -> IntegerType.INSTANCE;
            case FLOAT -> FloatType.INSTANCE;
            case STRING -> TextType.INSTANCE;
            case BOOLEAN -> BooleanType.INSTANCE;
            case NULL -> null;
        };
    }

    private boolean isComparisonOp(String op) {
        return "=".equals(op) || "!=".equals(op) || "<".equals(op)
            || ">".equals(op) || "<=".equals(op) || ">=".equals(op);
    }
}

// BoundColumn: see BoundColumn.java
// BoundStatement: see BoundStatement.java
// BoundExpression: see BoundExpression.java

