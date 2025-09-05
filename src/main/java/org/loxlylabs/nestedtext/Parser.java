package org.loxlylabs.nestedtext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Object parse() {
        // A NestedText document can be a dictionary, a list, a multiline string, or empty.
        if (isAtEnd() || peek().type == TokenType.EOF) return null;
        if (check(TokenType.INDENT)) {
            throw error(peek(), "top-level content must start in column 1.");
        }
        return parseObject();
    }

    private Object parseObject() {
        TokenType nextTok = peek().type;
        if (nextTok == TokenType.DASH) {
            return parseList();
        }
        if (nextTok == TokenType.GREATER) {
            return parseMultilineString();
        }
        if (nextTok == TokenType.KEY) {
            return parseDictionary();
        }

        throw error(peek(), "Unexpected token; expected a list ('-'), dictionary ('key:'), or multiline string ('>').");
    }

    private List<Object> parseList() {
        List<Object> list = new ArrayList<>();

        while (match(TokenType.DASH)) {
            Object value;
            // Value for this list item is a new object
            if (check(TokenType.NEWLINE) && checkNext(TokenType.INDENT)) {
                advance();
                advance();
                value = parseObject();
                consume(TokenType.DEDENT, "invalid indentation.");
            }
            // Simple string value
            else if (match(TokenType.STRING)) {
                value = previous().literal;
                match(TokenType.NEWLINE);
            } else {
                value = ""; // Empty list item
                match(TokenType.NEWLINE);
            }
            list.add(value);
        }
        return list;
    }

    private Map<String,Object> parseDictionary() {
        Map<String,Object> dictionary = new LinkedHashMap<>();

        while (match(TokenType.KEY)) {
            String key = (String)previous().literal;
            Object value;
            // Value for this list item is a new object
            if (check(TokenType.NEWLINE) && checkNext(TokenType.INDENT)) {
                advance();
                advance();
                value = parseObject();
                consume(TokenType.DEDENT, "invalid indentation.");
            }
            // Simple string value
            else if (match(TokenType.STRING)) {
                value = previous().literal;
                match(TokenType.NEWLINE);
            } else {
                value = ""; // Empty value for key
                match(TokenType.NEWLINE);
            }
            dictionary.put(key, value);
        }
        return dictionary;
    }

    private String parseMultilineString() {
        StringBuilder sb = new StringBuilder();

        boolean start = true;
        while (!isAtEnd() && peek().type == TokenType.GREATER) {
            if (!start) {
                sb.append("\n");
            }
            start = false;
            consume(TokenType.GREATER, "Expect '>' for multiline string line.");
            if (check(TokenType.STRING)) {
                sb.append(peek().literal);
                advance();
            }
            if (!match(TokenType.NEWLINE)) {
                break;
            }
        }
        return sb.toString();
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private boolean checkNext(TokenType type) {
        if (isAtEnd() || current + 1 >= tokens.size()) return false;
        return peekNext().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekNext() {
        if (current + 1 >= tokens.size()) return tokens.get(tokens.size() - 1); // EOF
        return tokens.get(current + 1);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private NestedTextException error(Token token, String message) {
        return new NestedTextException(message, token);
    }
}
