package org.loxlylabs.nestedtext;

public class NestedTextException extends RuntimeException {

    private final int line;
    private final int column;

    public NestedTextException(String message, int line, int column) {
        super(getErrorMessage(message, line, column));
        this.line = line;
        this.column = column;
    }

    public NestedTextException(String message, Token token) {
        super(getErrorMessage(message, token.line, token.column));
        this.line = token.line;
        this.column = token.column;
    }

    private static String getErrorMessage(String message, int line, int column) {
        if (column == 0) {
            return String.format("Error at line %d: %s", line, message);
        } else {
            return String.format("Error at line %d, column %d: %s", line, column, message);
        }
    }

    public NestedTextException(String message, Throwable cause) {
        super(message, cause);
        this.line = -1;
        this.column = -1;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
