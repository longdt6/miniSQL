package com.minisql.storage;

/**
 * Points to a specific row in the heap file: which page and which slot.
 * Stored as leaf values in B+tree indexes.
 */
public record RowId(int pageNum, int slotNum) {

    /** Pack into a single long for compact storage. */
    public long toLong() {
        return ((long) pageNum << 32) | (slotNum & 0xFFFFFFFFL);
    }

    /** Unpack from a long. */
    public static RowId fromLong(long packed) {
        return new RowId((int) (packed >>> 32), (int) packed);
    }

    @Override
    public String toString() {
        return "(" + pageNum + "," + slotNum + ")";
    }
}
