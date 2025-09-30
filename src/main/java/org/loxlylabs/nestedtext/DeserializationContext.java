package org.loxlylabs.nestedtext;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * Provides context for a deserialization operation and serves as the main entry point
 * for data binding.
 * <p>
 * This is the primary class for deserializing a String, List, or Map object
 * structure parsed from Minimal NestedText into a strongly-typed Java object.
 *
 * <p>The conversion process follows a set of configurable rules:
 * <ul>
 * <li>A {@code Map<String, Object>} is converted to a Java Record or POJO.</li>
 * <li>A {@code List<Object>} is converted to a {@code Collection} (e.g., ArrayList) or an array.</li>
 * <li>A {@code String} is parsed into a simple type (e.g., int, boolean, BigDecimal, Enum).</li>
 * </ul>
 *
 * @see Deserializer
 * @see TypeReference
 * @see DeserializationException
 */
public class DeserializationContext {

    private final Map<Class<?>, Deserializer<?>> customDeserializers;
    private final Map<Class<?>, TypeMappingRules> typeMappingRules;

    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_MAP = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            void.class, Void.class
    );

    private static final Map<Class<?>, Function<String, ?>> STRING_CONVERTERS = Map.ofEntries(
            Map.entry(String.class, s -> s),
            Map.entry(Integer.class, Integer::valueOf),
            Map.entry(Long.class, Long::valueOf),
            Map.entry(Double.class, Double::valueOf),
            Map.entry(Float.class, Float::valueOf),
            Map.entry(Short.class, Short::valueOf),
            Map.entry(Byte.class, Byte::valueOf),
            Map.entry(BigDecimal.class, BigDecimal::new),
            Map.entry(BigInteger.class, BigInteger::new),
            Map.entry(UUID.class, UUID::fromString),
            Map.entry(Boolean.class, Boolean::parseBoolean),
            Map.entry(LocalDate.class, LocalDate::parse),
            Map.entry(LocalTime.class, LocalTime::parse),
            Map.entry(LocalDateTime.class, LocalDateTime::parse),
            Map.entry(Instant.class, Instant::parse),
            Map.entry(ZonedDateTime.class, ZonedDateTime::parse),
            Map.entry(Duration.class, Duration::parse),
            Map.entry(Period.class, Period::parse)
    );

    /**
     * Creates a new context with no custom deserializers.
     *
     * @param typeMappingRules the type specific rules for deserialization
     */
    public DeserializationContext(Map<Class<?>, TypeMappingRules> typeMappingRules) {
        this.typeMappingRules = typeMappingRules;
        this.customDeserializers = new HashMap<>();
    }


    /**
     * Creates a new context with the given custom deserializers.
     *
     * @param customDeserializers A map of type-to-deserializer mappings to use for conversion.
     * @param typeMappingRules the type specific rules for deserialization
     */
    public DeserializationContext(Map<Class<?>, Deserializer<?>> customDeserializers, Map<Class<?>, TypeMappingRules> typeMappingRules) {
        this.customDeserializers = new HashMap<>(customDeserializers);
        this.typeMappingRules = typeMappingRules;
    }

    /**
     * Converts a source object graph into an instance of a specified non-generic target class.
     *
     * <p>This method is the primary entry point for deserializing into simple, non-generic
     * types like {@code User.class} or {@code String.class}. For types with generics
     * (e.g., {@code List<Integer>}), use {@link #convert(Object, TypeReference)} instead.
     *
     * @param <T>         The generic type of the target class.
     * @param source      The raw source object (Map, List, or String).
     * @param targetClass The {@code Class} of the object to be created, e.g., {@code User.class}.
     * @return A new instance of the {@code targetClass}, populated with data from the source.
     * @throws DeserializationException if the conversion fails for any reason.
     */
    public <T> T convert(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }

        return convertRecursively(source, targetClass, null);
    }

    /**
     * Converts a source object graph into an instance of a specified generic target class.
     *
     * <pre>{@code
     * // To convert to a List<Integer>:
     * TypeReference<List<Integer>> typeRef = new TypeReference<List<Integer>>() {};
     * List<Integer> numbers = context.convert(sourceList, typeRef);
     * }</pre>
     *
     * Or simply:
     * <pre>{@code
     * List<Integer> numbers = context.convert(sourceList, new TypeReference<>() {});
     * }</pre>
     *
     * For simple, non-generic types, the overload {@link #convert(Object, Class)} can be
     * used for convenience.
     *
     * @param <T>     The generic type of the target class.
     * @param source  The raw source object (Map, List, or String). A {@code null} source
     * yields a {@code null} output.
     * @param typeRef A {@link TypeReference} that captures the complete generic type.
     * @return A new instance of the target type, populated with data from the source.
     * @throws DeserializationException if the conversion fails for any reason.
     */
    @SuppressWarnings("unchecked")
    public <T> T convert(Object source, TypeReference<T> typeRef) {
        if (source == null) {
            return null;
        }

        Type targetType = typeRef.getType();
        Class<?> rawClass;

        if (targetType instanceof Class<?>) {
            rawClass = (Class<?>) targetType;
        } else if (targetType instanceof ParameterizedType) {
            rawClass = (Class<?>) ((ParameterizedType) targetType).getRawType();
        } else {
            throw new DeserializationException("Unsupported type reference: " + targetType);
        }

        // Call the internal recursive method with all the necessary type info
        return (T) convertRecursively(source, rawClass, targetType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T convertRecursively(Object source, Class<T> targetClass, Type genericType) {
        if (source == null) {
            return null;
        }

        // Use custom deserializer if found
        if (customDeserializers.containsKey(targetClass)) {
            return (T) customDeserializers.get(targetClass).deserialize(source, this);
        }

        if (source instanceof String str && str.isEmpty()) {
            // Minimal NestedText cannot represent empty list or empty map.
            // If we dump out an object where a field's value is an empty list or map
            // it would look like this:
            // users:<EOL>
            // When we load that NestedText, it reads it as an empty String.
            if (List.class.isAssignableFrom(targetClass) || Collection.class.isAssignableFrom(targetClass)) {
                return (T) Collections.emptyList();
            }
            if (Map.class.isAssignableFrom(targetClass)) {
                return (T) Collections.emptyMap();
            }
                // assign null if the value is empty string and target is a pojo, record, etc.
            if (targetClass != String.class && !targetClass.isPrimitive()) {
                return null;
            }
        }

        if (source instanceof String s) {
            T convertedFromString = convertFromString(s, targetClass);
            if (convertedFromString != null) {
                return convertedFromString;
            }
        }

        if (source instanceof Map<?, ?> sourceMap) {
            // our source is a Map<String,Object> and our target class is a Map<K,V>
            // so we must convert both the keys and values into a new Map.
            if (Map.class.isAssignableFrom(targetClass)) {
                return (T) convertMapToMap((Map<String, Object>) sourceMap, genericType);
            }
            // typical path - converting Map to a record or pojo
            return convertMapToObject( (Map<String, Object>) sourceMap, targetClass);
        } else if (source instanceof List<?> sourceList) {
            if (targetClass.isArray()) {
                return (T) convertListToArray(sourceList, targetClass);
            } else if (Collection.class.isAssignableFrom(targetClass)) {
                return (T) convertListToCollection(sourceList, (Class<? extends Collection>) targetClass, genericType);
            }
        }

        throw new DeserializationException(
                "Cannot convert from " + source.getClass().getName() +
                        " to " + targetClass.getName() + ". No direct mapping or custom deserializer found."
        );
    }

    private Map<?, ?> convertMapToMap(Map<String, Object> sourceMap, Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            // Our target Map class must have type information
            throw new DeserializationException("Cannot convert to Map without generic type information: " + genericType);
        }

        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        Type keyType = typeArguments[0];
        Type valueType = typeArguments[1];

        Map<Object, Object> newMap = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            // Source key is always a String, but target Map may have a different key type
            Object convertedKey = convertRecursively(entry.getKey(), (Class<?>) keyType, keyType);
            Object convertedValue = convertRecursively(entry.getValue(),
                    (Class<?>) (valueType instanceof ParameterizedType ? ((ParameterizedType) valueType).getRawType() : valueType),
                    valueType);
            newMap.put(convertedKey, convertedValue);
        }
        return newMap;
    }

    private <T> T convertMapToObject(Map<String, Object> sourceMap, Class<T> targetClass) {
        try {
            if (targetClass.isRecord()) {
                return convertMapToRecord(sourceMap, targetClass);
            } else {
                return convertMapToPojo(sourceMap, targetClass);
            }
        } catch (Exception e) {
            throw new DeserializationException("Failed to convert Map to " + targetClass.getName(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T convertFromString(String s, Class<T> targetClass) {
        Class<?> effectiveTargetClass = targetClass.isPrimitive()
                ? PRIMITIVE_WRAPPER_MAP.get(targetClass)
                : targetClass;

        Function<String, ?> converter = STRING_CONVERTERS.get(effectiveTargetClass);

        try {
            if (converter != null) {
                return (T) converter.apply(s);
            }

            if (effectiveTargetClass.isEnum()) {
                return (T) Enum.valueOf((Class<Enum>) effectiveTargetClass, s);
            }
        } catch (Exception e) {
            throw new DeserializationException("Failed to parse string '"
                    + s
                    + "' into type "
                    + targetClass.getName(), e);
        }

        // no conversion was found
        return null;
    }

    private <T> T convertMapToRecord(Map<String, Object> sourceMap, Class<T> recordClass) throws Exception {
        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] constructorArgs = new Object[components.length];
        Class<?>[] constructorParamTypes = new Class<?>[components.length];
        TypeMappingRules typeMappingRulesForClass = typeMappingRules.get(recordClass);

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String name = component.getName();
            Class<?> type = component.getType();
            Type genericType = component.getGenericType();

            constructorParamTypes[i] = type;
            String nestedTextFieldName = Optional.ofNullable(typeMappingRulesForClass)
                    .map(it -> it.renameFromJava().get(name))
                    .orElse(name);
            Object value = sourceMap.get(nestedTextFieldName);

            constructorArgs[i] = convertRecursively(value, type, genericType);
        }

        Constructor<T> constructor = recordClass.getDeclaredConstructor(constructorParamTypes);
        return constructor.newInstance(constructorArgs);
    }

    private <T> T convertMapToPojo(Map<String, Object> sourceMap, Class<T> pojoClass) throws Exception {
        Constructor<T> constructor = pojoClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        T instance = constructor.newInstance();
        TypeMappingRules typeMappingRulesForClass = typeMappingRules.get(pojoClass);

        for (Field field : pojoClass.getDeclaredFields()) {
            if (sourceMap.containsKey(field.getName())) {
                field.setAccessible(true);
                String nestedTextFieldName = Optional.ofNullable(typeMappingRulesForClass)
                        .map(it -> it.renameFromJava().get(field.getName()))
                        .orElse(field.getName());
                Object value = sourceMap.get(nestedTextFieldName);
                Object convertedValue = convertRecursively(value, field.getType(), field.getGenericType());
                field.set(instance, convertedValue);
            }
        }
        return instance;
    }

    @SuppressWarnings("rawtypes")
    private Collection<?> convertListToCollection(List<?> sourceList, Class<? extends Collection> targetClass, Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            throw new DeserializationException("Cannot convert to list without generic type information: " + targetClass.getName());
        }

        // Gets the <T> from List<T>
        // For List<Map<String, String>>, this is a ParameterizedType representing Map<String, String>.
        Type itemGenericType = parameterizedType.getActualTypeArguments()[0];

        Class<?> itemRawClass = getListRawClass(itemGenericType);

        Collection<Object> newCollection;
        if (targetClass.isAssignableFrom(ArrayList.class)) {
            newCollection = new ArrayList<>();
        } else if (targetClass.isAssignableFrom(HashSet.class)) {
            newCollection = new HashSet<>();
        } else {
            throw new DeserializationException("Unsupported collection type: " + targetClass.getName());
        }

        for (Object item : sourceList) {
            newCollection.add(convertRecursively(item, itemRawClass, itemGenericType));
        }
        return newCollection;
    }

    private static Class<?> getListRawClass(Type itemGenericType) {
        if (itemGenericType instanceof Class<?> clazz) {
            // This handles simple cases like List<String>
            return clazz;
        } else if (itemGenericType instanceof ParameterizedType pType) {
            // This handles the more complex case such as List<Map<String, String>>
            // The raw type of Map<String, String> is Map.class.
            return (Class<?>) pType.getRawType();
        }
        throw new DeserializationException("Unsupported generic type in List: " + itemGenericType.getTypeName());
    }

    private Object[] convertListToArray(List<?> sourceList, Class<?> targetArrayClass) {
        Class<?> componentType = targetArrayClass.getComponentType();
        Object[] newArray = (Object[]) Array.newInstance(componentType, sourceList.size());
        for (int i = 0; i < sourceList.size(); i++) {
            newArray[i] = convertRecursively(sourceList.get(i), componentType, null);
        }
        return newArray;
    }
}
