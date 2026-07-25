package com.minisql.common;

/**
 * Shared constants used across the system.
 */
public final class Constants {

    private Constants() {}

    /** Size of each disk page in bytes. */
    public static final int PAGE_SIZE = 8192;

    /** Size of the page header in bytes. */
    public static final int PAGE_HEADER_SIZE = 24;

    /** Size of each slot directory entry in bytes: 2B offset + 2B flags + 2B length. */
    public static final int SLOT_SIZE = 6;

    /** Flag bit 0: set when a tuple slot is deleted (soft delete). */
    public static final short SLOT_FLAG_DELETED = 0x0001;

    /** Default database name. */
    public static final String DEFAULT_DB_NAME = "mydb";

    /** Default HTTP server port. */
    public static final int DEFAULT_PORT = 8080;
}
