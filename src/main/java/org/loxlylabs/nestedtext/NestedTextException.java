package org.loxlylabs.nestedtext;

public class NestedTextException extends RuntimeException {

    private final int line;
    private final int column;

    public NestedTextException(String message, int line, int column) {
        super(String.format("Error at line %d, column %d: %s", line, column, message));
        this.line = line;
        this.column = column;
    }

    public NestedTextException(String message, Token token) {
        super(String.format("Error at line %d, column %d: %s", token.line, token.column, message));
        this.line = token.line;
        this.column = token.column;
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
