package com.minisql.storage;

import com.minisql.common.Constants;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Manages the physical file for one table — a sequence of 8KB pages.
 * Provides page-level read/write and an iterator over all live tuples.
 */
public class HeapFile {

    private final Path filePath;
    private final String tableName;

    /**
     * Open or create a heap file for the given table.
     */
    public HeapFile(Path filePath, String tableName) {
        this.filePath = filePath;
        this.tableName = tableName;
        // Ensure the file and parent directories exist
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot create heap file: " + filePath, e);
        }
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getTableName() {
        return tableName;
    }

    /**
     * Read a page from disk. Returns null if pageNum is out of bounds.
     */
    public Page readPage(int pageNum) throws IOException {
        long offset = (long) pageNum * Constants.PAGE_SIZE;
        long fileSize = Files.size(filePath);

        if (offset + Constants.PAGE_SIZE > fileSize) {
            return null;
        }

        byte[] raw = new byte[Constants.PAGE_SIZE];
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            raf.seek(offset);
            raf.readFully(raw);
        }

        return new Page(filePath.toString(), pageNum, raw);
    }

    /**
     * Write a page to disk.
     */
    public void writePage(Page page) throws IOException {
        int pageNum = page.getPageNumber();
        long offset = (long) pageNum * Constants.PAGE_SIZE;

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
            raf.seek(offset);
            raf.write(page.getRawData());
        }
        page.markClean();
    }

    /**
     * Append a new empty page and return its page number.
     */
    public int appendPage() throws IOException {
        int pageNum = getNumPages();
        Page page = new Page(filePath.toString(), pageNum);
        writePage(page);
        return pageNum;
    }

    /**
     * Get the last page number, or -1 if the file is empty.
     * Creates page 0 if the file is empty.
     */
    public int getLastPageNum() throws IOException {
        int numPages = getNumPages();
        if (numPages == 0) {
            return appendPage();
        }
        return numPages - 1;
    }

    /**
     * Number of pages in this file.
     */
    public int getNumPages() throws IOException {
        long size = Files.size(filePath);
        return (int) (size / Constants.PAGE_SIZE);
    }

    /**
     * Find a page that has enough space for byteCount bytes, or append a new one.
     */
    public int findOrCreatePageForInsert(int byteCount) throws IOException {
        int numPages = getNumPages();
        // Scan existing pages for space (start from last — most likely to have room)
        for (int p = numPages - 1; p >= 0; p--) {
            Page page = readPage(p);
            if (page != null && page.hasSpace(byteCount)) {
                return p;
            }
        }
        // No space — append a new page
        return appendPage();
    }

    /**
     * Iterate over all live tuples across all pages.
     * Each call to next() returns a byte[] — the raw tuple bytes.
     */
    public TupleIterator tupleIterator() {
        return new TupleIterator();
    }

    /**
     * An iterator that walks all pages and returns raw tuple bytes, skipping deleted slots.
     */
    public class TupleIterator {
        private int currentPage;
        private int currentSlot;
        private Page page;

        public TupleIterator() {
            this.currentPage = -1;
            this.currentSlot = -1;
            this.page = null;
        }

        public boolean hasNext() {
            try {
                // Advance until we find a live slot or run out of pages
                while (true) {
                    // Need to load current page?
                    if (page == null) {
                        currentPage++;
                        if (currentPage >= getNumPages()) {
                            return false;
                        }
                        page = readPage(currentPage);
                        currentSlot = 0;
                    }

                    // Scan slots in current page
                    while (currentSlot < page.getNumSlots()) {
                        if (!page.isSlotDeleted(currentSlot)) {
                            return true;
                        }
                        currentSlot++;
                    }

                    // Page exhausted — move to next
                    page = null;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error iterating heap file: " + filePath, e);
            }
        }

        public byte[] next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            byte[] tuple = page.getTuple(currentSlot);
            currentSlot++;
            return tuple;
        }

        /** Current page number, or -1 before first call. */
        public int currentPageNumber() {
            return currentPage;
        }
    }

    @Override
    public String toString() {
        try {
            return "HeapFile{" + filePath.getFileName() + ", pages=" + getNumPages() + "}";
        } catch (IOException e) {
            return "HeapFile{" + filePath + "}";
        }
    }
}
