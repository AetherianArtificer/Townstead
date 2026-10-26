package com.aetherianartificer.townstead.work.feedback;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Data-authored village-life dialogue subjects supplied by recognized building types. */
public final class BuildingDialogueTopics {
    private static volatile Map<String, Set<String>> BY_BUILDING = Map.of();

    private BuildingDialogueTopics() {}

    public static void replaceAll(Map<String, Set<String>> topics) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        if (topics != null) {
            topics.forEach((building, values) -> {
                if (building != null && values != null && !values.isEmpty()) {
                    copy.put(building, Set.copyOf(values));
                }
            });
        }
        BY_BUILDING = Map.copyOf(copy);
    }

    public static boolean matches(String buildingType, String topic) {
        if (buildingType == null || topic == null) return false;
        Set<String> topics = BY_BUILDING.get(buildingType);
        return topics != null && topics.contains(topic);
    }
}
