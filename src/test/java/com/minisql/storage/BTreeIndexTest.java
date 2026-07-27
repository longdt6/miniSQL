package com.minisql.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BTreeIndexTest {

    @Test
    void searchOnEmptyIndexReturnsNull(@TempDir Path dir) throws IOException {
        BTreeIndex index = new BTreeIndex(dir.resolve("idx.dat"), new BufferPool(4));
        assertThat(index.search(1)).isNull();
    }

    @Test
    void insertThenSearchFindsValue(@TempDir Path dir) throws IOException {
        BTreeIndex index = new BTreeIndex(dir.resolve("idx.dat"), new BufferPool(4));
        index.insert(10, new RowId(0, 0));
        index.insert(20, new RowId(0, 1));

        assertThat(index.search(10)).isEqualTo(new RowId(0, 0));
        assertThat(index.search(20)).isEqualTo(new RowId(0, 1));
        assertThat(index.search(99)).isNull();
    }

    @Test
    void insertWithExistingKeyOverwritesValue(@TempDir Path dir) throws IOException {
        BTreeIndex index = new BTreeIndex(dir.resolve("idx.dat"), new BufferPool(4));
        index.insert(1, new RowId(0, 0));
        index.insert(1, new RowId(5, 5));

        assertThat(index.search(1)).isEqualTo(new RowId(5, 5));
    }

    @Test
    void insertManyKeysForcesSplitAndAllRemainSearchable(@TempDir Path dir) throws IOException {
        BTreeIndex index = new BTreeIndex(dir.resolve("idx.dat"), new BufferPool(16));

        int count = 1200; // exceeds MAX_LEAF_KEYS (500) to force multiple splits
        for (int i = 0; i < count; i++) {
            index.insert(i, new RowId(i, 0));
        }

        assertThat(index.getNumPages()).isGreaterThan(1);
        for (int i = 0; i < count; i++) {
            assertThat(index.search(i)).isEqualTo(new RowId(i, 0));
        }
    }

    @Test
    void deleteRemovesKey(@TempDir Path dir) throws IOException {
        BTreeIndex index = new BTreeIndex(dir.resolve("idx.dat"), new BufferPool(4));
        index.insert(1, new RowId(0, 0));
        index.insert(2, new RowId(0, 1));

        index.delete(1);

        assertThat(index.search(1)).isNull();
        assertThat(index.search(2)).isEqualTo(new RowId(0, 1));
    }

    @Test
    void deleteAcrossManyKeysTriggersRebalanceAndPreservesSurvivors(@TempDir Path dir) throws IOException {
        BTreeIndex index = new BTreeIndex(dir.resolve("idx.dat"), new BufferPool(16));

        int count = 1200;
        for (int i = 0; i < count; i++) {
            index.insert(i, new RowId(i, 0));
        }
        // Delete every other key to force borrow/merge rebalancing
        for (int i = 0; i < count; i += 2) {
            index.delete(i);
        }

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                assertThat(index.search(i)).isNull();
            } else {
                assertThat(index.search(i)).isEqualTo(new RowId(i, 0));
            }
        }
    }

    @Test
    void searchRangeReturnsKeysWithinBounds(@TempDir Path dir) throws IOException {
        BTreeIndex index = new BTreeIndex(dir.resolve("idx.dat"), new BufferPool(4));
        for (int i = 0; i < 10; i++) {
            index.insert(i, new RowId(i, 0));
        }

        List<RowId> range = index.searchRange(3, 6);
        assertThat(range).containsExactly(
            new RowId(3, 0), new RowId(4, 0), new RowId(5, 0), new RowId(6, 0));
    }

    @Test
    void searchFromReturnsAllKeysGreaterOrEqual(@TempDir Path dir) throws IOException {
        BTreeIndex index = new BTreeIndex(dir.resolve("idx.dat"), new BufferPool(4));
        for (int i = 0; i < 5; i++) {
            index.insert(i, new RowId(i, 0));
        }

        List<RowId> result = index.searchFrom(3);
        assertThat(result).containsExactly(new RowId(3, 0), new RowId(4, 0));
    }
}
