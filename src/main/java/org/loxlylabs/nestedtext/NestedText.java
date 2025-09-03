package org.loxlylabs.nestedtext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class NestedText {

    // must return String, Map, List, or nested adapters
    @FunctionalInterface
    public interface Adapter<T> {
        Object toNestedTextValue(T value);
    }

    private final Map<Class<?>, Adapter<?>> adapters = new HashMap<>();
    private String eol = System.lineSeparator();
    private int indentAmount = 4;
    private boolean useReflection = true;

    public NestedText() {
    }

    public <T> NestedText registerAdapter(Class<T> type, Adapter<T> adapter) {
        adapters.put(type, adapter);
        return this;
    }

    public NestedText indent(int numSpaces) {
        this.indentAmount = numSpaces;
        return this;
    }

    public NestedText lineSeparator(String lineSeparator) {
        this.eol = lineSeparator;
        return this;
    }

    public NestedText useReflection(boolean useReflection) {
        this.useReflection = useReflection;
        return this;
    }

    public Object load(File file) throws IOException {
        try (InputStream is = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
              return load(reader.lines());
        }
    }

    public Object load(Path path) throws IOException {
        return load(path.toFile());
    }

    public Object load(String contents) {
        return load(contents.lines());
    }

    private Object load(Stream<String> lines) {
        Scanner scanner = new Scanner(lines);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    public String dump(Object obj) {
        StringBuilder sb = new StringBuilder();
        if (useReflection) {
            obj = toNestedTextCompatible(obj);
        }
        dumpValue(obj, sb, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> Object applyAdapter(Class<T> type, Object value) {
        Adapter<T> adapter = (Adapter<T>) adapters.get(type);
        return adapter.toNestedTextValue((T) value);
    }

    /**
     * Uses reflection so we can serialize any Java class to NestedText
     */
    private Object toNestedTextCompatible(Object o) {
        return switch (o) {
            case null -> null;
            case String s -> s;
            case Map<?, ?> m -> {
                Map<String, Object> result = new LinkedHashMap<>();
                for (var e : m.entrySet()) {
                    result.put(
                            String.valueOf(e.getKey()),
                            toNestedTextCompatible(e.getValue())
                    );
                }
                yield result;
            }
            case Collection<?> l -> l.stream()
                    .map(this::toNestedTextCompatible)
                    .toList();
            default -> {
                if (adapters.containsKey(o.getClass())) {
                    yield applyAdapter(o.getClass(), o);
                } else if (o.getClass().isRecord()) {
                    Map<String,Object> map = new LinkedHashMap<>();
                    for (var comp : o.getClass().getRecordComponents()) {
                        try {
                            Object value = comp.getAccessor().invoke(o);
                            map.put(comp.getName(), toNestedTextCompatible(value));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    yield map;
                } else {
                    // fallback: use public fields
                    Map<String,Object> map = new LinkedHashMap<>();
                    for (var field : o.getClass().getFields()) {
                        try {
                            Object value = field.get(o);
                            map.put(field.getName(), toNestedTextCompatible(value));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    yield map;
                }
            }
        };
    }

    private void dumpValue(Object obj, StringBuilder sb, int indent) {
        switch (obj) {
            case null ->  {}
            case String s -> dumpString(s, sb, indent);
            case Map<?, ?> m -> dumpMap(m, sb, indent);
            case Collection<?> l -> dumpList(l, sb, indent);
            default -> throw new IllegalArgumentException(
                    "Unsupported type for NestedText dump: " + obj.getClass()
            );
        }
    }

    private void dumpString(String s, StringBuilder sb, int indent) {
        String indentStr = " ".repeat(indent);
        if (s.contains(eol)) {
            // multiline string
            for (String line : s.split(eol)) {
                sb.append(indentStr)
                        .append("> ")
                        .append(line)
                        .append(eol);
            }
        } else {
            if (indent == 0) {
                sb.append("> ");
            }
            sb.append(indentStr)
                    .append(s)
                    .append(eol);
        }
    }

    private void dumpMap(Map<?,?> m, StringBuilder sb, int indent) {
        String indentStr = " ".repeat(indent);
        for (var entry : m.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            // Single line: key: value
            if (value instanceof String s && !s.contains(eol)) {
                sb.append(indentStr)
                        .append(key)
                        .append(": ")
                        .append(s)
                        .append(eol);
            } else {
                sb.append(indentStr)
                        .append(key)
                        .append(":")
                        .append(eol);
                dumpValue(value, sb, indent + indentAmount);
            }
        }
    }

    private void dumpList(Collection<?> collection, StringBuilder sb, int indent) {
        String indentStr = " ".repeat(indent);
        for (var item : collection) {
            if (item instanceof String s && !s.contains(eol)) {
                sb.append(indentStr)
                        .append("- ")
                        .append(s)
                        .append(eol);
            } else {
                sb.append(indentStr)
                        .append("-")
                        .append(eol);
                dumpValue(item, sb, indent + indentAmount);
            }
        }
    }
}