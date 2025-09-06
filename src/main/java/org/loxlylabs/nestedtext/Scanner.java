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
            tokens.add(createToken(TokenType.DEDENT, 0));
            indentStack.pop();
        }
        tokens.add(createToken(TokenType.EOF, 0));
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

        tokens.add(createToken(TokenType.NEWLINE, current));

        return tokens;
    }

    private List<Token> processListLine() {
        final int keyStart = current - 1;
        if (peek() == ' ') {
            advance();
        } else {
            if (!isEOL()) {
                return processDictionaryLine();
            }
        }
        List<Token> tokens = new ArrayList<>();
        tokens.add(createToken(TokenType.DASH, keyStart));
        if (!isEOL()) {
            tokens.add(processString());
        }
        return tokens;
    }

    private List<Token> processMultilineStringLine() {
        final int keyStart = current - 1;
        if (peek() == ' ') {
            advance();
        } else {
            if (!isEOL()) {
                return processDictionaryLine();
            }
        }
        List<Token> tokens = new ArrayList<>();
        tokens.add(createToken(TokenType.GREATER, keyStart));
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

    // While Java has a stripTrailing method, it does not encompass
    // non-breaking spaces and others.
    private static String stripTrailingWhitespace(String s) {
        int end = s.length();
        while (end > 0 && isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    private static boolean isWhitespace(int c) {
        // Match Unicode White_Space characters
        return Character.getType(c) == Character.SPACE_SEPARATOR
                || Character.isWhitespace(c);
    }

    private Token processKey() {
        final int keyStart = current;

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

                    if (!value.isEmpty()) {
                        char firstChar = value.charAt(0);
                        if (firstChar == '[' || firstChar == '{') {
                            throw new NestedTextException("key may not start with '" + firstChar + "'.",
                                    lineNumber - 1,
                                    keyStart);
                        }
                    }

                    // whitespace before the colon is trimmed
                    return createToken(TokenType.KEY, stripTrailingWhitespace(value), keyStart);
                }
            } else {
                advance();
            }
        }
        throw new NestedTextException("unrecognized line.", lineNumber - 1, keyStart);
    }

    private Token processString() {
        String value = curLine.substring(current);
        return createToken(TokenType.STRING, value, current);
    }

    private String whiteSpaceToString(char c) {
        return switch (c) {
            case '\t' -> "'\\t'";
            case '\u00A0' -> "'\\xa0' (NO-BREAK SPACE)";
            case '\u1680' -> "'\\u1680' (OGHAM SPACE MARK)";
            case '\u2000' -> "'\\u2000' (EN QUAD)";
            case '\u2001' -> "'\\u2001' (EM QUAD)";
            case '\u2002' -> "'\\u2002' (EN SPACE)";
            case '\u2003' -> "'\\u2003' (EM SPACE)";
            case '\u2004' -> "'\\u2004' (THREE-PER-EM SPACE)";
            case '\u2005' -> "'\\u2005' (FOUR-PER-EM SPACE)";
            case '\u2006' -> "'\\u2006' (SIX-PER-EM SPACE)";
            case '\u2007' -> "'\\u2007' (FIGURE SPACE)";
            case '\u2008' -> "'\\u2008' (PUNCTUATION SPACE)";
            case '\u2009' -> "'\\u2009' (THIN SPACE)";
            case '\u200A' -> "'\\u200A' (HAIR SPACE)";
            case '\u202F' -> "'\\u202F' (NARROW NO-BREAK SPACE)";
            case '\u205F' -> "'\\u205F' (MEDIUM MATHEMATICAL SPACE)";
            case '\u3000' -> "'\\u3000' (IDEOGRAPHIC SPACE)";
            default -> {
                if (Character.isWhitespace(c)) {
                    yield String.format("'\\u%04X' (WHITESPACE)", (int) c);
                } else {
                    yield String.format("'\\u%04X'", (int) c);
                }
            }
        };
    }

    private List<Token> handleIndentation() {
        List<Token> tokens = new ArrayList<>();

        int indent = 0;
        while (peek() == ' ') {
            indent++;
            advance();
        }

        if (isWhitespace(peek())) {
            throw new NestedTextException("invalid character in indentation: "
                    + whiteSpaceToString(peek())
                    + ".", lineNumber - 1, current);
        }

        if (peek() == '#' || isEOL()) {
            skipLine();
            return null;
        }

        Integer lastIndent = indentStack.peek();

        if (indent > lastIndent) {
            indentStack.push(indent);
            tokens.add(createToken(TokenType.INDENT, indent));
        } else if (indent < lastIndent) {
            if (!indentStack.contains(indent)) {
                throw new NestedTextException("invalid indentation, partial dedent.", lineNumber - 1, 0);
            }
            while (indent < indentStack.peek()) {
                indentStack.pop();
                tokens.add(createToken(TokenType.DEDENT, 0));
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

    private Token createToken(TokenType type, int column) {
        return createToken(type, null, column);
    }

    private Token createToken(TokenType type, Object literal, int column) {
        return new Token(type, literal, lineNumber - 1, column);
    }
}