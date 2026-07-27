package com.minisql.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HeapFileTest {

    private byte[] tuple(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void appendPageIncreasesPageCount(@TempDir Path dir) throws IOException {
        HeapFile hf = new HeapFile(dir.resolve("t.dat"), "t");
        assertThat(hf.getNumPages()).isEqualTo(0);
        hf.appendPage();
        hf.appendPage();
        assertThat(hf.getNumPages()).isEqualTo(2);
    }

    @Test
    void writeThenReadPageRoundTrips(@TempDir Path dir) throws IOException {
        HeapFile hf = new HeapFile(dir.resolve("t.dat"), "t");
        hf.appendPage();
        Page page = hf.readPage(0);
        page.insert(tuple("row1"));
        hf.writePage(page);

        Page reloaded = hf.readPage(0);
        assertThat(reloaded.getTuple(0)).isEqualTo(tuple("row1"));
    }

    @Test
    void readPageOutOfBoundsReturnsNull(@TempDir Path dir) throws IOException {
        HeapFile hf = new HeapFile(dir.resolve("t.dat"), "t");
        hf.appendPage();
        assertThat(hf.readPage(5)).isNull();
    }

    @Test
    void findOrCreatePageForInsertReusesPageWithSpace(@TempDir Path dir) throws IOException {
        HeapFile hf = new HeapFile(dir.resolve("t.dat"), "t");
        int pageNum = hf.findOrCreatePageForInsert(100);
        assertThat(pageNum).isEqualTo(0);
        assertThat(hf.getNumPages()).isEqualTo(1);

        // Small insert should reuse page 0, not create a new page
        int again = hf.findOrCreatePageForInsert(100);
        assertThat(again).isEqualTo(0);
        assertThat(hf.getNumPages()).isEqualTo(1);
    }

    @Test
    void tupleIteratorSkipsDeletedAcrossMultiplePages(@TempDir Path dir) throws IOException {
        HeapFile hf = new HeapFile(dir.resolve("t.dat"), "t");

        Page page0 = hf.readPage(hf.appendPage());
        page0.insert(tuple("keep1"));
        int deletedSlot = page0.insert(tuple("delete-me"));
        page0.deleteTuple(deletedSlot);
        hf.writePage(page0);

        Page page1 = hf.readPage(hf.appendPage());
        page1.insert(tuple("keep2"));
        hf.writePage(page1);

        HeapFile.TupleIterator it = hf.tupleIterator();
        java.util.List<String> seen = new java.util.ArrayList<>();
        while (it.hasNext()) {
            seen.add(new String(it.next(), StandardCharsets.UTF_8));
        }

        assertThat(seen).containsExactly("keep1", "keep2");
    }

    @Test
    void getLastPageNumCreatesFirstPageWhenEmpty(@TempDir Path dir) throws IOException {
        HeapFile hf = new HeapFile(dir.resolve("t.dat"), "t");
        assertThat(hf.getLastPageNum()).isEqualTo(0);
        assertThat(hf.getNumPages()).isEqualTo(1);
    }
}
