package com.aetherianartificer.townstead.client.animation.cem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class CemVariableStore {
    private final Map<String, Double> values = new HashMap<>();
    private final Set<String> assigned = new HashSet<>();

    void clearAssignments() {
        assigned.clear();
    }

    double get(String key) {
        return values.getOrDefault(normalize(key), 0.0D);
    }

    void seed(String key, double value) {
        values.put(normalize(key), sanitize(value));
    }

    void set(String key, double value) {
        String normalized = normalize(key);
        values.put(normalized, sanitize(value));
        assigned.add(normalized);
    }

    boolean wasAssigned(String key) {
        return assigned.contains(normalize(key));
    }

    private static String normalize(String key) {
        if (key.startsWith("varb.")) return key.substring(5).toLowerCase(Locale.ROOT);
        if (key.startsWith("var.")) return key.substring(4).toLowerCase(Locale.ROOT);
        return key.toLowerCase(Locale.ROOT);
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
