package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.EntityGlowS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * The entities this player's {@code pheno:glow} genes outline for them alone, as the server last
 * sent them. Ids are only meaningful in the level they arrived in, so a level change drops them
 * until the next sync.
 */
public final class GeneEntityGlow {
    private static volatile Set<Integer> ids = Set.of();
    private static volatile Level level;

    private GeneEntityGlow() {}

    public static void apply(EntityGlowS2CPayload payload) {
        Set<Integer> next = new HashSet<>(payload.entityIds().length);
        for (int id : payload.entityIds()) next.add(id);
        level = Minecraft.getInstance().level;
        ids = Set.copyOf(next);
    }

    public static boolean glows(Entity entity) {
        Set<Integer> current = ids;
        return !current.isEmpty() && entity.level() == level && current.contains(entity.getId());
    }
}
