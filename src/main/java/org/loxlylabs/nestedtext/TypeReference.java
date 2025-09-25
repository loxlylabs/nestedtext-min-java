package org.loxlylabs.nestedtext;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Used for serializing NestedText to a List&lt;T&gt; or a Map&lt;K,V&gt;
 * @param <T> the generic type
 */
public abstract class TypeReference<T> {
    private final Type type;

    /**
     * Constructor which captures the generic type.
     * Used for converting NestedText directly to List or Map.
     */
    protected TypeReference() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof Class) {
            throw new IllegalArgumentException("TypeReference requires a generic type parameter.");
        }
        this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    }

    /**
     * Returns the generic {@link Type}.
     * @return The captured generic {@link Type}.
     */
    public Type getType() {
        return type;
    }
}
