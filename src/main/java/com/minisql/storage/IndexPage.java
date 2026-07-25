package com.minisql.storage;

import com.minisql.common.Constants;

/**
 * B+tree node stored inside an 8KB page.
 *
 * Page layout:
 * ┌─ 24B: Page header (reuses Page header fields) ────┐
 * │  pageNumber (4B)                                  │
 * │  nodeType (2B): 0=INTERNAL, 1=LEAF               │
 * │  numKeys (2B)                                      │
 * │  freeOffset (2B): next write position              │
 * │  [INTERNAL only] unused (6B)                       │
 * │  [LEAF only] nextLeafPage (4B), prevLeafPage (4B) │
 * │  checksum (4B), unused (4B)                        │
 * ├────────────────────────────────────────────────────┤
 * │  Entry array (grows upward, sorted by key)         │
 * │  INTERNAL: [key:4B][childPtr:4B] repeated         │
 * │            + one extra childPtr at the end         │
 * │  LEAF:     [key:4B][pageNum:4B][slotNum:4B]       │
 * └────────────────────────────────────────────────────┘
 */
public class IndexPage {

    // ── Offsets within the page header ──────────────────────────
    private static final int OFF_PAGE_NUMBER  = 0;
    private static final int OFF_NODE_TYPE    = 4;
    private static final int OFF_NUM_KEYS     = 6;
    private static final int OFF_FREE_OFFSET  = 8;
    // LEAF-specific (bytes 10-17)
    private static final int OFF_NEXT_LEAF    = 10;
    private static final int OFF_PREV_LEAF    = 14;

    /** Where entry data begins. */
    static final int ENTRY_START = 24;

    /** Node type constants. */
    public static final short NODE_INTERNAL = 0;
    public static final short NODE_LEAF = 1;

    /** Entry sizes. */
    static final int INTERNAL_ENTRY_SIZE = 8;   // 4B key + 4B child pointer
    static final int LEAF_ENTRY_SIZE = 12;       // 4B key + 4B pageNum + 4B slotNum

    /** Maximum keys per node (calculated from available space). */
    static final int MAX_INTERNAL_KEYS = 500;    // leaves room for extra end-pointer
    static final int MAX_LEAF_KEYS = 500;

    /** Minimum fill after deletion (below this triggers borrow/merge). */
    static final int MIN_INTERNAL_KEYS = MAX_INTERNAL_KEYS / 2;
    static final int MIN_LEAF_KEYS = MAX_LEAF_KEYS / 2;

    // ── Fields ──────────────────────────────────────────────────
    private final byte[] data;
    private final PageId pageId;

    public IndexPage(PageId pageId) {
        this.pageId = pageId;
        this.data = new byte[Constants.PAGE_SIZE];
        writeInt(OFF_PAGE_NUMBER, pageId.pageNumber());
        writeShort(OFF_NODE_TYPE, NODE_LEAF); // default to leaf
        writeShort(OFF_NUM_KEYS, (short) 0);
        writeShort(OFF_FREE_OFFSET, (short) ENTRY_START);
        writeInt(OFF_NEXT_LEAF, -1);
        writeInt(OFF_PREV_LEAF, -1);
    }

    public IndexPage(PageId pageId, byte[] rawData) {
        if (rawData.length != Constants.PAGE_SIZE) {
            throw new IllegalArgumentException("Expected " + Constants.PAGE_SIZE + " bytes");
        }
        this.pageId = pageId;
        this.data = rawData;
    }

    // ── Accessors ────────────────────────────────────────────────

    public PageId getPageId() { return pageId; }
    public int getPageNumber() { return readInt(OFF_PAGE_NUMBER); }
    public short getNodeType() { return readShort(OFF_NODE_TYPE); }
    public int getNumKeys() { return readShort(OFF_NUM_KEYS); }
    public int getFreeOffset() { return readShort(OFF_FREE_OFFSET); }

    public boolean isLeaf() { return getNodeType() == NODE_LEAF; }
    public boolean isInternal() { return getNodeType() == NODE_INTERNAL; }

    public int getNextLeafPage() { return readInt(OFF_NEXT_LEAF); }
    public int getPrevLeafPage() { return readInt(OFF_PREV_LEAF); }

    public void setNextLeafPage(int pageNum) { writeInt(OFF_NEXT_LEAF, pageNum); }
    public void setPrevLeafPage(int pageNum) { writeInt(OFF_PREV_LEAF, pageNum); }
    public void setNodeType(short type) { writeShort(OFF_NODE_TYPE, type); }

    // ── Internal node: key/child access ──────────────────────────
    //
    // Layout: [child0:4B][key0:4B][child1:4B][key1:4B]...[child{N-1}:4B][key{N-1}:4B][childN:4B]
    // child[i] at ENTRY_START + i*8, key[i] at ENTRY_START + i*8 + 4
    // Last child[N] at ENTRY_START + N*8 (same formula works for all i)

    /** Get the key at a given index in an internal node. */
    public int getInternalKey(int index) {
        int offset = ENTRY_START + index * INTERNAL_ENTRY_SIZE + 4;
        return readInt(offset);
    }

    /** Get the child page pointer at a given index (0..numKeys). */
    public int getInternalChild(int index) {
        int offset = ENTRY_START + index * INTERNAL_ENTRY_SIZE;
        return readInt(offset);
    }

    /** Set an internal key at a given index. */
    public void setInternalKey(int index, int key) {
        int offset = ENTRY_START + index * INTERNAL_ENTRY_SIZE + 4;
        writeInt(offset, key);
    }

    /** Set an internal child pointer at a given index. */
    public void setInternalChild(int index, int childPage) {
        int offset = ENTRY_START + index * INTERNAL_ENTRY_SIZE;
        writeInt(offset, childPage);
    }

    /** Set the last child pointer (child N, after the last key). */
    public void setLastChild(int childPage) {
        int offset = ENTRY_START + getNumKeys() * INTERNAL_ENTRY_SIZE;
        writeInt(offset, childPage);
    }

    /** Find the child page index for a given search key (binary search). */
    public int findChildIndex(int key) {
        int n = getNumKeys();
        int lo = 0, hi = n - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int midKey = getInternalKey(mid);
            if (key < midKey) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return lo; // insertion point = child index
    }

    /** Insert a key and its right-child pointer into an internal node at sorted position. */
    public void insertInternalEntry(int insertPos, int key, int rightChild) {
        int n = getNumKeys();
        // Shift keys[p..n-1] and children[p+1..n] right by 8 bytes
        // Layout: [child0][key0][child1][key1]...[childN]
        // Start shifting from key[p] (child[p] stays as left child)
        int shiftStart = ENTRY_START + insertPos * INTERNAL_ENTRY_SIZE + 4;
        int shiftLen = (n - insertPos) * INTERNAL_ENTRY_SIZE + 4; // keys + children + final child
        System.arraycopy(data, shiftStart, data, shiftStart + INTERNAL_ENTRY_SIZE, shiftLen);

        // Write new (child, key) pair: child[p] already at the right spot
        // Write new key at key[p] position, new right child right after it
        writeInt(ENTRY_START + insertPos * INTERNAL_ENTRY_SIZE + 4, key);
        writeInt(ENTRY_START + insertPos * INTERNAL_ENTRY_SIZE + 8, rightChild);

        writeShort(OFF_NUM_KEYS, (short) (n + 1));
        writeShort(OFF_FREE_OFFSET, (short) (ENTRY_START + (n + 1) * INTERNAL_ENTRY_SIZE + 4));
    }

    /** Remove a key and its child pointer from an internal node. */
    public void removeInternalEntry(int index) {
        int n = getNumKeys();
        // Shift everything from key[index+1] left to overwrite key[index] and child[index+1]
        // Layout: [child0][key0]...[child{idx}][key{idx}][child{idx+1}]...
        // Remove: key[idx] and child[idx+1]. child[idx] stays.
        int shiftSrc = ENTRY_START + (index + 1) * INTERNAL_ENTRY_SIZE + 4; // key[index+1]
        int shiftDst = ENTRY_START + index * INTERNAL_ENTRY_SIZE + 4;       // key[index] (overwrite)
        int shiftLen = (n - index - 1) * INTERNAL_ENTRY_SIZE + 4;
        System.arraycopy(data, shiftSrc, data, shiftDst, shiftLen);

        writeShort(OFF_NUM_KEYS, (short) (n - 1));
        writeShort(OFF_FREE_OFFSET, (short) (ENTRY_START + (n - 1) * INTERNAL_ENTRY_SIZE + 4));
    }

    /** Split this internal node in half. Returns [promotedKey, newRightPage]. */
    public SplitResult splitInternal(PageId newPageId) {
        IndexPage right = new IndexPage(newPageId);
        right.setNodeType(NODE_INTERNAL);

        int n = getNumKeys();
        int mid = n / 2;
        int promotedKey = getInternalKey(mid);

        // Right side: keys[mid+1..n-1] and children[mid+1..n]
        // Keep child[mid] (which is right of key[mid]) — it stays with left node
        int rightKeyCount = n - mid - 1;
        // Copy children[keyCount] as child[0] of right node first
        right.setInternalChild(0, getInternalChild(mid + 1));
        for (int i = 0; i < rightKeyCount; i++) {
            right.setInternalKey(i, getInternalKey(mid + 1 + i));
            right.setInternalChild(i + 1, getInternalChild(mid + 2 + i));
        }
        right.writeShort(OFF_NUM_KEYS, (short) rightKeyCount);
        right.writeShort(OFF_FREE_OFFSET, (short) (ENTRY_START + rightKeyCount * INTERNAL_ENTRY_SIZE + 4));

        // Truncate this node to keys [0..mid-1] and children [0..mid]
        writeShort(OFF_NUM_KEYS, (short) mid);
        writeShort(OFF_FREE_OFFSET, (short) (ENTRY_START + mid * INTERNAL_ENTRY_SIZE + 4));

        return new SplitResult(promotedKey, right);
    }

    // ── Leaf node: key/value access ──────────────────────────────

    /** Get the key at a given index in a leaf node. */
    public int getLeafKey(int index) {
        int offset = ENTRY_START + index * LEAF_ENTRY_SIZE;
        return readInt(offset);
    }

    /** Get the RowId at a given index in a leaf node. */
    public RowId getLeafValue(int index) {
        int offset = ENTRY_START + index * LEAF_ENTRY_SIZE + 4;
        int pageNum = readInt(offset);
        int slotNum = readInt(offset + 4);
        return new RowId(pageNum, slotNum);
    }

    /** Set a key and value at a given index in a leaf node. */
    public void setLeafEntry(int index, int key, RowId value) {
        int offset = ENTRY_START + index * LEAF_ENTRY_SIZE;
        writeInt(offset, key);
        writeInt(offset + 4, value.pageNum());
        writeInt(offset + 8, value.slotNum());
    }

    /** Find the index of a key in a leaf node, or insertion point (binary search). */
    public int findLeafIndex(int key) {
        int n = getNumKeys();
        int lo = 0, hi = n - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int midKey = getLeafKey(mid);
            if (key < midKey) {
                hi = mid - 1;
            } else if (key > midKey) {
                lo = mid + 1;
            } else {
                return mid; // exact match
            }
        }
        return -(lo + 1); // insertion point, encoded as negative
    }

    /** Insert a key-value pair into a leaf node at sorted position. */
    public void insertLeafEntry(int insertPos, int key, RowId value) {
        int n = getNumKeys();
        // Shift entries right
        int shiftStart = ENTRY_START + insertPos * LEAF_ENTRY_SIZE;
        int shiftLen = (n - insertPos) * LEAF_ENTRY_SIZE;
        System.arraycopy(data, shiftStart, data, shiftStart + LEAF_ENTRY_SIZE, shiftLen);

        setLeafEntry(insertPos, key, value);
        writeShort(OFF_NUM_KEYS, (short) (n + 1));
        writeShort(OFF_FREE_OFFSET, (short) (ENTRY_START + (n + 1) * LEAF_ENTRY_SIZE));
    }

    /** Remove a key-value pair from a leaf node. */
    public void removeLeafEntry(int index) {
        int n = getNumKeys();
        int shiftStart = ENTRY_START + (index + 1) * LEAF_ENTRY_SIZE;
        int shiftDest = ENTRY_START + index * LEAF_ENTRY_SIZE;
        int shiftLen = (n - index - 1) * LEAF_ENTRY_SIZE;
        System.arraycopy(data, shiftStart, data, shiftDest, shiftLen);

        writeShort(OFF_NUM_KEYS, (short) (n - 1));
        writeShort(OFF_FREE_OFFSET, (short) (ENTRY_START + (n - 1) * LEAF_ENTRY_SIZE));
    }

    /** Split this leaf node in half. Returns [promotedKey, newRightPage]. */
    public SplitResult splitLeaf(PageId newPageId) {
        IndexPage right = new IndexPage(newPageId);
        right.setNodeType(NODE_LEAF);

        int n = getNumKeys();
        int mid = n / 2;
        int promotedKey = getLeafKey(mid);

        // Right side gets keys [mid .. n-1]
        int rightKeyCount = n - mid;
        for (int i = 0; i < rightKeyCount; i++) {
            right.setLeafEntry(i, getLeafKey(mid + i), getLeafValue(mid + i));
        }
        right.writeShort(OFF_NUM_KEYS, (short) rightKeyCount);
        right.writeShort(OFF_FREE_OFFSET, (short) (ENTRY_START + rightKeyCount * LEAF_ENTRY_SIZE));

        // Update linked list
        right.setNextLeafPage(this.getNextLeafPage());
        right.setPrevLeafPage(this.getPageNumber());
        this.setNextLeafPage(right.getPageNumber());

        // Truncate this node to keys [0 .. mid-1]
        writeShort(OFF_NUM_KEYS, (short) mid);
        writeShort(OFF_FREE_OFFSET, (short) (ENTRY_START + mid * LEAF_ENTRY_SIZE));

        return new SplitResult(promotedKey, right);
    }

    // ── Capacity ─────────────────────────────────────────────────

    public boolean isInternalFull() {
        return getNumKeys() >= MAX_INTERNAL_KEYS;
    }

    public boolean isLeafFull() {
        return getNumKeys() >= MAX_LEAF_KEYS;
    }

    public boolean isInternalUnderfull() {
        return getNumKeys() < MIN_INTERNAL_KEYS;
    }

    public boolean isLeafUnderfull() {
        return getNumKeys() < MIN_LEAF_KEYS;
    }

    // ── Binary I/O helpers ───────────────────────────────────────

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

    private short readShort(int offset) {
        return (short) (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
    }

    private void writeShort(int offset, short value) {
        data[offset]     = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    byte[] getRawData() {
        return data;
    }

    // ── Debug ────────────────────────────────────────────────────

    @Override
    public String toString() {
        String type = isLeaf() ? "LEAF" : "INTERNAL";
        StringBuilder sb = new StringBuilder();
        sb.append(type).append("[").append(pageId).append("] keys=").append(getNumKeys()).append(": ");
        int n = getNumKeys();
        if (isLeaf()) {
            for (int i = 0; i < Math.min(n, 5); i++) {
                sb.append(getLeafKey(i));
                if (i < n - 1) sb.append(", ");
            }
            if (n > 5) sb.append("...(").append(n).append(" total)");
            if (getNextLeafPage() >= 0) sb.append(" → page ").append(getNextLeafPage());
        } else {
            for (int i = 0; i < Math.min(n, 5); i++) {
                sb.append(getInternalKey(i));
                if (i < n - 1) sb.append(", ");
            }
            if (n > 5) sb.append("...(").append(n).append(" total)");
        }
        return sb.toString();
    }

    // ── Split result ─────────────────────────────────────────────

    /**
     * Result of splitting a node: the key promoted to the parent
     * and the new right sibling page.
     */
    public record SplitResult(int promotedKey, IndexPage rightPage) {}
}
