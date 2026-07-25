package com.minisql.storage;

/**
 * Uniquely identifies a page: which heap file and which page number within it.
 */
public record PageId(String heapFilePath, int pageNumber) {

    @Override
    public String toString() {
        return heapFilePath + ":" + pageNumber;
    }
}
