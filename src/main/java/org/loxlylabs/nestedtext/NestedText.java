package org.loxlylabs.nestedtext;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * The main class for loading and dumping NestedText data. This class provides methods
 * to parse NestedText from a String or File into Java objects (typically a {@code Map<String, Object>},
 * {@code List<Object>}, or {@code String}) and to serialize Java objects back into a NestedText formatted string.
 *
 * <p>This implementation supports the Minimal NestedText specification.
 *
 * <p>Usage Example:
 * <pre>{@code
 * // Create a new NestedText instance
 * NestedText nt = new NestedText();
 *
 * // Loading NestedText
 * String data = """
 * name: John Doe
 * age: 42
 * children:
 * - Jane
 * - Bill
 * """;
 * Map<String, Object> person = (Map<String, Object>) nt.load(data);
 * System.out.println(person.get("name")); // Prints "John Doe"
 *
 * // Serializing to NestedText
 * Map<String, Object> address = Map.of("city", "Anytown", "zip", "12345");
 * String nestedText = nt.dump(address);
 * System.out.println(nestedText);
 * // Prints:
 * // city: Anytown
 * // zip: 12345
 * }</pre>
 *
 * <p>Instances of this class are configurable and can be reused.
 *
 * @see <a href="https://nestedtext.org/en/latest/minimal-nestedtext.html">Minimal NestedText Specification</a>
 */
public class NestedText {

    /**
     * A functional interface for providing a custom serialization strategy for a specific class.
     * This is useful for types that the default reflection-based serializer cannot handle correctly.
     *
     * @param <T> The type to adapt.
     */
    @FunctionalInterface
    public interface Adapter<T> {
        /**
         * Converts a given value of type T into a NestedText-compatible object.
         * The returned object should be a {@code String}, {@code Map}, or {@code Collection}.
         *
         * @param value The object to convert.
         * @return The NestedText-compatible representation of the value.
         */
        Object toNestedTextValue(T value);
    }

    private final Map<Class<?>, Adapter<?>> adapters = new HashMap<>();
    private String eol = System.lineSeparator();
    private int indentAmount = 4;
    private boolean useReflection = true;

    /**
     * Creates a new NestedText instance with default settings.
     * <ul>
     * <li>Indentation: 4 spaces</li>
     * <li>Line Separator: System default</li>
     * <li>Reflection for dumping: enabled</li>
     * </ul>
     */
    public NestedText() {
    }

    /**
     * Registers a custom adapter for serializing a specific class to a NestedText-compatible format.
     *
     * @param type    The class type to register the adapter for.
     * @param adapter The adapter that converts an instance of the class to a {@code String}, {@code Map}, or {@code List}.
     * @param <T>     The type of the class.
     * @return This {@code NestedText} instance for fluent configuration.
     */
    public <T> NestedText registerAdapter(Class<T> type, Adapter<T> adapter) {
        adapters.put(type, adapter);
        return this;
    }

    /**
     * Sets the number of spaces to use for each level of indentation when dumping data.
     * The default is 4.
     *
     * @param numSpaces The number of spaces for indentation (must be positive).
     * @return This {@code NestedText} instance for fluent configuration.
     */
    public NestedText indent(int numSpaces) {
        this.indentAmount = numSpaces;
        return this;
    }

    /**
     * Sets the line separator string to use when dumping data.
     * The default is the system's line separator.
     *
     * @param lineSeparator The string to use for line breaks (e.g., "\n" or "\r\n").
     * @return This {@code NestedText} instance for fluent configuration.
     */
    public NestedText lineSeparator(String lineSeparator) {
        this.eol = lineSeparator;
        return this;
    }

    /**
     * Enables or disables the use of reflection for serializing arbitrary Java objects during a dump operation.
     * When enabled (default), the library will attempt to serialize records and POJOs by inspecting their
     * fields and components. If disabled, a {@code NestedTextException} will be thrown for unsupported types.
     *
     * @param useReflection {@code true} to enable reflection (default), {@code false} to disable.
     * @return This {@code NestedText} instance for fluent configuration.
     */
    public NestedText useReflection(boolean useReflection) {
        this.useReflection = useReflection;
        return this;
    }

    /**
     * Loads (parses) NestedText content from a file.
     *
     * @param file The file to read from.
     * @return A {@code Map<String, Object>}, {@code List<Object>}, {@code String}, or {@code null} if the file is empty.
     * @throws NestedTextException  if the file content is not valid NestedText.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    public Object load(File file) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (InputStream is = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, decoder))) {
              return load(reader.lines());
        } catch (MalformedInputException ex) {
            throw new NestedTextException("invalid start byte", ex);
        }
    }

    /**
     * Loads (parses) NestedText content from a path.
     *
     * @param path The path to the file to read from.
     * @return A {@code Map<String, Object>}, {@code List<Object>}, {@code String}, or {@code null} if the file is empty.
     * @throws NestedTextException  if the file content is not valid NestedText.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    public Object load(Path path) throws IOException {
        return load(path.toFile());
    }

    /**
     * Loads (parses) NestedText content from a byte array.
     *
     * <p>This method decodes the byte array as UTF-8 with strict error handling.
     * Malformed byte sequences will result in an exception.
     *
     * @param data The byte array containing NestedText data, encoded in UTF-8.
     * @return A {@code Map<String, Object>}, {@code List<Object>}, {@code String}, or {@code null} if the content is empty.
     * @throws NestedTextException if the byte array is not valid UTF-8 or if the decoded string is not valid NestedText.
     */
    public Object load(byte[] data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String text = decoder.decode(ByteBuffer.wrap(data)).toString();
            return load(text);
        } catch (CharacterCodingException e) {
            throw new NestedTextException("invalid start byte", e);
        }
    }

    /**
     * Loads (parses) NestedText content from a string.
     *
     * @param contents The string containing NestedText data.
     * @return A {@code Map<String, Object>}, {@code List<Object>}, {@code String}, or {@code null} if the string is empty or contains only whitespace/comments.
     * @throws NestedTextException if the string is not valid NestedText.
     */
    public Object load(String contents) {
        return load(contents.lines());
    }

    private Object load(Stream<String> lines) {
        Scanner scanner = new Scanner(lines);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    /**
     * Dumps (serializes) a Java object into a NestedText formatted string.
     * <p>
     * The method supports common Java types by default:
     * <ul>
     * <li>{@code Map} (keys are converted to strings)</li>
     * <li>{@code Collection} and arrays (become lists)</li>
     * <li>{@code String}, {@code Number}, {@code Boolean}, {@code Character}, {@code Enum} (become strings)</li>
     * <li>Java Records and POJOs (become dictionaries, requires reflection)</li>
     * </ul>
     *
     * @param obj The object to serialize.
     * @return A string containing the NestedText representation of the object.
     * @throws NestedTextException if an unsupported object type is encountered or a reflection error occurs.
     */
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
            case Boolean b -> b.toString();
            case Number n -> n.toString();
            case Enum<?> e -> e.name();
            case Character c -> c.toString();
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
                } else if (o.getClass().isArray()) {
                    List<Object> list = new ArrayList<>();
                    int length = Array.getLength(o);
                    for (int i = 0; i < length; i++) {
                        list.add(toNestedTextCompatible(Array.get(o, i)));
                    }
                    yield list;
                } else if (o.getClass().isRecord()) {
                    Map<String,Object> map = new LinkedHashMap<>();
                    for (var comp : o.getClass().getRecordComponents()) {
                        try {
                            Object value = comp.getAccessor().invoke(o);
                            map.put(comp.getName(), toNestedTextCompatible(value));
                        } catch (Exception e) {
                            throw new NestedTextException("Failed to serialize field '"
                                    + comp.getName()
                                    + "' from object of type "
                                    + o.getClass().getSimpleName(), e);
                        }
                    }
                    yield map;
                } else {
                    // fallback: use public fields
                    Map<String,Object> map = new LinkedHashMap<>();
                    for (var field : o.getClass().getDeclaredFields()) {
                        try {
                            field.setAccessible(true);
                            Object value = field.get(o);
                            map.put(field.getName(), toNestedTextCompatible(value));
                        } catch (Exception e) {
                            throw new NestedTextException("Failed to serialize field '"
                                    + field.getName()
                                    + "' from object of type "
                                    + o.getClass().getSimpleName(), e);
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