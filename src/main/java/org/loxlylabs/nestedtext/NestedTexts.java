package org.loxlylabs.nestedtext;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility class to serialize or deserialize with default configuration.
 */
public class NestedTexts {

    private static final NestedText DEFAULT_INSTANCE = NestedText.builder().build();

    // Private constructor to prevent instantiation.
    private NestedTexts() {}

    /**
     * Dumps an object to a NestedText string using default settings.
     * @param obj The object to serialize.
     * @return The NestedText string representation.
     */
    public static String dump(Object obj) {
        return DEFAULT_INSTANCE.dump(obj);
    }

    /**
     * Begins a fluent deserialization operation from a String source.
     *
     * @param content The NestedText string content.
     * @return A {@link NestedText.Reader} instance to specify the target type.
     * @throws NestedTextException if the string is not valid NestedText.
     */
    public static NestedText.Reader from(String content) {
        return DEFAULT_INSTANCE.from(content);
    }

    /**
     * Begins a fluent deserialization operation from a Path source.
     *
     * @param path The path to the file containing NestedText.
     * @return A {@link NestedText.Reader} instance to specify the target type.
     * @throws NestedTextException  if the file content is not valid NestedText.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    public static NestedText.Reader from(Path path) throws IOException {
        return DEFAULT_INSTANCE.from(path);
    }

    /**
     * Begins a fluent deserialization operation from a byte[] source.
     *
     * @param data The byte array containing NestedText data.
     * @return A {@link NestedText.Reader} instance to specify the target type.
     * @throws NestedTextException if the byte array is not valid UTF-8 or if the decoded string is not valid NestedText.
     */
    public static NestedText.Reader from(byte[] data) {
        return DEFAULT_INSTANCE.from(data);
    }
}