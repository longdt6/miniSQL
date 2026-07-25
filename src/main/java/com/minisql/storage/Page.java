package com.minisql.storage;

import com.minisql.common.Constants;

/**
 * An 8KB disk page using the slotted-page layout.
 *
 * Layout:
 * ┌─ Header (24B) ──────────────────────┐
 * │  pageNumber (4B)                    │
 * │  numSlots (2B)                      │
 * │  freeSpaceOffset (2B)  ← slot dir   │
 * │  freeSpaceEnd (2B)      ← tuple data│
 * │  slotDirOffset (2B)                 │
 * │  nextPageId (4B)                    │
 * │  checksum (4B)                      │
 * │  unused (4B)                        │
 * ├─ Slot Directory (6B each, grows ↓) ─┤
 * │  [offset:2B, flags:2B, length:2B]   │
 * ├─ FREE SPACE ────────────────────────┤
 * ├─ Tuple Data (variable, grows ↑) ────┤
 * └─────────────────────────────────────┘ byte 8191
 */
public class Page {

    private final byte[] data;
    private boolean dirty;
    private final PageId pageId;
    private final String heapFilePath;

    // Offset constants within the 24-byte header
    private static final int OFF_PAGE_NUMBER   = 0;
    private static final int OFF_NUM_SLOTS     = 4;
    private static final int OFF_FREE_OFFSET   = 6;
    private static final int OFF_FREE_END      = 8;
    private static final int OFF_SLOT_DIR      = 10;
    private static final int OFF_NEXT_PAGE     = 12;
    private static final int OFF_CHECKSUM      = 16;

    /**
     * Create a new empty page.
     */
    public Page(String heapFilePath, int pageNumber) {
        this.heapFilePath = heapFilePath;
        this.pageId = new PageId(heapFilePath, pageNumber);
        this.data = new byte[Constants.PAGE_SIZE];
        this.dirty = true;

        writeInt(OFF_PAGE_NUMBER, pageNumber);
        writeShort(OFF_NUM_SLOTS, (short) 0);
        writeShort(OFF_FREE_OFFSET, (short) Constants.PAGE_HEADER_SIZE);
        writeShort(OFF_FREE_END, (short) Constants.PAGE_SIZE);
        writeShort(OFF_SLOT_DIR, (short) Constants.PAGE_HEADER_SIZE);
        writeInt(OFF_NEXT_PAGE, -1);
        writeInt(OFF_CHECKSUM, 0);
    }

    /**
     * Load a page from raw bytes (read from disk).
     */
    public Page(String heapFilePath, int pageNumber, byte[] rawData) {
        if (rawData.length != Constants.PAGE_SIZE) {
            throw new IllegalArgumentException(
                "Expected " + Constants.PAGE_SIZE + " bytes, got " + rawData.length);
        }
        this.heapFilePath = heapFilePath;
        this.pageId = new PageId(heapFilePath, pageNumber);
        this.data = rawData;
        this.dirty = false;
    }

    // ── Header accessors ─────────────────────────────────────

    public PageId getPageId() { return pageId; }
    public String getHeapFilePath() { return heapFilePath; }
    public int getPageNumber() { return readInt(OFF_PAGE_NUMBER); }
    public int getNumSlots() { return readShort(OFF_NUM_SLOTS); }
    public int getFreeSpaceOffset() { return readShort(OFF_FREE_OFFSET); }
    public int getFreeSpaceEnd() { return readShort(OFF_FREE_END); }
    public int getSlotDirOffset() { return readShort(OFF_SLOT_DIR); }
    public int getNextPageId() { return readInt(OFF_NEXT_PAGE); }

    public boolean isDirty() { return dirty; }
    public void markClean() { this.dirty = false; }

    // ── Slot directory access ────────────────────────────────

    private int slotOffset(int slot) {
        return getSlotDirOffset() + slot * Constants.SLOT_SIZE;
    }

    public int getSlotTupleOffset(int slot) {
        return readShort(slotOffset(slot));
    }

    public int getSlotFlags(int slot) {
        return readShort(slotOffset(slot) + 2);
    }

    public int getSlotLength(int slot) {
        return readShort(slotOffset(slot) + 4);
    }

    public boolean isSlotDeleted(int slot) {
        return (getSlotFlags(slot) & Constants.SLOT_FLAG_DELETED) != 0;
    }

    private void setSlotEntry(int slot, int offset, int flags, int length) {
        int pos = slotOffset(slot);
        writeShort(pos, (short) offset);
        writeShort(pos + 2, (short) flags);
        writeShort(pos + 4, (short) length);
    }

    // ── Tuple operations ─────────────────────────────────────

    /**
     * Insert a tuple into this page. Returns the assigned slot number.
     * Caller must ensure hasSpace(tuple.length) is true before calling.
     */
    public int insert(byte[] tuple) {
        int requiredSpace = tuple.length + Constants.SLOT_SIZE;
        if (!hasSpace(tuple.length)) {
            throw new IllegalStateException(
                "Page " + pageId + " has insufficient space. Free: " + getFreeSpace() + ", needed: " + requiredSpace);
        }

        int freeOffset = getFreeSpaceOffset();
        int freeEnd = getFreeSpaceEnd();
        int newSlot = getNumSlots();

        // Write tuple at bottom
        int tupleOffset = freeEnd - tuple.length;
        System.arraycopy(tuple, 0, data, tupleOffset, tuple.length);

        // Write slot entry at top
        setSlotEntry(newSlot, tupleOffset, 0, tuple.length);

        // Update header
        writeShort(OFF_NUM_SLOTS, (short) (newSlot + 1));
        writeShort(OFF_FREE_OFFSET, (short) (freeOffset + Constants.SLOT_SIZE));
        writeShort(OFF_FREE_END, (short) tupleOffset);

        dirty = true;
        return newSlot;
    }

    /**
     * Read the tuple at the given slot. Returns the raw bytes.
     */
    public byte[] getTuple(int slot) {
        if (slot < 0 || slot >= getNumSlots()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " out of range [0, " + getNumSlots() + ")");
        }
        int offset = getSlotTupleOffset(slot);
        int length = getSlotLength(slot);
        byte[] tuple = new byte[length];
        System.arraycopy(data, offset, tuple, 0, length);
        return tuple;
    }

    /**
     * Soft-delete the tuple at the given slot by setting the DELETED flag.
     */
    public void deleteTuple(int slot) {
        int pos = slotOffset(slot) + 2;
        short flags = (short) (readShort(pos) | Constants.SLOT_FLAG_DELETED);
        writeShort(pos, flags);
        dirty = true;
    }

    /**
     * Update the tuple at the given slot. If the new tuple is the same size,
     * overwrites in place. Otherwise, deletes the old slot and inserts a new tuple.
     */
    public void updateTuple(int slot, byte[] newTuple) {
        if (slot < 0 || slot >= getNumSlots()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " out of range");
        }

        int oldOffset = getSlotTupleOffset(slot);
        int oldLength = getSlotLength(slot);

        if (newTuple.length <= oldLength) {
            // Overwrite in place
            System.arraycopy(newTuple, 0, data, oldOffset, newTuple.length);
            setSlotEntry(slot, oldOffset, 0, newTuple.length);
        } else {
            // Delete old, insert new
            deleteTuple(slot);
            // Can't reuse slot index — the old slot stays as a tombstone.
            // We write the new tuple as a fresh slot.
            int newSlot = insert(newTuple);
            // Note: the caller (HeapFile or executor) is responsible for tracking
            // that the logical tuple moved from oldSlot to newSlot.
        }
        dirty = true;
    }

    // ── Space management ─────────────────────────────────────

    /** Check whether a tuple of byteCount bytes will fit. */
    public boolean hasSpace(int byteCount) {
        int required = byteCount + Constants.SLOT_SIZE;
        return getFreeSpaceOffset() + required <= getFreeSpaceEnd();
    }

    /** Total free space in bytes (including room needed for slot entries). */
    public int getFreeSpace() {
        return getFreeSpaceEnd() - getFreeSpaceOffset();
    }

    /** Usable free space for tuple data (accounts for slot overhead). */
    public int getUsableFreeSpace() {
        return getFreeSpace() - Constants.SLOT_SIZE;
    }

    /**
     * Compact the page: remove deleted tuples, pack live tuples together,
     * rebuild the slot directory with only live entries.
     * Returns the new slot index for each old live slot, or -1 for deleted slots.
     */
    public int[] compact() {
        int oldNumSlots = getNumSlots();

        // Gather live tuples
        int liveCount = 0;
        for (int s = 0; s < oldNumSlots; s++) {
            if (!isSlotDeleted(s)) liveCount++;
        }

        byte[][] liveTuples = new byte[liveCount][];
        int[] oldToNew = new int[oldNumSlots];
        java.util.Arrays.fill(oldToNew, -1);

        int idx = 0;
        for (int s = 0; s < oldNumSlots; s++) {
            if (!isSlotDeleted(s)) {
                oldToNew[s] = idx;
                liveTuples[idx++] = getTuple(s);
            }
        }

        // Rebuild page from scratch
        writeShort(OFF_NUM_SLOTS, (short) 0);
        writeShort(OFF_FREE_OFFSET, (short) Constants.PAGE_HEADER_SIZE);
        writeShort(OFF_FREE_END, (short) Constants.PAGE_SIZE);

        for (byte[] tuple : liveTuples) {
            insert(tuple);
        }

        dirty = true;
        return oldToNew;
    }

    /** Number of live (non-deleted) tuples. */
    public int getLiveTupleCount() {
        int count = 0;
        int numSlots = getNumSlots();
        for (int s = 0; s < numSlots; s++) {
            if (!isSlotDeleted(s)) count++;
        }
        return count;
    }

    // ── Disk I/O helpers (package-private, used by HeapFile) ──

    /** Return the raw 8192-byte array for writing to disk. */
    public byte[] getRawData() {
        return data;
    }

    // ── Binary read/write helpers ────────────────────────────

    private int readInt(int offset) {
        return ((data[offset] & 0xFF) << 24)
             | ((data[offset + 1] & 0xFF) << 16)
             | ((data[offset + 2] & 0xFF) << 8)
             |  (data[offset + 3] & 0xFF);
    }

    private void writeInt(int offset, int value) {
        data[offset]     = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private int readShort(int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private void writeShort(int offset, short value) {
        data[offset]     = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    // ── Standard ─────────────────────────────────────────────

    @Override
    public String toString() {
        return "Page{" + pageId + ", slots=" + getNumSlots()
            + " (live=" + getLiveTupleCount() + "), free=" + getFreeSpace() + "}";
    }
}
