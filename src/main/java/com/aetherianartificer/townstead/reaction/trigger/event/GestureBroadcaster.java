package com.aetherianartificer.townstead.reaction.trigger.event;

import com.aetherianartificer.townstead.reaction.ReactionDispatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Broadcasts a gesture event around any entity that just played an
 * emote. Used by command and packet entry points so that a player
 * running {@code /townstead emote play <id>} (with no target, or
 * targeting themselves) causes nearby villagers to react as if they
 * saw the wave.
 *
 * <p>Excludes the source entity itself when it's a villager so a
 * forcibly-emoted villager doesn't loop back into their own reaction.
 * Depth is always 0 here — the {@link ReactionDispatcher} mirror
 * propagation runs at depth 1, never re-entering this broadcaster.</p>
 */
public final class GestureBroadcaster {
    private static final double DEFAULT_RADIUS = 6.0;

    private GestureBroadcaster() {}

    public static void broadcast(ServerLevel level, Entity source, String emoteName) {
        broadcast(level, source, emoteName, DEFAULT_RADIUS);
    }

    public static void broadcast(ServerLevel level, Entity source, String emoteName, double radius) {
        if (level == null || source == null || emoteName == null || emoteName.isBlank()) return;
        if (radius <= 0) return;
        Player playerCause = source instanceof Player p ? p : null;
        AABB box = source.getBoundingBox().inflate(radius);
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, box)) {
            if (villager == source) continue;
            ReactionDispatcher.onGesture(level, playerCause, villager, emoteName, 0);
        }
    }
}
