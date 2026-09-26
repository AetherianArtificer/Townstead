package com.aetherianartificer.townstead.switchboard;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Switchboard settings that are keyed by loaded content rather than defined in the config file:
 * who can have each Root, how often it spawns, the same for whole species, ancestries, lineages and
 * packs, and the same for cultures. A key names content by id, so a key for content that is not
 * loaded is kept and simply does nothing.
 */
public final class WorldKeys {
    private WorldKeys() {}

    public static final String ROOT_STATE = "roots.state.";
    public static final String ROOT_RATE = "roots.rate.";
    public static final String GROUP_ON = "roots.group.";
    public static final String GROUP_RATE = "roots.groupRate.";
    public static final String CULTURE_ON = "cultures.enabled.";
    public static final String CULTURE_RATE = "cultures.rate.";

    /** How a Root's content can be grouped; the group key is {@code <dimension>.<id>}. */
    public static final List<String> DIMENSIONS = List.of("species", "ancestry", "lineage", "pack");

    /**
     * Who may have a Root. Discoverable Roots spawn as villagers; players can choose one once
     * someone on the server befriends a villager of it.
     */
    public enum RootState { EVERYONE, VILLAGERS, PLAYERS, DISCOVERABLE, OFF }

    /** The spawn rate steps a slider offers. Normal is 1. */
    public static final double[] RATES = {0.1, 0.25, 0.5, 0.75, 1, 1.5, 2, 3, 5};
    private static final double MAX_RATE = 10;

    public static String rootState(String rootId) { return ROOT_STATE + rootId; }
    public static String rootRate(String rootId) { return ROOT_RATE + rootId; }
    public static String groupOn(String dimension, String id) { return GROUP_ON + dimension + "." + id; }
    public static String groupRate(String dimension, String id) { return GROUP_RATE + dimension + "." + id; }
    public static String cultureOn(String cultureId) { return CULTURE_ON + cultureId; }
    public static String cultureRate(String cultureId) { return CULTURE_RATE + cultureId; }

    public static boolean isKey(String key) {
        return key != null && (key.startsWith(ROOT_STATE) || key.startsWith(ROOT_RATE)
                || groupDimension(key) != null || key.startsWith(CULTURE_ON) || key.startsWith(CULTURE_RATE));
    }

    /** The value a key has when nothing sets it. */
    public static @Nullable Object defaultValue(String key) {
        if (key.startsWith(ROOT_STATE)) return RootState.EVERYONE;
        if (key.startsWith(GROUP_ON) || key.startsWith(CULTURE_ON)) return Boolean.TRUE;
        if (key.startsWith(ROOT_RATE) || key.startsWith(GROUP_RATE) || key.startsWith(CULTURE_RATE)) return 1.0;
        return null;
    }

    public static @Nullable Object parse(String key, @Nullable JsonElement json) {
        if (!isKey(key) || json == null || !json.isJsonPrimitive()) return null;
        try {
            if (key.startsWith(ROOT_STATE)) {
                return RootState.valueOf(json.getAsString().trim().toUpperCase(Locale.ROOT));
            }
            if (key.startsWith(GROUP_ON) || key.startsWith(CULTURE_ON)) {
                return json.getAsJsonPrimitive().isBoolean() ? json.getAsBoolean() : null;
            }
            double rate = json.getAsDouble();
            return rate > 0 && rate <= MAX_RATE ? rate : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The dimension a group key names, or null when it is not a group key. */
    public static @Nullable String groupDimension(String key) {
        String rest = key.startsWith(GROUP_ON) ? key.substring(GROUP_ON.length())
                : key.startsWith(GROUP_RATE) ? key.substring(GROUP_RATE.length()) : null;
        if (rest == null) return null;
        int dot = rest.indexOf('.');
        if (dot <= 0) return null;
        String dimension = rest.substring(0, dot);
        return DIMENSIONS.contains(dimension) && dot < rest.length() - 1 ? dimension : null;
    }

    /** The content id a key names: the Root, culture, or group member after the prefix. */
    public static String subject(String key) {
        String dimension = groupDimension(key);
        if (dimension != null) {
            String prefix = key.startsWith(GROUP_ON) ? GROUP_ON : GROUP_RATE;
            return key.substring(prefix.length() + dimension.length() + 1);
        }
        for (String prefix : List.of(ROOT_STATE, ROOT_RATE, CULTURE_ON, CULTURE_RATE)) {
            if (key.startsWith(prefix)) return key.substring(prefix.length());
        }
        return key;
    }
}
