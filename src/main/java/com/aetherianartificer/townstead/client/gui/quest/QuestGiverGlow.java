package com.aetherianartificer.townstead.client.gui.quest;

import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;

/** The givers of the quests the player tracks, outlined for that player alone. */
public final class QuestGiverGlow {
    private static volatile Set<UUID> givers = Set.of();

    private QuestGiverGlow() {}

    static void set(Set<UUID> next) {
        givers = Set.copyOf(next);
    }

    public static boolean glows(Entity entity) {
        return !givers.isEmpty() && givers.contains(entity.getUUID());
    }
}
