package com.minisql.types;

/**
 * Boolean type. Stored as 1 byte on disk: 0x01 = true, 0x00 = false.
 */
public class BooleanType implements DataType {

    public static final BooleanType INSTANCE = new BooleanType();

    private BooleanType() {}

    @Override
    public byte[] encode(Object value) {
        return new byte[] { (boolean) value ? (byte) 1 : (byte) 0 };
    }

    @Override
    public Object decode(byte[] data, int offset, int length) {
        return data[offset] != 0;
    }

    @Override
    public int compare(Object a, Object b) {
        return Boolean.compare((boolean) a, (boolean) b);
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public String getSqlName() {
        return "BOOLEAN";
    }

    @Override
    public Object parse(String literal) {
        String s = literal.trim().toUpperCase();
        return "TRUE".equals(s) || "1".equals(s);
    }
}
