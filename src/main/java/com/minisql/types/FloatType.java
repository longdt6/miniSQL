package com.minisql.types;

/**
 * 64-bit IEEE 754 double-precision floating point. Stored as 8 bytes on disk.
 */
public class FloatType implements DataType {

    public static final FloatType INSTANCE = new FloatType();

    private FloatType() {}

    @Override
    public byte[] encode(Object value) {
        long bits = Double.doubleToLongBits((double) value);
        return new byte[] {
            (byte) (bits >>> 56),
            (byte) (bits >>> 48),
            (byte) (bits >>> 40),
            (byte) (bits >>> 32),
            (byte) (bits >>> 24),
            (byte) (bits >>> 16),
            (byte) (bits >>> 8),
            (byte) bits
        };
    }

    @Override
    public Object decode(byte[] data, int offset, int length) {
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits = (bits << 8) | (data[offset + i] & 0xFF);
        }
        return Double.longBitsToDouble(bits);
    }

    @Override
    public int compare(Object a, Object b) {
        return Double.compare((double) a, (double) b);
    }

    @Override
    public int getSize() {
        return 8;
    }

    @Override
    public String getSqlName() {
        return "FLOAT";
    }

    @Override
    public Object parse(String literal) {
        return Double.parseDouble(literal.trim());
    }
}
