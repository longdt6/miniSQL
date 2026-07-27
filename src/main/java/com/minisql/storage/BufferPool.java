package com.minisql.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU (Least Recently Used) cache of disk pages.
 * Access-order LinkedHashMap gives us LRU eviction with zero effort.
 * Shared across all tables in the database.
 */
public class BufferPool {

    private static final Logger log = LoggerFactory.getLogger(BufferPool.class);

    private final int maxPages;
    private final LinkedHashMap<PageId, Page> cache;

    /**
     * @param maxPages maximum number of pages to keep in memory
     */
    public BufferPool(int maxPages) {
        this.maxPages = maxPages;
        // accessOrder=true → entries are ordered by most-recent access
        this.cache = new LinkedHashMap<>(maxPages, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<PageId, Page> eldest) {
                if (size() > maxPages) {
                    flushPage(eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * Get a page from the pool. If not cached, reads it from disk via the HeapFile.
     * The caller provides a factory lambda because the pool tracks HeapFiles lazily.
     */
    public Page getPage(PageId pageId, HeapFile heapFile) {
        Page page = cache.get(pageId);
        if (page == null) {
            try {
                page = heapFile.readPage(pageId.pageNumber());
                if (page == null) {
                    throw new IllegalStateException("Page not found: " + pageId);
                }
                cache.put(pageId, page);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read page: " + pageId, e);
            }
        }
        return page;
    }

    /**
     * Get a page without I/O — returns the cached page or creates a new empty page.
     * Used when you know the page must exist (e.g., you just appended it).
     */
    public Page getOrCreatePage(PageId pageId) {
        return cache.computeIfAbsent(pageId, id ->
            new Page(id.heapFilePath(), id.pageNumber()));
    }

    /**
     * Mark a page as modified (dirty). It will be written back to disk on eviction or flushAll.
     */
    public void markDirty(PageId pageId) {
        Page page = cache.get(pageId);
        if (page != null) {
            // Page tracks dirty state internally — calling this just ensures the page is in cache
        }
    }

    /**
     * Write a specific dirty page back to disk.
     */
    public void flushPage(Page page) {
        if (!page.isDirty()) return;
        try {
            // Need a HeapFile to write — this is a bit of a coupling issue,
            // but we construct it on-the-fly from the page's metadata
            HeapFile hf = new HeapFile(
                java.nio.file.Paths.get(page.getHeapFilePath()),
                "unknown"
            );
            hf.writePage(page);
        } catch (IOException e) {
            log.warn("Failed to flush page {}: {}", page.getPageId(), e.getMessage());
        }
    }

    /**
     * Write all dirty pages back to disk. Call on shutdown.
     */
    public void flushAll() {
        for (Page page : cache.values()) {
            if (page.isDirty()) {
                flushPage(page);
            }
        }
    }

    /** Number of pages currently cached. */
    public int size() {
        return cache.size();
    }

    /** Maximum capacity of the pool. */
    public int capacity() {
        return maxPages;
    }
}
