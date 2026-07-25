package com.minisql.types;

/**
 * Interface for all SQL data types. Each type knows how to encode/decode bytes,
 * compare values, parse literals, and report its SQL name and size.
 */
public interface DataType {

    /** Encode a Java value to on-disk bytes. */
    byte[] encode(Object value);

    /**
     * Decode bytes back to a Java value.
     *
     * @param data   the byte array
     * @param offset where this column's data starts
     * @param length how many bytes belong to this column
     */
    Object decode(byte[] data, int offset, int length);

    /** Compare two values of this type. Negative if a < b, zero if equal, positive if a > b. */
    int compare(Object a, Object b);

    /** Fixed size in bytes, or -1 for variable-length types (TEXT). */
    int getSize();

    /** The SQL name: "INTEGER", "TEXT", "FLOAT", "BOOLEAN". */
    String getSqlName();

    /** Whether this type has a fixed size (true for INT/FLOAT/BOOL, false for TEXT). */
    default boolean isFixedSize() {
        return getSize() >= 0;
    }

    /**
     * Parse a SQL literal string into a Java value.
     * For example: "42" → 42, "'hello'" → "hello", "TRUE" → true.
     */
    Object parse(String literal);

    /**
     * Look up a DataType by its SQL name (case-insensitive).
     * Accepts common aliases: INT/INTEGER, TEXT/VARCHAR, BOOL/BOOLEAN.
     */
    static DataType fromSqlName(String name) {
        return switch (name.toUpperCase()) {
            case "INT", "INTEGER" -> IntegerType.INSTANCE;
            case "FLOAT", "DOUBLE", "REAL" -> FloatType.INSTANCE;
            case "TEXT", "VARCHAR", "STRING" -> TextType.INSTANCE;
            case "BOOLEAN", "BOOL" -> BooleanType.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown data type: " + name);
        };
    }
}
