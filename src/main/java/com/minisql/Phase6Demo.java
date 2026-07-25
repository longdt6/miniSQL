package com.minisql;

import com.minisql.storage.*;

import java.nio.file.Paths;
import java.util.*;

/**
 * B+tree stress test with many keys.
 */
public class Phase6Demo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== miniSQL Phase 6: B+tree Index Demo ===\n");

        String dataDir = "data/mydb";
        BufferPool pool = new BufferPool(200);
        BTreeIndex index = new BTreeIndex(Paths.get(dataDir, "idx_test.idx"), pool);

        // ── Insert 600 random keys ──────────────────────────────────
        System.out.println("1. Inserting 600 random keys...");
        Random rng = new Random(42);
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            keys.add(rng.nextInt(10000));
        }

        for (int key : keys) {
            index.insert(key, new RowId(0, key));
        }
        System.out.println("   -> " + keys.size() + " inserts done, " + index.getNumPages() + " pages");

        // ── Tree structure ──────────────────────────────────────────
        System.out.println("\n2. Tree structure (" + index.getNumPages() + " pages):");
        index.printTree();

        // ── Verify all keys ─────────────────────────────────────────
        System.out.println("\n3. Verifying all 600 keys...");
        int found = 0;
        for (int key : keys) {
            if (index.search(key) != null) found++;
        }
        System.out.println("   -> " + found + " / " + keys.size() + " found "
            + (found == keys.size() ? "✓" : "✗"));

        // ── Range scan ─────────────────────────────────────────────
        System.out.println("\n4. Range scan [1000, 2000]:");
        List<RowId> range = index.searchRange(1000, 2000);
        System.out.println("   -> " + range.size() + " results");

        // ── Delete half ────────────────────────────────────────────
        System.out.println("\n5. Deleting first 300 keys...");
        for (int i = 0; i < 300; i++) {
            index.delete(keys.get(i));
        }
        System.out.println("   -> done, " + index.getNumPages() + " pages remain");

        // ── Verify after delete ────────────────────────────────────
        System.out.println("\n6. Verification after mass delete:");
        int zombie = 0, missing = 0;
        for (int i = 0; i < 300; i++) {
            if (index.search(keys.get(i)) != null) zombie++;
        }
        for (int i = 300; i < keys.size(); i++) {
            if (index.search(keys.get(i)) == null) missing++;
        }
        System.out.println("   -> " + zombie + " zombies, " + missing + " missing "
            + (zombie == 0 && missing == 0 ? "✓" : "✗"));

        // ── Full scan ──────────────────────────────────────────────
        System.out.println("\n7. Full scan of remaining keys:");
        List<RowId> all = index.searchFrom(Integer.MIN_VALUE);
        System.out.println("   -> " + all.size() + " keys remain");

        System.out.println("\n=== Phase 6 Demo Complete ===");
    }
}
