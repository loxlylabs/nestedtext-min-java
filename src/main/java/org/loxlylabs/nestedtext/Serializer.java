package org.loxlylabs.nestedtext;

/**
 * A functional interface for providing a custom serialization strategy for a specific class.
 * This is useful for types that the default reflection-based serializer cannot handle correctly.
 *
 * @param <T> The type to adapt.
 */
@FunctionalInterface
public interface Serializer<T> {
    /**
     * Converts a given value of type T into a NestedText-compatible object.
     * The returned object should be a {@code String}, {@code Map}, or {@code Collection}.
     *
     * @param value The object to convert.
     * @return The NestedText-compatible representation of the value.
     */
    Object serialize(T value);
}
