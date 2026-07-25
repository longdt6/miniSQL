package com.minisql.common;

/**
 * Checked exception for all SQL-level errors: syntax errors, type mismatches,
 * missing tables/columns, constraint violations, etc.
 */
public class SqlException extends Exception {

    public SqlException(String message) {
        super(message);
    }

    public SqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
