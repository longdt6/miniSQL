package com.minisql.types;

/**
 * 32-bit signed integer type. Stored as 4 bytes big-endian on disk.
 */
public class IntegerType implements DataType {

    public static final IntegerType INSTANCE = new IntegerType();

    private IntegerType() {}

    @Override
    public byte[] encode(Object value) {
        int v = (int) value;
        return new byte[] {
            (byte) (v >>> 24),
            (byte) (v >>> 16),
            (byte) (v >>> 8),
            (byte) v
        };
    }

    @Override
    public Object decode(byte[] data, int offset, int length) {
        return ((data[offset] & 0xFF) << 24)
             | ((data[offset + 1] & 0xFF) << 16)
             | ((data[offset + 2] & 0xFF) << 8)
             |  (data[offset + 3] & 0xFF);
    }

    @Override
    public int compare(Object a, Object b) {
        return Integer.compare((int) a, (int) b);
    }

    @Override
    public int getSize() {
        return 4;
    }

    @Override
    public String getSqlName() {
        return "INTEGER";
    }

    @Override
    public Object parse(String literal) {
        return Integer.parseInt(literal.trim());
    }
}
