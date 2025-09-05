package org.loxlylabs.nestedtext;

/**
 * The unified exception for all errors that occur within the NestedText library.
 */
public class NestedTextException extends RuntimeException {

    /** The 1-based line number where a parsing error occurred. */
    private final int line;
    /** The 1-based column number where a parsing error occurred. */
    private final int column;

    NestedTextException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    NestedTextException(String message, Token token) {
        super(message);
        this.line = token.line;
        this.column = token.column;
    }

    NestedTextException(String message, Throwable cause) {
        super(message, cause);
        this.line = -1;
        this.column = -1;
    }

    /**
     * Gets the line number where the parsing error occurred.
     *
     * @return The 1-based line number, or -1 if the error is not related to parsing
     * (e.g., a serialization error).
     */
    public int getLine() {
        return line;
    }

    /**
     * Gets the column number where the parsing error occurred.
     *
     * @return The 1-based column number, or 0 if the column was not precisely tracked,
     * or -1 if the error is not related to parsing.
     */
    public int getColumn() {
        return column;
    }
}
