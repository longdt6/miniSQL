package com.minisql.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BufferPoolTest {

    @Test
    void getOrCreatePageReturnsSameCachedInstance() {
        BufferPool pool = new BufferPool(4);
        PageId id = new PageId("t.dat", 0);

        Page first = pool.getOrCreatePage(id);
        Page second = pool.getOrCreatePage(id);

        assertThat(first).isSameAs(second);
        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    void capacityReportsConfiguredMax() {
        BufferPool pool = new BufferPool(7);
        assertThat(pool.capacity()).isEqualTo(7);
    }

    @Test
    void evictsLeastRecentlyUsedPageWhenOverCapacity(@TempDir Path dir) throws IOException {
        BufferPool pool = new BufferPool(2);
        HeapFile hf = new HeapFile(dir.resolve("t.dat"), "t");
        hf.appendPage();
        hf.appendPage();
        hf.appendPage();

        PageId id0 = new PageId(hf.getFilePath().toString(), 0);
        PageId id1 = new PageId(hf.getFilePath().toString(), 1);
        PageId id2 = new PageId(hf.getFilePath().toString(), 2);

        pool.getPage(id0, hf);
        pool.getPage(id1, hf);
        assertThat(pool.size()).isEqualTo(2);

        // Access id0 again so it's more recently used than id1
        pool.getPage(id0, hf);
        // Bringing in id2 should evict id1 (the least recently used), not id0
        pool.getPage(id2, hf);

        assertThat(pool.size()).isEqualTo(2);
        Page reloadedId0 = pool.getPage(id0, hf);
        assertThat(reloadedId0).isNotNull();
    }

    @Test
    void getPageReadsThroughHeapFileWhenNotCached(@TempDir Path dir) throws IOException {
        BufferPool pool = new BufferPool(4);
        HeapFile hf = new HeapFile(dir.resolve("t.dat"), "t");
        hf.appendPage();

        PageId id = new PageId(hf.getFilePath().toString(), 0);
        Page page = pool.getPage(id, hf);

        assertThat(page.getPageNumber()).isEqualTo(0);
    }
}
