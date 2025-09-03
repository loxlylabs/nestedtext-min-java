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
        super(String.format("Error at line %d: %s (near token: %s)", token.line, message, token));
        this.line = token.line;
        this.column = 0;
    }


    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
