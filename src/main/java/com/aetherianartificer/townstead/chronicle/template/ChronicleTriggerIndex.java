package com.aetherianartificer.townstead.chronicle.template;

import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.TriggerKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trigger key → candidate templates, rebuilt on data-pack load. Taps carry
 * semantic keys, so a no-match tap costs one map miss.
 */
public final class ChronicleTriggerIndex {

    private static volatile Map<TriggerKey, List<ChronicleEventTemplate>> INDEX = Map.of();

    private ChronicleTriggerIndex() {}

    static void rebuild(Collection<ChronicleEventTemplate> templates) {
        Map<TriggerKey, List<ChronicleEventTemplate>> index = new HashMap<>();
        for (ChronicleEventTemplate template : templates) {
            if (template.trigger() == null || template.trigger().key().isEmpty()) continue;
            index.computeIfAbsent(template.trigger(), ignored -> new ArrayList<>()).add(template);
        }
        index.replaceAll((key, list) -> List.copyOf(list));
        INDEX = Map.copyOf(index);
    }

    public static List<ChronicleEventTemplate> candidates(TriggerKey trigger) {
        return INDEX.getOrDefault(trigger, List.of());
    }

    public static boolean isEmpty() {
        return INDEX.isEmpty();
    }
}
