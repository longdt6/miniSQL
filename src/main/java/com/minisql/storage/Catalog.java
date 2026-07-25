package com.minisql.storage;

import com.minisql.common.SqlException;
import com.minisql.types.DataType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The database catalog — tracks what tables exist, their schemas, and heap file locations.
 * Persisted as JSON at startup/shutdown and on every DDL change.
 *
 * We use simple regex-based JSON parsing to avoid external dependencies.
 */
public class Catalog {

    private final Path basePath;
    private final Path catalogFile;
    private final Map<String, TableMetadata> tables;
    private final AtomicInteger nextTableId;

    public Catalog(String basePath) {
        this.basePath = Paths.get(basePath);
        this.catalogFile = this.basePath.resolve("catalog.json");
        this.tables = new LinkedHashMap<>();
        this.nextTableId = new AtomicInteger(1);
        load();
    }

    // ── Table CRUD ─────────────────────────────────────────────

    /**
     * Register a new table in the catalog. Creates the heap file and persists to disk.
     */
    public synchronized TableMetadata createTable(String tableName, List<ColumnDef> columnDefs) throws SqlException {
        if (tables.containsKey(tableName.toLowerCase())) {
            throw new SqlException("Table '" + tableName + "' already exists");
        }

        int id = nextTableId.getAndIncrement();
        String heapPath = basePath.resolve(tableName + ".dat").toString();

        List<ColumnMetadata> columns = new ArrayList<>();
        for (int i = 0; i < columnDefs.size(); i++) {
            ColumnDef def = columnDefs.get(i);
            columns.add(new ColumnMetadata(def.name(), def.dataType(), i));
        }

        TableMetadata meta = new TableMetadata(id, tableName, heapPath, columns);
        tables.put(tableName.toLowerCase(), meta);

        // Create empty heap file
        HeapFile hf = new HeapFile(Paths.get(heapPath), tableName);
        try {
            if (hf.getNumPages() == 0) {
                hf.appendPage();
            }
        } catch (IOException e) {
            throw new SqlException("Failed to create heap file for table '" + tableName + "'", e);
        }

        save();
        return meta;
    }

    /**
     * Remove a table from the catalog. Does not delete the heap file (caller's choice).
     */
    public synchronized void dropTable(String tableName, boolean ifExists) throws SqlException {
        TableMetadata meta = tables.remove(tableName.toLowerCase());
        if (meta == null) {
            if (!ifExists) {
                throw new SqlException("Table '" + tableName + "' does not exist");
            }
            return;
        }
        save();
    }

    /**
     * Look up a table by name. Throws if not found.
     */
    public TableMetadata getTable(String tableName) throws SqlException {
        TableMetadata meta = tables.get(tableName.toLowerCase());
        if (meta == null) {
            throw new SqlException("Table '" + tableName + "' does not exist");
        }
        return meta;
    }

    /**
     * Check if a table exists.
     */
    public boolean tableExists(String tableName) {
        return tables.containsKey(tableName.toLowerCase());
    }

    /**
     * All table names, unsorted.
     */
    public List<String> getTableNames() {
        return new ArrayList<>(tables.keySet());
    }

    /** All tables. */
    public Collection<TableMetadata> getAllTables() {
        return Collections.unmodifiableCollection(tables.values());
    }

    // ── Persistence ────────────────────────────────────────────

    private void load() {
        if (!Files.exists(catalogFile)) {
            return;
        }

        try {
            String json = Files.readString(catalogFile);

            // Simple regex-based JSON parsing for our known schema
            // Match each table object: { "id": N, "name": "...", "heapFile": "...", "columns": [...] }
            Pattern tablePattern = Pattern.compile(
                "\\{\\s*\"id\"\\s*:\\s*(\\d+),\\s*\"name\"\\s*:\\s*\"([^\"]+)\",\\s*\"heapFile\"\\s*:\\s*\"([^\"]+)\",\\s*\"columns\"\\s*:\\s*\\[(.*?)\\]\\s*\\}",
                Pattern.DOTALL
            );

            Matcher m = tablePattern.matcher(json);
            int maxId = 0;

            while (m.find()) {
                int id = Integer.parseInt(m.group(1));
                String name = m.group(2);
                String heapFile = m.group(3);
                String columnsJson = m.group(4);

                if (id > maxId) maxId = id;

                List<ColumnMetadata> columns = parseColumns(columnsJson);
                TableMetadata meta = new TableMetadata(id, name, heapFile, columns);
                tables.put(name.toLowerCase(), meta);
            }

            nextTableId.set(maxId + 1);

        } catch (IOException e) {
            System.err.println("WARNING: Could not load catalog: " + e.getMessage());
        }
    }

    private List<ColumnMetadata> parseColumns(String columnsJson) {
        List<ColumnMetadata> columns = new ArrayList<>();
        Pattern colPattern = Pattern.compile(
            "\"name\"\\s*:\\s*\"([^\"]+)\",\\s*\"type\"\\s*:\\s*\"([^\"]+)\",\\s*\"position\"\\s*:\\s*(\\d+)"
        );

        Matcher m = colPattern.matcher(columnsJson);
        while (m.find()) {
            String colName = m.group(1);
            String typeName = m.group(2);
            int position = Integer.parseInt(m.group(3));

            DataType type;
            try {
                type = DataType.fromSqlName(typeName);
            } catch (IllegalArgumentException e) {
                System.err.println("WARNING: Unknown type '" + typeName + "' for column '" + colName + "', defaulting to TEXT");
                type = com.minisql.types.TextType.INSTANCE;
            }

            columns.add(new ColumnMetadata(colName, type, position));
        }

        // Sort by position to ensure correct order
        columns.sort(Comparator.comparingInt(ColumnMetadata::getPosition));
        return columns;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(basePath);
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"tables\": [\n");
            boolean first = true;

            for (TableMetadata meta : tables.values()) {
                if (!first) sb.append(",\n");
                first = false;

                sb.append("    {\n");
                sb.append("      \"id\": ").append(meta.getTableId()).append(",\n");
                sb.append("      \"name\": \"").append(escapeJson(meta.getTableName())).append("\",\n");
                sb.append("      \"heapFile\": \"").append(escapeJson(meta.getHeapFilePath())).append("\",\n");
                sb.append("      \"columns\": [\n");

                List<ColumnMetadata> cols = meta.getColumns();
                for (int i = 0; i < cols.size(); i++) {
                    ColumnMetadata c = cols.get(i);
                    sb.append("        {\"name\": \"").append(escapeJson(c.getName()))
                      .append("\", \"type\": \"").append(c.getDataType().getSqlName())
                      .append("\", \"position\": ").append(c.getPosition()).append("}");
                    if (i < cols.size() - 1) sb.append(",");
                    sb.append("\n");
                }

                sb.append("      ]\n");
                sb.append("    }");
            }

            sb.append("\n  ]\n}\n");
            Files.writeString(catalogFile, sb.toString());
        } catch (IOException e) {
            System.err.println("ERROR: Could not save catalog: " + e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── Shutdown ───────────────────────────────────────────────

    public void close() {
        save();
    }

    @Override
    public String toString() {
        return "Catalog{" + tables.keySet() + "}";
    }

    /**
     * Simple record for column definition before table creation.
     */
    public record ColumnDef(String name, DataType dataType) {}
}
