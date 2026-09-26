package com.aetherianartificer.townstead.client.animation.cem;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

final class CemVariableStore {
    /** Shared by a program; expression names are resolved once when the pack is parsed. */
    static final class Layout {
        private final Map<String, Integer> slots = new HashMap<>();
        private final Set<String> references = new HashSet<>();

        int reference(String key) {
            references.add(normalize(key));
            return slot(key);
        }

        boolean references(String key) {
            return references.contains(normalize(key));
        }

        int slot(String key) {
            return slots.computeIfAbsent(normalize(key), ignored -> slots.size());
        }
    }

    private final Layout layout;
    private double[] values;
    private final BitSet assigned = new BitSet();

    CemVariableStore(Layout layout) {
        this.layout = layout;
        values = new double[Math.max(256, layout.slots.size())];
    }

    boolean references(String key) {
        return layout.references.contains(key);
    }

    void clearAssignments() {
        assigned.clear();
    }

    double get(String key) {
        return get(layout.slot(key));
    }

    double get(int slot) {
        return slot < values.length ? values[slot] : 0.0D;
    }

    void seed(String key, double value) {
        put(layout.slot(key), value);
    }

    void seed(int slot, double value) {
        put(slot, value);
    }

    void set(String key, double value) {
        set(layout.slot(key), value);
    }

    void set(int slot, double value) {
        put(slot, value);
        assigned.set(slot);
    }

    private void put(int slot, double value) {
        if (slot >= values.length) values = Arrays.copyOf(values, Math.max(slot + 1, values.length * 2));
        values[slot] = sanitize(value);
    }

    boolean wasAssigned(String key) {
        return assigned.get(layout.slot(key));
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
