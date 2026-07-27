package com.minisql;

import com.minisql.common.Constants;
import com.minisql.storage.*;
import com.minisql.types.*;

import java.io.IOException;
import java.util.List;

/**
 * End-to-end demonstration of Phase 1: the storage engine.
 * Creates a table, inserts rows, scans them back, and verifies everything.
 *
 * Run: mvn exec:java -Dexec.mainClass="com.minisql.Phase1Demo"
 */
public class Phase1Demo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== miniSQL Phase 1: Storage Engine Demo ===\n");

        // ── Setup ──────────────────────────────────────────────
        String dataDir = "data/mydb";
        System.out.println("1. Initializing catalog at: " + dataDir);
        Catalog catalog = new Catalog(dataDir);

        // ── CREATE TABLE users ─────────────────────────────────
        System.out.println("\n2. Creating table: users (id INTEGER, name TEXT, age INTEGER)");
        TableMetadata users = catalog.createTable("users", List.of(
            new Catalog.ColumnDef("id", IntegerType.INSTANCE),
            new Catalog.ColumnDef("name", TextType.INSTANCE),
            new Catalog.ColumnDef("age", IntegerType.INSTANCE)
        ));
        System.out.println("   -> " + users);

        // ── INSERT rows ────────────────────────────────────────
        System.out.println("\n3. Inserting 3 rows...");
        HeapFile hf = new HeapFile(
            java.nio.file.Paths.get(users.heapFilePath()),
            users.tableName()
        );
        TupleDesc desc = TupleDesc.fromTable(users);

        insertRowDirect(hf, desc, "id", 1, "name", "Alice", "age", 30);
        insertRowDirect(hf, desc, "id", 2, "name", "Bob", "age", 25);
        insertRowDirect(hf, desc, "id", 3, "name", "Carol", "age", 35);
        System.out.println("   -> 3 rows inserted");

        // ── SELECT * (full scan) ───────────────────────────────
        System.out.println("\n4. Scanning all rows (SELECT *):");
        int rowCount = 0;
        HeapFile.TupleIterator iter = hf.tupleIterator();
        while (iter.hasNext()) {
            byte[] tuple = iter.next();
            Row row = TupleSerializer.deserialize(tuple, desc);
            System.out.printf("   Row %d: {id=%d, name='%s', age=%d}%n",
                ++rowCount,
                row.getInt("id"),
                row.getString("name"),
                row.getInt("age"));
        }
        System.out.println("   -> " + rowCount + " rows found");

        // ── Verify pages on disk ───────────────────────────────
        System.out.println("\n5. Disk inspection:");
        hf = new HeapFile(java.nio.file.Paths.get(users.heapFilePath()), users.tableName());
        int numPages = hf.getNumPages();
        System.out.println("   " + hf);
        for (int p = 0; p < numPages; p++) {
            Page page = hf.readPage(p);
            if (page != null) {
                System.out.printf("   Page %d: %d slots (%d live), %d bytes free%n",
                    p,
                    page.getNumSlots(),
                    page.getLiveTupleCount(),
                    page.getFreeSpace());
            }
        }

        // ── Delete and verify ──────────────────────────────────
        System.out.println("\n6. Soft-deleting slot 1 (Bob)...");
        Page page0 = hf.readPage(0);
        page0.deleteTuple(1);
        hf.writePage(page0);
        System.out.println("   -> marked slot 1 as deleted");

        System.out.println("   Scanning live rows after delete:");
        rowCount = 0;
        iter = hf.tupleIterator();
        while (iter.hasNext()) {
            byte[] tuple = iter.next();
            Row row = TupleSerializer.deserialize(tuple, desc);
            System.out.printf("   Row %d: {id=%d, name='%s', age=%d}%n",
                ++rowCount, row.getInt("id"), row.getString("name"), row.getInt("age"));
        }
        System.out.println("   -> " + rowCount + " live rows (expected 2)");

        // ── Compact ────────────────────────────────────────────
        System.out.println("\n7. Compacting page 0...");
        page0 = hf.readPage(0);
        int[] oldToNew = page0.compact();
        hf.writePage(page0);
        System.out.println("   -> compacted, " + page0.getNumSlots() + " slots remain");
        for (int s = 0; s < oldToNew.length; s++) {
            if (oldToNew[s] >= 0) {
                System.out.printf("   Old slot %d → new slot %d%n", s, oldToNew[s]);
            } else {
                System.out.printf("   Old slot %d → REMOVED (was deleted)%n", s);
            }
        }

        // ── Insert more rows ───────────────────────────────────
        System.out.println("\n8. Inserting 2 more rows...");
        insertRowDirect(hf, desc, "id", 4, "name", "Dave", "age", 28);
        insertRowDirect(hf, desc, "id", 5, "name", "Eve", "age", 22);

        System.out.println("   Final scan:");
        rowCount = 0;
        iter = hf.tupleIterator();
        while (iter.hasNext()) {
            byte[] tuple = iter.next();
            Row row = TupleSerializer.deserialize(tuple, desc);
            System.out.printf("   Row %d: {id=%d, name='%s', age=%d}%n",
                ++rowCount, row.getInt("id"), row.getString("name"), row.getInt("age"));
        }
        System.out.println("   -> " + rowCount + " rows total");

        // ── Cleanup ────────────────────────────────────────────
        catalog.close();
        System.out.println("\n=== Phase 1 Demo Complete ===");
    }

    /**
     * Insert a row directly via HeapFile (no buffer pool caching).
     * Ensures the disk is always the source of truth for demo steps.
     */
    private static void insertRowDirect(HeapFile hf, TupleDesc desc,
                                         Object... keysAndValues) throws IOException {
        Row row = new Row();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.set((String) keysAndValues[i], keysAndValues[i + 1]);
        }

        byte[] tuple = TupleSerializer.serialize(row, desc);
        int pageNum = hf.findOrCreatePageForInsert(tuple.length);
        Page page = hf.readPage(pageNum);
        page.insert(tuple);
        hf.writePage(page);
    }
}
