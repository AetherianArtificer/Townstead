package com.aetherianartificer.townstead.reaction.trigger.event;

import com.aetherianartificer.townstead.reaction.Reaction;
import com.aetherianartificer.townstead.reaction.ReactionContext;
import com.aetherianartificer.townstead.reaction.ReactionDispatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * After a reaction successfully plays an emote, broadcast a synthetic
 * gesture event for that emote on nearby villagers. Mirrored reactions
 * fire at {@code mirrorDepth=1} and won't re-propagate, so the fan-out
 * is bounded to one hop regardless of how many gesture triggers match.
 *
 * <p>Unifies the old {@code mirror_of} concept into the single
 * {@code gesture} trigger: from a villager's perspective there's no
 * difference between "a player waved" and "another villager just
 * played the wave reaction" — both are nearby waves.</p>
 */
public final class MirrorPropagator {
    private MirrorPropagator() {}

    public static void propagate(ServerLevel level, LivingEntity source, Reaction reaction, String emoteName,
            ReactionContext sourceContext) {
        if (level == null || source == null || reaction == null || emoteName == null) return;
        if (reaction.mirrorRadius() <= 0) return;
        if (sourceContext.mirrorDepth() != 0) return;

        double radius = reaction.mirrorRadius();
        AABB box = source.getBoundingBox().inflate(radius);
        float mirrorChance = reaction.mirrorChance();
        var rand = level.getRandom();
        for (VillagerEntityMCA neighbor : level.getEntitiesOfClass(VillagerEntityMCA.class, box)) {
            if (neighbor == source) continue;
            if (mirrorChance < 1.0F && rand.nextFloat() >= mirrorChance) continue;
            ReactionDispatcher.onGesture(level, null, neighbor, emoteName, 1);
        }
    }
}
