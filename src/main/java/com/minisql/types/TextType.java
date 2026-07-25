package com.minisql.types;

import java.nio.charset.StandardCharsets;

/**
 * Variable-length UTF-8 text. Stored as 2B length prefix + UTF-8 bytes on disk.
 * Maximum length: 65535 bytes.
 */
public class TextType implements DataType {

    public static final TextType INSTANCE = new TextType();

    private TextType() {}

    @Override
    public byte[] encode(Object value) {
        String s = (String) value;
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[2 + utf8.length];
        // 2-byte length prefix, big-endian
        result[0] = (byte) (utf8.length >>> 8);
        result[1] = (byte) utf8.length;
        System.arraycopy(utf8, 0, result, 2, utf8.length);
        return result;
    }

    @Override
    public Object decode(byte[] data, int offset, int length) {
        return new String(data, offset + 2, length - 2, StandardCharsets.UTF_8);
    }

    @Override
    public int compare(Object a, Object b) {
        return ((String) a).compareTo((String) b);
    }

    @Override
    public int getSize() {
        return -1; // variable length
    }

    @Override
    public String getSqlName() {
        return "TEXT";
    }

    @Override
    public Object parse(String literal) {
        // Expect single-quoted string: 'hello' → hello
        String s = literal.trim();
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            s = s.substring(1, s.length() - 1);
            // Handle escaped single quotes: '' → '
            s = s.replace("''", "'");
        }
        return s;
    }
}
