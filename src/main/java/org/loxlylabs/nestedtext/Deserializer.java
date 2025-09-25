package org.loxlylabs.nestedtext;

/**
 * Defines a custom strategy for deserializing a List, String, or Map into a
 * specific Java type {@code T}.
 *
 * @param <T> The target Java type this deserializer is responsible for creating.
 * @see DeserializationContext
 */
@FunctionalInterface
public interface Deserializer<T> {
    /**
     * Converts a NestedText-compatible object into a Java object of type T.
     * @param value The object (either a Map, List, String).
     * @param context the deserialization context
     * @return The deserialized Java object.
     */
    T deserialize(Object value, DeserializationContext context);
}
