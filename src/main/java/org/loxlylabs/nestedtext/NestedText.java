package org.loxlylabs.nestedtext;

import java.io.*;
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

    private final Map<Class<?>, Deserializer<?>> deserializers = new HashMap<>();
    private final Map<Class<?>, Serializer<?>> serializers = new HashMap<>();

    /**
     * Creates a new NestedText instance.
     */
    public NestedText() {
    }

    /**
     * Registers a custom deserializer for deserializing a value to specific class.
     *
     * @param type    The class type to register the deserializer for.
     * @param deserializer The adapter that converts a {@code String}, {@code Map}, or {@code List} to an instance of the class.
     * @param <T>     The type of the class.
     * @return This {@code NestedText} instance for fluent configuration.
     */
    public <T> NestedText registerDeserializer(Class<T> type, Deserializer<T> deserializer) {
        deserializers.put(type, deserializer);
        return this;
    }

    /**
     * Registers a custom serializer for serializing a specific class to a NestedText-compatible format.
     *
     * @param type    The class type to register the serializer for.
     * @param serializer The adapter that converts an instance of the class to a {@code String}, {@code Map}, or {@code List}.
     * @param <T>     The type of the class.
     * @return This {@code NestedText} instance for fluent configuration.
     */
    public <T> NestedText registerSerializer(Class<T> type, Serializer<T> serializer) {
        serializers.put(type, serializer);
        return this;
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
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (InputStream is = new FileInputStream(path.toFile());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, decoder))) {
            return load(reader.lines());
        } catch (MalformedInputException ex) {
            int col = ex.getInputLength();
            throw new NestedTextException("invalid start byte", ex, 0, col);
        }
    }

    /**
     * Loads NestedText content from a path.
     *
     * @param <T>  The generic type of the target class.
     * @param path The path to the file to read from.
     * @param type The {@code Class} of the object to be created (e.g., {@code User.class}).
     * @return A new instance of the target type, populated with data.
     * @throws NestedTextException  if the file content is not valid NestedText.
     * @throws IOException if an I/O error occurs while reading the file.
     * @throws DeserializationException if the parsed data cannot be converted to the specified target type.
     */
    public <T> T load(Path path, Class<T> type) throws IOException {
        Object obj = load(path);
        return new DeserializationContext(deserializers).convert(obj, type);
    }

    /**
     * Loads NestedText content and converts it into a Java object.
     *
     * @param <T>      The generic type of the target class.
     * @param path     The path to the file to read from.
     * @param typeRef  A {@link TypeReference} that captures the complete generic type of the object.
     * @return A new instance of the target type, populated with data.
     * @throws NestedTextException if the string is not valid NestedText.
     * @throws IOException if an I/O error occurs while reading the file.
     * @throws DeserializationException if the parsed data cannot be converted to the specified target type.
     */

    public <T> T load(Path path, TypeReference<T> typeRef) throws IOException {
        Object obj = load(path);
        return new DeserializationContext(deserializers).convert(obj, typeRef);
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
            int col = e instanceof MalformedInputException mal ? mal.getInputLength() : 0;
            throw new NestedTextException("invalid start byte", e, 0, col);
        }
    }

    /**
     * Loads NestedText content from a byte array and converts it into a Java object.
     *
     * <p>This method decodes the byte array as UTF-8 with strict error handling.
     * Malformed byte sequences will result in an exception.
     *
     * @param <T>  The generic type of the target class.
     * @param data The byte array containing NestedText data, encoded in UTF-8.
     * @param type The {@code Class} of the object to be created (e.g., {@code User.class}).
     * @return A new instance of the target type, populated with data.
     * @throws NestedTextException if the byte array is not valid UTF-8 or if the decoded string is not valid NestedText.
     * @throws DeserializationException if the parsed data cannot be converted to the specified target type.
     */
    public <T> T load(byte[] data, Class<T> type) {
        Object obj = load(data);
        return new DeserializationContext(deserializers).convert(obj, type);
    }

    /**
     * Loads NestedText content from a byte array and converts it into a generic Java type.
     *
     * <p>This method decodes the byte array as UTF-8 with strict error handling.
     * Malformed byte sequences will result in an exception.
     *
     * @param <T>      The generic type of the target class.
     * @param data     The byte array containing NestedText data, encoded in UTF-8.
     * @param typeRef  A {@link TypeReference} that captures the complete generic type of the object.
     * @return A new instance of the target type, populated with data.
     * @throws NestedTextException if the byte array is not valid UTF-8 or if the decoded string is not valid NestedText.
     * @throws DeserializationException if the parsed data cannot be converted to the specified target type.
     */
    public <T> T load(byte[] data, TypeReference<T> typeRef) {
        Object obj = load(data);
        return new DeserializationContext(deserializers).convert(obj, typeRef);
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

    /**
     * Loads NestedText content and converts it into a Java object.
     *
     * @param <T>      The generic type of the target class.
     * @param contents The string containing NestedText data.
     * @param type     The {@code Class} of the object to be created (e.g., {@code User.class}).
     * @return A new instance of the target type, populated with data.
     * @throws NestedTextException if the string is not valid NestedText.
     * @throws DeserializationException if the parsed data cannot be converted to the specified target type.
     */
    public <T> T load(String contents, Class<T> type) {
        Object obj = load(contents);
        return new DeserializationContext(deserializers).convert(obj, type);
    }

    /**
     * Loads NestedText content and converts it into a generic Java type.
     *
     * @param <T>      The generic type of the target class.
     * @param contents The string containing NestedText data.
     * @param typeRef  A {@link TypeReference} that captures the complete generic type of the object.
     * @return A new instance of the target type, populated with data.
     * @throws NestedTextException if the string is not valid NestedText.
     * @throws DeserializationException if the parsed data cannot be converted to the specified target type.
     */
    public <T> T load(String contents, TypeReference<T> typeRef) {
        Object obj = load(contents);
        return new DeserializationContext(deserializers).convert(obj, typeRef);
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
     * @param obj     The object to serialize.
     * @return A string containing the NestedText representation of the object.
     * @throws NestedTextException if an unsupported object type is encountered or a reflection error occurs.
     */
    public String dump(Object obj) {
        return dump(obj, new DumpOptions(System.lineSeparator(), 4, true));
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
     * @param obj     The object to serialize.
     * @param options The serialization options, such as indent, eol char, etc.
     * @return A string containing the NestedText representation of the object.
     * @throws NestedTextException if an unsupported object type is encountered or a reflection error occurs.
     */
    public String dump(Object obj, DumpOptions options) {
        NestedTextSerializer serializer = new NestedTextSerializer(options, serializers);
        return serializer.serialize(obj);
    }
}