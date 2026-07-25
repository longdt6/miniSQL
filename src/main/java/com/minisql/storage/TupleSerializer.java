package com.minisql.storage;

import com.minisql.common.Constants;
import com.minisql.types.*;

import java.util.Arrays;

/**
 * Converts between in-memory Row objects and on-disk binary tuple format.
 *
 * Tuple layout:
 * ┌─ 4B header ──────────────────────┐
 * │  byte 0: flags (bit 0 = deleted) │
 * │  byte 1: columnCount             │
 * │  byte 2-3: total tuple length    │
 * ├─ ceil(N/8)B: null bitmap ────────┤
 * ├─ 2B per col: column offsets ─────┤
 * ├─ variable: column data ──────────┤
 * └──────────────────────────────────┘
 */
public class TupleSerializer {

    private static final int HEADER_SIZE = 4;

    /** Soft-deleted tuple flag. */
    public static final byte FLAG_DELETED = 0x01;

    private TupleSerializer() {}

    // ── Serialize ──────────────────────────────────────────────

    /**
     * Convert a Row to on-disk bytes using the schema in desc.
     */
    public static byte[] serialize(Row row, TupleDesc desc) {
        int colCount = desc.getColumnCount();
        int nullBitmapSize = (colCount + 7) / 8;

        // Step 1: encode each column into separate byte arrays
        byte[][] colData = new byte[colCount][];
        boolean[] isNull = new boolean[colCount];
        int dataSize = 0;

        for (int i = 0; i < colCount; i++) {
            String colName = desc.getColumnName(i);
            Object value = row.get(colName);

            if (value == null) {
                isNull[i] = true;
                colData[i] = new byte[0];
            } else {
                colData[i] = desc.getColumnType(i).encode(value);
                dataSize += colData[i].length;
            }
        }

        // Step 2: calculate sizes
        int offsetTableSize = colCount * 2;
        int totalSize = HEADER_SIZE + nullBitmapSize + offsetTableSize + dataSize;

        byte[] result = new byte[totalSize];

        // Step 3: write header
        result[0] = 0x00;                     // flags (alive)
        result[1] = (byte) colCount;          // column count
        result[2] = (byte) (totalSize >>> 8); // total length high
        result[3] = (byte) totalSize;         // total length low

        // Step 4: write null bitmap
        for (int i = 0; i < colCount; i++) {
            if (isNull[i]) {
                int byteIdx = i / 8;
                int bitIdx = i % 8;
                result[HEADER_SIZE + byteIdx] |= (byte) (1 << bitIdx);
            }
        }

        // Step 5: compute and write column offsets
        int dataStart = HEADER_SIZE + nullBitmapSize + offsetTableSize;
        int currentOffset = dataStart;

        for (int i = 0; i < colCount; i++) {
            int offsetPos = HEADER_SIZE + nullBitmapSize + i * 2;
            result[offsetPos]     = (byte) (currentOffset >>> 8);
            result[offsetPos + 1] = (byte) currentOffset;
            currentOffset += colData[i].length;
        }

        // Step 6: write column data
        int dest = dataStart;
        for (int i = 0; i < colCount; i++) {
            if (!isNull[i]) {
                System.arraycopy(colData[i], 0, result, dest, colData[i].length);
                dest += colData[i].length;
            }
        }

        return result;
    }

    // ── Deserialize ────────────────────────────────────────────

    /**
     * Convert on-disk bytes back to a Row.
     */
    public static Row deserialize(byte[] data, TupleDesc desc) {
        int colCount = Byte.toUnsignedInt(data[1]);
        int nullBitmapSize = (colCount + 7) / 8;
        int offsetTableSize = colCount * 2;
        int offsetBase = HEADER_SIZE + nullBitmapSize;

        Row row = new Row();

        for (int i = 0; i < colCount; i++) {
            String colName = desc.getColumnName(i);
            DataType type = desc.getColumnType(i);

            // Check null bitmap
            int byteIdx = i / 8;
            int bitIdx = i % 8;
            boolean isNull = (data[HEADER_SIZE + byteIdx] & (1 << bitIdx)) != 0;

            if (isNull) {
                row.set(colName, null);
            } else {
                int offsetPos = offsetBase + i * 2;
                int offset = ((data[offsetPos] & 0xFF) << 8) | (data[offsetPos + 1] & 0xFF);

                // Determine length: either from next offset, or total tuple size
                int length;
                if (type.isFixedSize()) {
                    length = type.getSize();
                } else if (i < colCount - 1) {
                    int nextOffsetPos = offsetBase + (i + 1) * 2;
                    int nextOffset = ((data[nextOffsetPos] & 0xFF) << 8) | (data[nextOffsetPos + 1] & 0xFF);
                    length = nextOffset - offset;
                } else {
                    // Last column: extends to end of tuple
                    int totalLen = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    length = totalLen - offset;
                }

                Object value = type.decode(data, offset, length);
                row.set(colName, value);
            }
        }

        return row;
    }

    // ── Helpers ────────────────────────────────────────────────

    /** Check if a tuple is soft-deleted. */
    public static boolean isDeleted(byte[] tuple) {
        return tuple.length > 0 && (tuple[0] & FLAG_DELETED) != 0;
    }

    /** Mark a tuple as deleted in-place. */
    public static void markDeleted(byte[] tuple) {
        if (tuple.length > 0) {
            tuple[0] |= FLAG_DELETED;
        }
    }

    /** Compute the size a tuple would occupy for a given row and schema. */
    public static int computeSize(Row row, TupleDesc desc) {
        int colCount = desc.getColumnCount();
        int nullBitmapSize = (colCount + 7) / 8;
        int offsetTableSize = colCount * 2;
        int fixed = HEADER_SIZE + nullBitmapSize + offsetTableSize;

        int dataSize = 0;
        for (int i = 0; i < colCount; i++) {
            Object value = row.get(desc.getColumnName(i));
            if (value != null) {
                dataSize += desc.getColumnType(i).encode(value).length;
            }
        }
        return fixed + dataSize;
    }
}
