package org.loxlylabs.nestedtext;

import java.io.BufferedReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

class Scanner {
    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private int current = 0;
    private int lineNumber = 1;
    private String curLine;
    private final Stream<String> lines;

    public Scanner(String source) {
        this(source.lines());
    }

    public Scanner(BufferedReader reader) {
        this(reader.lines());
    }

    public Scanner(Stream<String> lines) {
        this.lines = lines;
        this.indentStack.push(0);
    }

    public List<Token> scanTokens() {
        List<Token> tokens = new ArrayList<>();
        lines.forEach(line -> {
            current = 0;
            tokens.addAll(processLine(line));
            lineNumber++;
        });

        if (!tokens.isEmpty()) {
            if (tokens.getLast().type == TokenType.NEWLINE) {
                tokens.removeLast();
            }
        }
        while (indentStack.size() > 1) {
            tokens.add(createToken(TokenType.DEDENT));
            indentStack.pop();
        }
        tokens.add(createToken(TokenType.EOF));
        return tokens;
    }

    private List<Token> processLine(String line) {
        curLine = line;

        // Start with indent/dedent tokens which may be empty if no-indent.
        // If null, it means this line is blank or a comment and no further processing needed.
        List<Token> tokens = handleIndentation();
        if (tokens == null) {
            return List.of();
        }

        // Process line type, may be list, multi-line, or dict
        char c = advance();

        List<Token> newTokens = switch(c) {
            case '-' -> processListLine();
            case '>' -> processMultilineStringLine();
            default -> processDictionaryLine();
        };
        tokens.addAll(newTokens);

        tokens.add(createToken(TokenType.NEWLINE));

        return tokens;
    }

    private List<Token> processListLine() {
        List<Token> tokens = new ArrayList<>();
        if (peek() == ' ') {
            advance();
        }
        tokens.add(createToken(TokenType.DASH));
        if (!isEOL()) {
            tokens.add(processString());
        }
        return tokens;
    }

    private List<Token> processMultilineStringLine() {
        List<Token> tokens = new ArrayList<>();
        if (peek() == ' ') {
            advance();
        }
        tokens.add(createToken(TokenType.GREATER));
        if (!isEOL()) {
            tokens.add(processString());
        }
        return tokens;
    }

    private List<Token> processDictionaryLine() {
        List<Token> tokens = new ArrayList<>();

        current--; // Backtrack to include the character that started the string

        tokens.add(processKey());

        if (!isEOL()) {
            tokens.add(processString());
        }
        return tokens;
    }

    private Token processKey() {
        int keyStart = current;

        // Get key
        while (!isEOL()) {
            // A dictionary separator ": " terminates a key string.
            if (peek() == ':') {
                advance();

                // if not immediately followed by space or newline,
                // the colon is allowed in the key
                if (peek() == ' ' || isEOL()) {
                    String value = curLine.substring(keyStart, current - 1);
                    // consume space
                    if (peek() == ' ') {
                        advance();
                    }
                    // whitespace before the colon is trimmed
                    return createToken(TokenType.KEY, value.stripTrailing());
                }
            } else {
                advance();
            }
        }
        throw new NestedTextException("Unrecognized line structure, couldn't find a key.", lineNumber, keyStart);
    }

    private Token processString() {
        String value = curLine.substring(current);
        return createToken(TokenType.STRING, value);
    }

    private List<Token> handleIndentation() {
        List<Token> tokens = new ArrayList<>();

        int indent = 0;
        while (peek() == ' ') {
            indent++;
            advance();
        }

        if (peek() == '\t') {
            throw new NestedTextException("Tabs are not allowed for indentation; use spaces instead.", lineNumber, current);
        }

        if (peek() == '#' || isEOL()) {
            skipLine();
            return null;
        }

        Integer lastIndent = indentStack.peek();

        if (indent > lastIndent) {
            indentStack.push(indent);
            tokens.add(createToken(TokenType.INDENT));
        } else if (indent < lastIndent) {
            if (!indentStack.contains(indent)) {
                throw new NestedTextException("Mismatched indentation level.", lineNumber, current);
            }
            while (indent < indentStack.peek()) {
                indentStack.pop();
                tokens.add(createToken(TokenType.DEDENT));
            }
        }
        return tokens;
    }

    private void skipLine() {
        while (!isEOL()) advance();
    }

    private boolean isEOL() {
        return current >= curLine.length();
    }

    private char advance() {
        return curLine.charAt(current++);
    }

    private char peek() {
        if (isEOL()) return '\0';
        return curLine.charAt(current);
    }

    private Token createToken(TokenType type) {
        return createToken(type, null);
    }

    private Token createToken(TokenType type, Object literal) {
        return new Token(type, literal, lineNumber);
    }
}