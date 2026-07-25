package com.minisql.storage;

import com.minisql.common.Constants;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the physical file for one B+tree index — a sequence of 8KB pages.
 * Like HeapFile but for .idx files instead of .dat files.
 */
public class IndexFile {

    private final Path filePath;

    public IndexFile(Path filePath) {
        this.filePath = filePath;
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot create index file: " + filePath, e);
        }
    }

    public Path getFilePath() {
        return filePath;
    }

    /**
     * Read a page from disk. Returns null if pageNum is out of bounds.
     */
    public IndexPage readPage(int pageNum) throws IOException {
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

        return new IndexPage(new PageId(filePath.toString(), pageNum), raw);
    }

    /**
     * Write a page to disk.
     */
    public void writePage(IndexPage page) throws IOException {
        int pageNum = page.getPageId().pageNumber();
        long offset = (long) pageNum * Constants.PAGE_SIZE;

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
            raf.seek(offset);
            raf.write(page.getRawData());
        }
    }

    /**
     * Append a new page and return an empty IndexPage (type set by caller).
     */
    public IndexPage appendPage() throws IOException {
        int pageNum = getNumPages();
        IndexPage page = new IndexPage(new PageId(filePath.toString(), pageNum));
        writePage(page);
        return page;
    }

    /**
     * Number of pages in this file.
     */
    public int getNumPages() throws IOException {
        long size = Files.size(filePath);
        return (int) (size / Constants.PAGE_SIZE);
    }

    /**
     * Get the root page. Assumes root is always page 0.
     */
    public IndexPage getRootPage() throws IOException {
        if (getNumPages() == 0) {
            return null;
        }
        return readPage(0);
    }

    @Override
    public String toString() {
        try {
            return "IndexFile{" + filePath.getFileName() + ", pages=" + getNumPages() + "}";
        } catch (IOException e) {
            return "IndexFile{" + filePath + "}";
        }
    }
}
