package org.loxlylabs.nestedtext;

import java.lang.reflect.Array;
import java.util.*;

class NestedTextSerializer {

    private final Map<Class<?>, Serializer<?>> serializers = new HashMap<>();
    private final DumpOptions options;
    private final Map<Class<?>, TypeMappingRules> typeMappingRules;

    public NestedTextSerializer(DumpOptions options, Map<Class<?>, Serializer<?>> serializers, Map<Class<?>, TypeMappingRules> typeMappingRules) {
        this.options = options;
        this.serializers.putAll(serializers);
        this.typeMappingRules = typeMappingRules;
    }

    public String serialize(Object obj) {
        StringBuilder sb = new StringBuilder();
        obj = toNestedTextCompatible(obj);
        dumpValue(obj, sb, 0);
        // internal dump methods always add line separator
        removeLastLineSeparator(sb);
        return sb.toString();
    }

    /**
     * Efficiently removes the last line separator.
     *
     * @param sb StringBuilder to remove last line separator from.
     */
    private void removeLastLineSeparator(StringBuilder sb) {
        // Be safe even if we assume it always ends in eol
        if (sb.length() >= options.eol().length()) {
            // avoid expensive substring since string may be large
            int startOfSeparator = sb.length() - options.eol().length();

            for (int i = 0; i < options.eol().length(); i++) {
                if (sb.charAt(startOfSeparator + i) != options.eol().charAt(i)) {
                    // does not end with separator, just return
                    return;
                }
            }
            sb.setLength(startOfSeparator);
        }
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
                if (serializers.containsKey(o.getClass())) {
                    yield applyAdapter(o.getClass(), o);
                } else if (o.getClass().isArray()) {
                    List<Object> list = new ArrayList<>();
                    int length = Array.getLength(o);
                    for (int i = 0; i < length; i++) {
                        list.add(toNestedTextCompatible(Array.get(o, i)));
                    }
                    yield list;
                } else if (o.getClass().isRecord()) {
                    TypeMappingRules typeMappingRulesForClass = typeMappingRules.get(o.getClass());
                    Map<String,Object> map = new LinkedHashMap<>();
                    for (var comp : o.getClass().getRecordComponents()) {
                        String fieldName = comp.getName();
                        // ignore field during serialization
                        if (typeMappingRulesForClass != null) {
                            if (typeMappingRulesForClass.fieldsToIgnore().contains(comp.getName())) {
                                continue;
                            }
                            // rename field when writing NestedText
                            fieldName = typeMappingRulesForClass.renameFromJava().getOrDefault(fieldName, fieldName);
                        }
                        try {
                            Object value = comp.getAccessor().invoke(o);
                            if (typeMappingRulesForClass != null) {
                                // ignore field when null
                                if (value == null && typeMappingRulesForClass.fieldsToIgnoreWhenNull().contains(fieldName)) {
                                    continue;
                                }
                            }
                            map.put(fieldName, toNestedTextCompatible(value));
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
                    TypeMappingRules typeMappingRulesForClass = typeMappingRules.get(o.getClass());
                    for (var field : o.getClass().getDeclaredFields()) {
                        try {
                            field.setAccessible(true);
                            String fieldName = field.getName();
                            // ignore field during serialization
                            if (typeMappingRulesForClass != null) {
                                if (typeMappingRulesForClass.fieldsToIgnore().contains(fieldName)) {
                                    continue;
                                }
                                // rename field when writing NestedText
                                fieldName = typeMappingRulesForClass.renameFromJava().getOrDefault(fieldName, fieldName);
                            }
                            Object value = field.get(o);
                            if (typeMappingRulesForClass != null) {
                                // ignore field when null
                                if (value == null && typeMappingRulesForClass.fieldsToIgnoreWhenNull().contains(fieldName)) {
                                    continue;
                                }
                            }
                            map.put(fieldName, toNestedTextCompatible(value));
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
        if (s.contains(options.eol())) {
            String[] lines = s.split(options.eol(), -1);
            for (String line : lines) {
                sb.append(indentStr)
                        .append(">");
                if (!line.isEmpty()) {
                    sb.append(" ");
                }
                sb.append(line).append(options.eol());
            }
        } else {
            if (indent == 0) {
                sb.append("> ");
            }
            sb.append(indentStr)
                    .append(s)
                    .append(options.eol());
        }
    }

    private void dumpMap(Map<?,?> m, StringBuilder sb, int indent) {
        String indentStr = " ".repeat(indent);
        for (var entry : m.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            // Single line: key: value
            if (value instanceof String s && !s.contains(options.eol())) {
                sb.append(indentStr)
                        .append(key)
                        .append(": ")
                        .append(s)
                        .append(options.eol());
            } else {
                sb.append(indentStr)
                        .append(key)
                        .append(":")
                        .append(options.eol());
                dumpValue(value, sb, indent + options.indentAmount());
            }
        }
    }

    private void dumpList(Collection<?> collection, StringBuilder sb, int indent) {
        String indentStr = " ".repeat(indent);
        for (var item : collection) {
            if (item instanceof String s && !s.contains(options.eol())) {
                sb.append(indentStr)
                        .append("- ")
                        .append(s)
                        .append(options.eol());
            } else {
                sb.append(indentStr)
                        .append("-")
                        .append(options.eol());
                dumpValue(item, sb, indent + options.indentAmount());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Object applyAdapter(Class<T> type, Object value) {
        Serializer<T> serializer = (Serializer<T>) serializers.get(type);
        return serializer.serialize((T) value);
    }
}
