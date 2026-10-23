package com.aetherianartificer.townstead.chronicle.template;

import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.TriggerKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trigger key → candidate templates, rebuilt on data-pack load. Taps carry
 * semantic keys, so a no-match tap costs one map miss.
 */
public final class ChronicleTriggerIndex {

    /** Trigger type for Minecraft's own {@code GameEvent}s, keyed by their registry id. */
    public static final String TYPE_GAME = "game";

    private static volatile Map<TriggerKey, List<ChronicleEventTemplate>> INDEX = Map.of();
    private static volatile Set<String> WATCHED_GAME_EVENTS = Set.of();
    private static volatile Set<net.minecraft.resources.ResourceLocation> WATCHED_IDS = Set.of();

    private ChronicleTriggerIndex() {}

    static void rebuild(Collection<ChronicleEventTemplate> templates) {
        Map<TriggerKey, List<ChronicleEventTemplate>> index = new HashMap<>();
        Set<String> watched = new HashSet<>();
        for (ChronicleEventTemplate template : templates) {
            if (template.trigger() == null || template.trigger().key().isEmpty()) continue;
            index.computeIfAbsent(template.trigger(), ignored -> new ArrayList<>()).add(template);
            if (TYPE_GAME.equals(template.trigger().type())) {
                watched.add(template.trigger().key());
            }
        }
        index.replaceAll((key, list) -> List.copyOf(list));
        INDEX = Map.copyOf(index);
        WATCHED_GAME_EVENTS = Set.copyOf(watched);
        Set<net.minecraft.resources.ResourceLocation> ids = new HashSet<>();
        for (String key : watched) {
            net.minecraft.resources.ResourceLocation id =
                    com.aetherianartificer.townstead.data.DataPackLang.parseId(key);
            if (id != null) ids.add(id);
        }
        WATCHED_IDS = Set.copyOf(ids);
    }

    /**
     * The game events some template listens for. Vanilla fires these constantly
     * (every step, every splash), so the bridge tests this set first, on the
     * registry id itself: an event nobody wrote a story about costs one hash
     * lookup and allocates nothing.
     */
    public static boolean watchesGameEvent(net.minecraft.resources.ResourceLocation id) {
        return WATCHED_IDS.contains(id);
    }

    public static Set<String> watchedGameEvents() {
        return WATCHED_GAME_EVENTS;
    }

    public static List<ChronicleEventTemplate> candidates(TriggerKey trigger) {
        return INDEX.getOrDefault(trigger, List.of());
    }

    public static boolean isEmpty() {
        return INDEX.isEmpty();
    }
}
