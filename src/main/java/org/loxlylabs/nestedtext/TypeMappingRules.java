package org.loxlylabs.nestedtext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Contains the type level rules for serialization / deserialization.
 *
 * @param renameFromJava    the Java field we're renaming from
 * @param renameTo          the field we're renaming to in the NestedText output
 * @param fieldsToIgnore    fields to ignore when serializing
 * @param fieldsToIgnoreWhenNull  fields to ignore when serializing if the value is null
 */
public record TypeMappingRules(
        Map<String, String> renameFromJava,
        Map<String, String> renameTo,
        Set<String> fieldsToIgnore,
        Set<String> fieldsToIgnoreWhenNull
) {
    /**
     * Creates a new TypeMappingRules ensuring all arguments are non-null.
     *
     * @param renameFromJava    the Java field we're renaming from
     * @param renameTo          the field we're renaming to in the NestedText output
     * @param fieldsToIgnore    fields to ignore when serializing
     * @param fieldsToIgnoreWhenNull  fields to ignore when serializing if the value is null
     */
    public TypeMappingRules {
        if (renameFromJava == null) {
            throw new IllegalArgumentException("renameFromJava cannot be null");
        }
        if (renameTo == null) {
            throw new IllegalArgumentException("renameTo cannot be null");
        }
        if (fieldsToIgnore == null) {
            throw new IllegalArgumentException("fieldsToIgnore cannot be null");
        }
        if (fieldsToIgnoreWhenNull == null) {
            throw new IllegalArgumentException("fieldsToIgnoreWhenNull cannot be null");
        }
    }

    /**
     * Creates a new TypeMappingRules object initializing all fields with empty collections.
     */
    public TypeMappingRules() {
        this(new HashMap<>(), new HashMap<>(), new HashSet<>(), new HashSet<>());
    }
}
