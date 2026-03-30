package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.compat.ModCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EmoteReactionRegistry {
    private static final Map<String, EmoteReactionDefinition> DEFINITIONS = new LinkedHashMap<>();

    private EmoteReactionRegistry() {}

    public static synchronized void clear() {
        DEFINITIONS.clear();
    }

    public static synchronized boolean register(EmoteReactionDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) return false;
        DEFINITIONS.put(normalize(definition.id()), definition);
        return true;
    }

    public static synchronized Collection<EmoteReactionDefinition> all() {
        return new ArrayList<>(DEFINITIONS.values());
    }

    public static synchronized EmoteReactionDefinition findMatch(Set<String> aliases) {
        if (aliases == null || aliases.isEmpty()) return null;
        for (EmoteReactionDefinition definition : DEFINITIONS.values()) {
            if (definition.requiredMod() != null
                    && !definition.requiredMod().isBlank()
                    && !ModCompat.isLoaded(definition.requiredMod())) {
                continue;
            }
            for (String alias : aliases) {
                if (definition.triggerEmotes().contains(normalize(alias))) {
                    return definition;
                }
            }
        }
        return null;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
