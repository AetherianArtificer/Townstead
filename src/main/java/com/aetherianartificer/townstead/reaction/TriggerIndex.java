package com.aetherianartificer.townstead.reaction;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes parsed triggers by {@code (type, key)} so {@link
 * com.aetherianartificer.townstead.reaction.trigger.event event sites} can
 * find matching reactions in O(matches) at fire time. The actual {@code
 * TriggerType} layer that populates this index lands in Phase 4; for now
 * the index simply stores the raw trigger JSON keyed by the reaction's id
 * and provides a single direct lookup by reaction id (used by the debug
 * command). Trigger evaluation in Phase 4 will add type/key indexing.
 */
public final class TriggerIndex {
    public static final TriggerIndex EMPTY = new TriggerIndex(Collections.emptyMap());

    private final Map<String, Map<String, List<ResourceLocation>>> byTypeAndKey;

    private TriggerIndex(Map<String, Map<String, List<ResourceLocation>>> byTypeAndKey) {
        this.byTypeAndKey = byTypeAndKey;
    }

    public List<ResourceLocation> matchesFor(String triggerType, String key) {
        Map<String, List<ResourceLocation>> byKey = byTypeAndKey.get(triggerType);
        if (byKey == null) return List.of();
        List<ResourceLocation> list = byKey.get(key);
        return list == null ? List.of() : list;
    }

    public int size() {
        int total = 0;
        for (Map<String, List<ResourceLocation>> byKey : byTypeAndKey.values()) {
            for (List<ResourceLocation> list : byKey.values()) total += list.size();
        }
        return total;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable accumulator used at reload time. Trigger-type implementations
     * call {@link #add(String, String, ResourceLocation)} with the parsed
     * (type, key) pair plus the reaction id.
     */
    public static final class Builder {
        private final Map<String, Map<String, List<ResourceLocation>>> byTypeAndKey = new HashMap<>();

        public void add(String triggerType, String key, ResourceLocation reactionId) {
            byTypeAndKey.computeIfAbsent(triggerType, t -> new HashMap<>())
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(reactionId);
        }

        public TriggerIndex build() {
            Map<String, Map<String, List<ResourceLocation>>> frozen = new HashMap<>();
            for (Map.Entry<String, Map<String, List<ResourceLocation>>> outer : byTypeAndKey.entrySet()) {
                Map<String, List<ResourceLocation>> inner = new HashMap<>();
                for (Map.Entry<String, List<ResourceLocation>> e : outer.getValue().entrySet()) {
                    inner.put(e.getKey(), List.copyOf(e.getValue()));
                }
                frozen.put(outer.getKey(), Collections.unmodifiableMap(inner));
            }
            return new TriggerIndex(Collections.unmodifiableMap(frozen));
        }
    }
}
