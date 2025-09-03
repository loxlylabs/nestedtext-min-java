package org.loxlylabs.nestedtext;

import java.util.Objects;

class Token {
    public final TokenType type;
    public final Object literal;
    public final int line;

    Token(TokenType type, Object literal, int line) {
        this.type = type;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return String.format("%-12s %s", type, literal);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return line == token.line &&
               type == token.type &&
               Objects.equals(literal, token.literal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, literal, line);
    }
}
