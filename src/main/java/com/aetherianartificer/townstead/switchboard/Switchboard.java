package com.aetherianartificer.townstead.switchboard;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Reads server settings with the world's Switchboard values layered over the TOML.
 *
 * <p>Holds the effective overrides for the world this side is running or connected to: the
 * server fills it on start and on every change, clients fill it from the sync packet. A setting
 * with no entry reads straight through to its config value.</p>
 */
public final class Switchboard {
    private Switchboard() {}

    private static volatile Map<Object, Object> active = new IdentityHashMap<>();
    private static volatile Map<String, JsonElement> activeJson = Map.of();
    private static volatile Map<String, Object> content = Map.of();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    /** Runs after every change of the values in effect, on whichever thread applied them. */
    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }

    private static void changed() {
        for (Runnable listener : LISTENERS) listener.run();
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Supplier<T> value) {
        Object override = active.get(value);
        return override != null ? (T) override : value.get();
    }

    /** A content-keyed setting ({@link WorldKeys}) in effect, or its default when nothing sets it. */
    public static Object content(String key) {
        Object v = content.get(key);
        return v != null ? v : WorldKeys.defaultValue(key);
    }

    /** Whether a key names any Switchboard setting, from the config file or from loaded content. */
    public static boolean isKnown(String key) {
        return SettingIndex.get(key) != null || WorldKeys.isKey(key);
    }

    /** The typed value of any Switchboard key, or null when the key is unknown or the value does not fit. */
    public static @Nullable Object parse(String key, @Nullable JsonElement json) {
        SettingIndex.Entry entry = SettingIndex.get(key);
        return entry != null ? SettingIndex.parse(entry, json) : WorldKeys.parse(key, json);
    }

    /** The effective value at a TOML path, or null when the path names no server setting. */
    public static @Nullable Object valueAt(List<String> path) {
        SettingIndex.Entry entry = SettingIndex.get(String.join(".", path));
        if (entry == null) return null;
        try {
            return get(entry.value());
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** The overrides in effect, as JSON, keyed by setting. */
    public static Map<String, JsonElement> overrides() {
        return activeJson;
    }

    /** Replaces every override. Keys that are unknown or values that fail validation are dropped. */
    public static void apply(Map<String, JsonElement> overrides) {
        Map<Object, Object> typed = new IdentityHashMap<>();
        Map<String, Object> keyed = new LinkedHashMap<>();
        Map<String, JsonElement> json = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : overrides.entrySet()) {
            SettingIndex.Entry entry = SettingIndex.get(e.getKey());
            Object value = entry != null ? SettingIndex.parse(entry, e.getValue()) : WorldKeys.parse(e.getKey(), e.getValue());
            if (value == null) continue;
            if (entry != null) typed.put(entry.value(), value);
            else keyed.put(e.getKey(), value);
            json.put(e.getKey(), e.getValue());
        }
        active = typed;
        content = Collections.unmodifiableMap(keyed);
        activeJson = Collections.unmodifiableMap(json);
        changed();
    }

    public static void clear() {
        active = new IdentityHashMap<>();
        content = Map.of();
        activeJson = Map.of();
        changed();
    }
}
