package org.loxlylabs.nestedtext;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The main class for loading and dumping NestedText data. This class provides
 * methods to parse NestedText from a String or File and bind to Java classes.
 * It also provides methods to serialize Java objects back into a Minimal
 * NestedText formatted string.
 *
 * <p>This implementation supports the Minimal NestedText specification.
 *
 * <p>Usage Example:
 * <pre>{@code
 * record Person(String fullName, int age) {}
 *
 * // Deserializing from NestedText
 * String content = """
 *         name: Alice Smith
 *         age: 5
 *         """;
 *
 * // Use the builder to have field level control over
 * // serialization/deserialization
 * NestedText nt = NestedText.builder()
 *         .forType(Person.class, type -> {
 *             // Map "fullName" field in our record to "name"
 *             type.renameField("fullName").to("name");
 *         }).build();
 *
 * Person p = nt.from(content).as(Person.class);
 * System.out.println(p.fullName());
 *
 * // Serializing to NestedText
 * String nestedText = nt.dump(p);
 * System.out.println(nestedText);
 * // Prints:
 * // name: Alice Smith
 * // age: 5
 * }</pre>
 *
 * <p>Instances of this class are configurable and can be reused.
 *
 * @see <a href="https://nestedtext.org/en/latest/minimal-nestedtext.html">Minimal NestedText Specification</a>
 */
public class NestedText {

    private final Map<Class<?>, Deserializer<?>> deserializers;
    private final Map<Class<?>, Serializer<?>> serializers;
    private final Map<Class<?>, TypeMappingRules> typeMappingRules;
    private final String endOfLine;
    private final int indentAmount;

    /**
     * Creates a new NestedText instance.
     */
    private NestedText(Builder builder) {
        this.deserializers = Map.copyOf(builder.deserializers);
        this.serializers = Map.copyOf(builder.serializers);
        this.typeMappingRules = Map.copyOf(builder.typeMappingRules);
        this.endOfLine = builder.defaultEol;
        this.indentAmount = builder.defaultIndentAmount;
    }

    /**
     * Create a new NestedText Builder
     *
     * @return a new NestedText Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads (parses) NestedText content from a path.
     *
     * @param path The path to the file to read from.
     * @return a Reader which can convert the NestedText to Java types.
     * @throws NestedTextException  if the file content is not valid NestedText.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    public Reader from(Path path) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (InputStream is = new FileInputStream(path.toFile());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, decoder))) {
            return new Reader(load(reader.lines()));
        } catch (MalformedInputException ex) {
            int col = ex.getInputLength();
            throw new NestedTextException("invalid start byte", ex, 0, col);
        }
    }

    /**
     * Loads (parses) NestedText content from a byte array.
     *
     * <p>This method decodes the byte array as UTF-8 with strict error handling.
     * Malformed byte sequences will result in an exception.
     *
     * @param data The byte array containing NestedText data, encoded in UTF-8.
     * @return a Reader which can convert the NestedText to Java types.
     * @throws NestedTextException if the byte array is not valid UTF-8 or if the decoded string is not valid NestedText.
     */
    public Reader from(byte[] data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String text = decoder.decode(ByteBuffer.wrap(data)).toString();
            return from(text);
        } catch (CharacterCodingException e) {
            int col = e instanceof MalformedInputException mal ? mal.getInputLength() : 0;
            throw new NestedTextException("invalid start byte", e, 0, col);
        }
    }


    /**
     * Loads (parses) NestedText content from a string.
     *
     * @param contents The string containing NestedText data.
     * @return a Reader which can convert the NestedText to Java types.
     * @throws NestedTextException if the string is not valid NestedText.
     */
    public Reader from(String contents) {
        return new Reader(load(contents.lines()));
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
        return dump(obj, new DumpOptions(endOfLine, indentAmount));
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
        SerializationContext serializer = new SerializationContext(options, serializers, typeMappingRules);
        return serializer.serialize(obj);
    }

    /**
     * An intermediate object used to specify the target type for the parsed NestedText data.
     */
    public final class Reader {
        private final Object source;

        private Reader(Object source) {
            this.source = source;
        }

        /**
         * Completes the deserialization by converting the parsed data into the specified type.
         * @param targetClass The class to convert to.
         * @param <T> The target type.
         * @return An instance of the target type.
         */
        public <T> T as(Class<T> targetClass) {
            return new DeserializationContext(deserializers, typeMappingRules).convert(source, targetClass);
        }

        /**
         * Completes the deserialization by converting the parsed data into the specified generic type.
         * @param typeRef A {@link TypeReference} capturing the generic type.
         * @param <T> The target type.
         * @return An instance of the target type.
         */
        public <T> T as(TypeReference<T> typeRef) {
            return new DeserializationContext(deserializers, typeMappingRules).convert(source, typeRef);
        }

        /**
         * Returns the raw, untyped result of the parsing operation (a Map, List, or String).
         *
         * <p><b>Note:</b> This method returns the direct output of the parser and does NOT
         * apply any type-specific mapping rules (e.g., field renaming or ignoring).
         *
         * @return The untyped object.
         */
        public Object asObject() {
            return source;
        }
    }

    /**
     * A builder class to assist in creating NestedText instances.
     */
    public static class Builder {
        private final Map<Class<?>, Deserializer<?>> deserializers = new HashMap<>();
        private final Map<Class<?>, Serializer<?>> serializers = new HashMap<>();
        private final Map<Class<?>, TypeMappingRules> typeMappingRules = new HashMap<>();
        private String defaultEol = System.lineSeparator();
        private int defaultIndentAmount = 4;

        Builder() {
        }

        /**
         * Registers a custom deserializer for deserializing a value to specific class.
         *
         * @param type    The class type to register the deserializer for.
         * @param deserializer The adapter that converts a {@code String}, {@code Map}, or {@code List} to an instance of the class.
         * @param <T>     The type of the class.
         * @return This builder instance for chaining.
         */
        public <T> Builder registerDeserializer(Class<T> type, Deserializer<T> deserializer) {
            deserializers.put(type, deserializer);
            return this;
        }

        /**
         * Registers a custom serializer for serializing a specific class to a NestedText-compatible format.
         *
         * @param type    The class type to register the serializer for.
         * @param serializer The adapter that converts an instance of the class to a {@code String}, {@code Map}, or {@code List}.
         * @param <T>     The type of the class.
         * @return This builder instance for chaining.
         */
        public <T> Builder registerSerializer(Class<T> type, Serializer<T> serializer) {
            serializers.put(type, serializer);
            return this;
        }

        /**
         * Provides a fluent API for configuring serialization and deserialization
         * rules for a specific class without modifying the class itself.
         *
         * @param type The class to configure.
         * @param config A lambda expression to define the configuration rules.
         * @param <T> The type being configured.
         * @return This builder instance for chaining.
         */
        public <T> Builder forType(Class<T> type, Consumer<TypeConfiguration<T>> config) {
            TypeMappingRules rules = typeMappingRules.computeIfAbsent(type, k -> new TypeMappingRules());
            TypeConfiguration<T> typeConfig = new TypeConfiguration<>(rules);
            config.accept(typeConfig);
            return this;
        }

        /**
         * Configures default end of line separator to use when dumping NestedText
         *
         * @param eol  the end of line separator
         * @return This builder instance for chaining.
         */
        public Builder endOfLine(String eol) {
            this.defaultEol = eol;
            return this;
        }

        /**
         * Configures default indentation to use when dumping NestedText
         *
         * @param indent the indent amount
         * @return This builder instance for chaining.
         */
        public Builder indentAmount(int indent) {
            if (indent <= 0) {
                throw new IllegalArgumentException("Indent amount must be positive.");
            }
            this.defaultIndentAmount = indent;
            return this;
        }

        /**
         * Builds an immutable {@link NestedText} instance with the configured settings.
         *
         * @return A new {@code NestedText} instance.
         */
        public NestedText build() {
            return new NestedText(this);
        }
    }

    /**
     * Provides a fluent API for configuring type-specific mapping rules.
     * @param <T> The type being configured.
     */
    public static class TypeConfiguration<T> {
        private final TypeMappingRules rules;

        private TypeConfiguration(TypeMappingRules rules) {
            this.rules = rules;
        }

        /**
         * Specifies a field to be ignored during serialization and deserialization.
         *
         * @param fieldName The name of the field in the Java object.
         * @return this configuration instance for chaining.
         */
        public TypeConfiguration<T> ignoreField(String fieldName) {
            rules.fieldsToIgnore().add(fieldName);
            return this;
        }

        /**
         * Specifies a field to be ignored during serialization when the value is null.
         *
         * @param fieldName The name of the field in the Java object.
         * @return this configuration instance for chaining.
         */
        public TypeConfiguration<T> ignoreFieldWhenNull(String fieldName) {
            rules.fieldsToIgnoreWhenNull().add(fieldName);
            return this;
        }

        /**
         * Specifies a new name for a field during serialization and deserialization.
         *
         * @param fieldName The name of the field in the Java object.
         * @return A {@link Renamer} to specify the new name.
         */
        public Renamer renameField(String fieldName) {
            return new Renamer(fieldName, rules);
        }
    }

    /**
     * Helper class to complete the renameField operation.
     */
    public static class Renamer {
        private final String fromField;
        private final TypeMappingRules rules;

        Renamer(String fromField, TypeMappingRules rules) {
            this.fromField = fromField;
            this.rules = rules;
        }

        /**
         * Sets the target name for the field in the NestedText document.
         * @param toField The name to be used in the NestedText output/input.
         */
        public void to(String toField) {
            rules.renameFromJava().put(fromField, toField);
            rules.renameTo().put(toField, fromField);
        }
    }
}