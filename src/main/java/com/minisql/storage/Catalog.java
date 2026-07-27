package com.minisql.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minisql.common.SqlException;
import com.minisql.types.DataType;
import com.minisql.types.TextType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The database catalog — tracks what tables exist, their schemas, and heap file locations.
 * Persisted as JSON at startup/shutdown and on every DDL change.
 */
public class Catalog {

    private static final Logger log = LoggerFactory.getLogger(Catalog.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            CatalogFile file = MAPPER.readValue(catalogFile.toFile(), CatalogFile.class);
            int maxId = 0;

            for (TableJson t : file.tables()) {
                if (t.id() > maxId) maxId = t.id();

                List<ColumnMetadata> columns = new ArrayList<>();
                for (ColumnJson c : t.columns()) {
                    DataType type;
                    try {
                        type = DataType.fromSqlName(c.type());
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown type '{}' for column '{}', defaulting to TEXT", c.type(), c.name());
                        type = TextType.INSTANCE;
                    }
                    columns.add(new ColumnMetadata(c.name(), type, c.position()));
                }
                columns.sort(Comparator.comparingInt(ColumnMetadata::position));

                TableMetadata meta = new TableMetadata(t.id(), t.name(), t.heapFile(), columns);
                tables.put(t.name().toLowerCase(), meta);
            }

            nextTableId.set(maxId + 1);

        } catch (IOException e) {
            log.warn("Could not load catalog: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(basePath);

            List<TableJson> tableJsons = new ArrayList<>();
            for (TableMetadata meta : tables.values()) {
                List<ColumnJson> columnJsons = new ArrayList<>();
                for (ColumnMetadata c : meta.columns()) {
                    columnJsons.add(new ColumnJson(c.name(), c.dataType().getSqlName(), c.position()));
                }
                tableJsons.add(new TableJson(meta.tableId(), meta.tableName(), meta.heapFilePath(), columnJsons));
            }

            MAPPER.writerWithDefaultPrettyPrinter().writeValue(catalogFile.toFile(), new CatalogFile(tableJsons));
        } catch (IOException e) {
            log.error("Could not save catalog: {}", e.getMessage());
        }
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

    // ── JSON DTOs (Jackson-serialized on-disk format) ───────────

    private record CatalogFile(List<TableJson> tables) {}

    private record TableJson(int id, String name, String heapFile, List<ColumnJson> columns) {}

    private record ColumnJson(String name, String type, int position) {}
}
