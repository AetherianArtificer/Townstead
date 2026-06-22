package com.aetherianartificer.townstead.api;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

/**
 * Small path resolver over Townstead snapshot records.
 *
 * <p>Paths are dot-separated record/map/list lookups, for example
 * {@code originId}, {@code needs.hunger}, or {@code variants.0.id}.</p>
 */
public final class TownsteadQuery {
    private TownsteadQuery() {}

    public static Object resolve(Object root, String path) {
        if (root == null || path == null || path.isBlank()) return root;
        Object value = root;
        for (String segment : path.split("\\.")) {
            if (segment.isBlank()) continue;
            value = step(value, segment);
            if (value == null) return null;
        }
        return value;
    }

    public static int resultValue(Object value) {
        if (value instanceof Boolean b) return b ? 1 : 0;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof CharSequence s) return s.length();
        if (value instanceof List<?> l) return l.size();
        if (value instanceof Map<?, ?> m) return m.size();
        return value == null ? 0 : 1;
    }

    public static String render(Object value) {
        if (value == null) return "<missing>";
        if (value instanceof Map<?, ?> || value instanceof List<?>) return value.toString();
        return String.valueOf(value);
    }

    private static Object step(Object value, String segment) {
        if (value instanceof Map<?, ?> map) return map.get(segment);
        if (value instanceof List<?> list) {
            try {
                int index = Integer.parseInt(segment);
                return index >= 0 && index < list.size() ? list.get(index) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Class<?> type = value.getClass();
        if (!type.isRecord()) return null;
        for (RecordComponent component : type.getRecordComponents()) {
            if (!component.getName().equals(segment)) continue;
            try {
                return component.getAccessor().invoke(value);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }
}
