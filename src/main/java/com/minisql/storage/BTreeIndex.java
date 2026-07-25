package com.minisql.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A B+tree index mapping integer keys to RowId values.
 *
 * Supports: search, insert (with split-cascade), delete (with borrow/merge), range scan.
 * Uses IndexPage nodes stored in an IndexFile.
 * Root always stays at page 0; when root splits, both children get new pages and page 0
 * becomes the new root.
 */
public class BTreeIndex {

    private final IndexFile indexFile;
    private final BufferPool pool;

    public BTreeIndex(Path filePath, BufferPool pool) throws IOException {
        this.indexFile = new IndexFile(filePath);
        this.pool = pool;

        if (indexFile.getNumPages() == 0) {
            IndexPage root = indexFile.appendPage(); // page 0
            root.setNodeType(IndexPage.NODE_LEAF);
            indexFile.writePage(root);
        }
    }

    public Path getFilePath() {
        return indexFile.getFilePath();
    }

    // ═══════════════════════════════════════════════════════════════
    // Search
    // ═══════════════════════════════════════════════════════════════

    public RowId search(int key) throws IOException {
        IndexPage leaf = findLeaf(key);
        if (leaf == null) return null;
        int idx = leaf.findLeafIndex(key);
        return (idx >= 0) ? leaf.getLeafValue(idx) : null;
    }

    private IndexPage findLeaf(int key) throws IOException {
        IndexPage node = readPage(0); // root at page 0
        if (node == null) return null;
        while (node.isInternal()) {
            int childIdx = node.findChildIndex(key);
            int childPageNum = node.getInternalChild(childIdx);
            node = readPage(childPageNum);
        }
        return node;
    }

    // ═══════════════════════════════════════════════════════════════
    // Insert
    // ═══════════════════════════════════════════════════════════════

    public void insert(int key, RowId value) throws IOException {
        IndexPage root = readPage(0);
        InsertResult result = insertInto(root, key, value);

        if (result != null) {
            // Root split — both children move to new pages, page 0 becomes new root
            int leftChildPage = indexFile.appendPage().getPageNumber();
            int rightChildPage = result.newChildPageNum;  // right half already at this page

            // Copy the left half (current root content) to a new page
            IndexPage leftChild = new IndexPage(new PageId(getFilePath().toString(), leftChildPage));
            System.arraycopy(root.getRawData(), 0, leftChild.getRawData(), 0, 8192);
            // Fix the page number in the copied header to match the actual page position
            fixPageNumber(leftChild, leftChildPage);
            indexFile.writePage(leftChild);

            // Create a new root at page 0
            IndexPage newRoot = new IndexPage(new PageId(getFilePath().toString(), 0));
            newRoot.setNodeType(IndexPage.NODE_INTERNAL);
            newRoot.insertInternalEntry(0, result.promotedKey, rightChildPage);
            newRoot.setInternalChild(0, leftChildPage);
            indexFile.writePage(newRoot);
        }
    }

    private static void fixPageNumber(IndexPage page, int pageNum) {
        byte[] raw = page.getRawData();
        raw[0] = (byte) (pageNum >>> 24);
        raw[1] = (byte) (pageNum >>> 16);
        raw[2] = (byte) (pageNum >>> 8);
        raw[3] = (byte) pageNum;
    }

    /** Returns non-null if this node split, containing pushed-up key + new child page number. */
    private InsertResult insertInto(IndexPage node, int key, RowId value) throws IOException {
        if (node.isLeaf()) {
            int idx = node.findLeafIndex(key);
            if (idx >= 0) {
                node.setLeafEntry(idx, key, value);
                indexFile.writePage(node);
                return null;
            }
            int insertPos = -(idx + 1);
            node.insertLeafEntry(insertPos, key, value);

            if (node.isLeafFull()) {
                IndexPage rightPage = indexFile.appendPage();
                IndexPage.SplitResult split = node.splitLeaf(rightPage.getPageId());
                indexFile.writePage(split.rightPage());
                indexFile.writePage(node);
                return new InsertResult(split.promotedKey(), rightPage.getPageNumber());
            }
            indexFile.writePage(node);
            return null;
        }

        // Internal node
        int childIdx = node.findChildIndex(key);
        int childPageNum = node.getInternalChild(childIdx);
        IndexPage child = readPage(childPageNum);
        InsertResult result = insertInto(child, key, value);

        if (result == null) {
            return null;
        }

        // Child split — insert promoted key at the correct position
        // The insertion point in THIS node depends on the promoted key
        int insertPos = childIdx;
        node.insertInternalEntry(insertPos, result.promotedKey, result.newChildPageNum);
        // The old child at insertPos stays as the left child (it was shifted right by insertInternalEntry)
        // We need child[insertPos] to remain the original childPageNum (which should be unchanged since
        // insertInternalEntry shifts starting from insertPos)
        // Actually, insertInternalEntry shifts keys AND child pointers from insertPos.
        // The new entry at insertPos has its right child = result.newChildPageNum (the right half).
        // The left child of the new entry should be the original childPageNum.
        // But insertInternalEntry places child at insertPos+1 as the right child of the new key.
        // We need to set child[insertPos] = childPageNum.
        node.setInternalChild(insertPos, childPageNum);

        if (node.isInternalFull()) {
            IndexPage rightPage = indexFile.appendPage();
            IndexPage.SplitResult split = node.splitInternal(rightPage.getPageId());
            indexFile.writePage(split.rightPage());
            indexFile.writePage(node);
            return new InsertResult(split.promotedKey(), rightPage.getPageNumber());
        }
        indexFile.writePage(node);
        return null;
    }

    private record InsertResult(int promotedKey, int newChildPageNum) {}

    // ═══════════════════════════════════════════════════════════════
    // Delete
    // ═══════════════════════════════════════════════════════════════

    public void delete(int key) throws IOException {
        IndexPage root = readPage(0);
        if (root == null) return;
        deleteFrom(root, key);

        // If root became internal with 0 keys, promote its only child to be the new root
        root = readPage(0);
        if (root.isInternal() && root.getNumKeys() == 0) {
            int childPage = root.getInternalChild(0);
            IndexPage child = readPage(childPage);
            IndexPage newRoot = new IndexPage(root.getPageId()); // page 0
            System.arraycopy(child.getRawData(), 0, newRoot.getRawData(), 0, 8192);
            fixPageNumber(newRoot, 0);
            indexFile.writePage(newRoot);
        }
    }

    private boolean deleteFrom(IndexPage node, int key) throws IOException {
        if (node.isLeaf()) {
            int idx = node.findLeafIndex(key);
            if (idx < 0) { indexFile.writePage(node); return false; }
            node.removeLeafEntry(idx);
            indexFile.writePage(node);
            return node.isLeafUnderfull();
        }

        int childIdx = node.findChildIndex(key);
        int childPageNum = node.getInternalChild(childIdx);
        IndexPage child = readPage(childPageNum);
        boolean needsRebalance = deleteFrom(child, key);

        if (!needsRebalance) { return false; }
        return rebalanceChild(node, childIdx);
    }

    private boolean rebalanceChild(IndexPage parent, int childIdx) throws IOException {
        IndexPage child = readPage(parent.getInternalChild(childIdx));

        // Try borrow from right sibling
        if (childIdx < parent.getNumKeys()) {
            IndexPage right = readPage(parent.getInternalChild(childIdx + 1));
            if (!right.isLeafUnderfull() && !right.isInternalUnderfull()) {
                borrowFromRight(parent, childIdx, child, right);
                indexFile.writePage(parent);
                indexFile.writePage(child);
                indexFile.writePage(right);
                return false;
            }
        }

        // Try borrow from left sibling
        if (childIdx > 0) {
            IndexPage left = readPage(parent.getInternalChild(childIdx - 1));
            if (!left.isLeafUnderfull() && !left.isInternalUnderfull()) {
                borrowFromLeft(parent, childIdx, child, left);
                indexFile.writePage(parent);
                indexFile.writePage(child);
                indexFile.writePage(left);
                return false;
            }
        }

        // Merge with a sibling
        if (childIdx < parent.getNumKeys()) {
            IndexPage right = readPage(parent.getInternalChild(childIdx + 1));
            mergeIntoLeft(child, right, parent, childIdx);
        } else {
            IndexPage left = readPage(parent.getInternalChild(childIdx - 1));
            mergeIntoLeft(left, child, parent, childIdx - 1);
        }

        indexFile.writePage(parent);
        return parent.isInternalUnderfull();
    }

    private void borrowFromRight(IndexPage parent, int childIdx, IndexPage child, IndexPage right) {
        if (child.isLeaf()) {
            // Leaf borrow: move first entry from right to end of child
            child.insertLeafEntry(child.getNumKeys(), right.getLeafKey(0), right.getLeafValue(0));
            right.removeLeafEntry(0);
            // Update parent separator to new first key of right
            parent.setInternalKey(childIdx, right.getLeafKey(0));
        } else {
            // Internal borrow: parent separator moves down to child, right's first key moves up to parent
            int sepKey = parent.getInternalKey(childIdx);
            int rightFirstChild = right.getInternalChild(0);
            child.insertInternalEntry(child.getNumKeys(), sepKey, rightFirstChild);
            // The last child pointer should point to rightFirstChild. insertInternalEntry set
            // child at entryCount+1 to rightFirstChild (which is correct — right child of last key).
            int newSep = right.getInternalKey(0);
            right.removeInternalEntry(0);
            parent.setInternalKey(childIdx, newSep);
        }
    }

    private void borrowFromLeft(IndexPage parent, int childIdx, IndexPage child, IndexPage left) {
        if (child.isLeaf()) {
            // Leaf borrow: move last entry from left to start of child
            int last = left.getNumKeys() - 1;
            child.insertLeafEntry(0, left.getLeafKey(last), left.getLeafValue(last));
            left.removeLeafEntry(last);
            // Update parent separator to new last key of left (which is the promoted key)
            parent.setInternalKey(childIdx - 1, child.getLeafKey(0));
        } else {
            // Internal borrow: parent separator moves down to child as first key,
            // left's last key moves up to parent
            int sepIdx = childIdx - 1;
            int sepKey = parent.getInternalKey(sepIdx);
            int leftLastChild = left.getInternalChild(left.getNumKeys());

            // Insert separator at position 0 in child. The right child of this new entry
            // should be child's old first child.
            int childOldFirstChild = child.getInternalChild(0);
            child.insertInternalEntry(0, sepKey, childOldFirstChild);
            // Set child[0] = leftLastChild (left child of the new first key)
            child.setInternalChild(0, leftLastChild);

            // Promote left's last key to parent separator
            int leftLastKey = left.getInternalKey(left.getNumKeys() - 1);
            left.removeInternalEntry(left.getNumKeys() - 1);
            parent.setInternalKey(sepIdx, leftLastKey);
        }
    }

    /** Merge right node into left node. Remove separator at childIdx from parent. */
    private void mergeIntoLeft(IndexPage left, IndexPage right, IndexPage parent, int childIdx) throws IOException {
        int sepKey = parent.getInternalKey(childIdx);

        if (left.isLeaf()) {
            for (int i = 0; i < right.getNumKeys(); i++) {
                left.insertLeafEntry(left.getNumKeys(), right.getLeafKey(i), right.getLeafValue(i));
            }
            left.setNextLeafPage(right.getNextLeafPage());
            if (right.getNextLeafPage() >= 0) {
                IndexPage next = readPage(right.getNextLeafPage());
                next.setPrevLeafPage(left.getPageNumber());
                indexFile.writePage(next);
            }
        } else {
            // Pull parent separator down
            left.insertInternalEntry(left.getNumKeys(), sepKey, right.getInternalChild(0));
            for (int i = 0; i < right.getNumKeys(); i++) {
                left.insertInternalEntry(left.getNumKeys(),
                    right.getInternalKey(i), right.getInternalChild(i + 1));
            }
        }

        parent.removeInternalEntry(childIdx);
        indexFile.writePage(left);
    }

    // ═══════════════════════════════════════════════════════════════
    // Range Scan
    // ═══════════════════════════════════════════════════════════════

    public List<RowId> searchRange(int startKey, int endKey) throws IOException {
        List<RowId> results = new ArrayList<>();
        IndexPage leaf = findLeaf(startKey);
        if (leaf == null) return results;

        while (leaf != null) {
            for (int i = 0; i < leaf.getNumKeys(); i++) {
                int key = leaf.getLeafKey(i);
                if (key > endKey) return results;
                if (key >= startKey) results.add(leaf.getLeafValue(i));
            }
            leaf = (leaf.getNextLeafPage() >= 0) ? readPage(leaf.getNextLeafPage()) : null;
        }
        return results;
    }

    public List<RowId> searchFrom(int startKey) throws IOException {
        List<RowId> results = new ArrayList<>();
        IndexPage leaf = findLeaf(startKey);
        if (leaf == null) return results;

        while (leaf != null) {
            for (int i = 0; i < leaf.getNumKeys(); i++) {
                if (leaf.getLeafKey(i) >= startKey) {
                    results.add(leaf.getLeafValue(i));
                }
            }
            leaf = (leaf.getNextLeafPage() >= 0) ? readPage(leaf.getNextLeafPage()) : null;
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════════
    // I/O
    // ═══════════════════════════════════════════════════════════════

    private IndexPage readPage(int pageNum) throws IOException {
        return indexFile.readPage(pageNum);
    }

    // ═══════════════════════════════════════════════════════════════
    // Debug
    // ═══════════════════════════════════════════════════════════════

    public void printTree() throws IOException {
        IndexPage root = readPage(0);
        if (root == null) { System.out.println("(empty tree)"); return; }
        printNode(root, 0);
    }

    private void printNode(IndexPage node, int indent) throws IOException {
        String prefix = "  ".repeat(indent);
        System.out.println(prefix + node);
        if (node.isInternal()) {
            for (int i = 0; i <= node.getNumKeys(); i++) {
                printNode(readPage(node.getInternalChild(i)), indent + 1);
            }
        }
    }

    public int getNumPages() throws IOException {
        return indexFile.getNumPages();
    }

    @Override
    public String toString() {
        return "BTreeIndex{" + indexFile + "}";
    }
}
