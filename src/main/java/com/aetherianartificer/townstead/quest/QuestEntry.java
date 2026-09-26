package com.aetherianartificer.townstead.quest;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable provider-neutral quest data consumed by the screen and filtering engine. */
public record QuestEntry(
        String providerId,
        String providerName,
        String id,
        String title,
        String description,
        String group,
        String iconItemId,
        QuestState state,
        List<QuestObjective> objectives,
        List<QuestReward> rewards,
        Set<QuestCapability> capabilities,
        boolean tracked,
        boolean pinned,
        List<String> tags,
        String metadata
) {
    public QuestEntry {
        providerId = normalized(providerId, "unknown");
        providerName = normalized(providerName, providerId);
        id = normalized(id, "unknown");
        title = normalized(title, id);
        description = Objects.requireNonNullElse(description, "");
        group = normalized(group, providerName);
        iconItemId = normalized(iconItemId, "minecraft:paper");
        state = Objects.requireNonNullElse(state, QuestState.AVAILABLE);
        objectives = List.copyOf(Objects.requireNonNullElse(objectives, List.of()));
        rewards = List.copyOf(Objects.requireNonNullElse(rewards, List.of()));
        capabilities = Set.copyOf(Objects.requireNonNullElse(capabilities, Set.of()));
        tags = List.copyOf(Objects.requireNonNullElse(tags, List.of()));
        metadata = Objects.requireNonNullElse(metadata, "");
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public String key() {
        return providerId + ":" + id;
    }

    public long completedObjectives() {
        return objectives.stream().filter(QuestObjective::done).count();
    }

    public QuestEntry withPinned(boolean value) {
        return new QuestEntry(providerId, providerName, id, title, description, group, iconItemId,
                state, objectives, rewards, capabilities, tracked, value, tags, metadata);
    }

    public QuestEntry withTracked(boolean value) {
        return new QuestEntry(providerId, providerName, id, title, description, group, iconItemId,
                state, objectives, rewards, capabilities, value, pinned, tags, metadata);
    }
}
