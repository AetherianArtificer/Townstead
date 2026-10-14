package com.aetherianartificer.townstead.root.hazard;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the pathfinder asks about a mob's hazards: whether standing on a block would put it in one,
 * so that block costs as much as other known danger and routes go around it when they reasonably
 * can. Each mob's hazards are refreshed by the gene ticker and lapse shortly after it stops, and
 * answers are cached briefly per block, since one route probes many blocks.
 */
public final class HazardPaths {
    private HazardPaths() {}

    /** Long enough to outlast the ticker's ten-tick cadence with room to spare. */
    private static final long HOLD_TICKS = 30;
    private static final long CACHE_TICKS = 40;
    private static final int CACHE_LIMIT = 4096;

    private static final class Active {
        final List<Hazard> hazards;
        final ConditionContext ctx;
        final long until;
        final Long2BooleanOpenHashMap answers = new Long2BooleanOpenHashMap();
        long answersSince;

        Active(List<Hazard> hazards, ConditionContext ctx, long until, long now) {
            this.hazards = hazards;
            this.ctx = ctx;
            this.until = until;
            this.answersSince = now;
        }
    }

    private static final Map<Mob, Active> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());

    /** Records the hazards routes should skirt for this mob; an empty list clears them. */
    public static void update(Mob mob, List<Hazard> hazards, ConditionContext ctx) {
        List<Hazard> routed = hazards.stream().filter(Hazard::avoidInPaths).toList();
        if (routed.isEmpty()) {
            ACTIVE.remove(mob);
            return;
        }
        long now = mob.level().getGameTime();
        Active previous = ACTIVE.get(mob);
        Active next = new Active(routed, ctx, now + HOLD_TICKS, now);
        // Keep recent answers across refreshes when the hazards are the same ones.
        if (previous != null && previous.hazards.equals(routed) && now - previous.answersSince < CACHE_TICKS) {
            next.answers.putAll(previous.answers);
            next.answersSince = previous.answersSince;
        }
        ACTIVE.put(mob, next);
    }

    public static void clear(Mob mob) {
        ACTIVE.remove(mob);
    }

    /** Whether standing at this block would put the mob in one of its hazards. */
    public static boolean dangerous(Mob mob, int x, int y, int z) {
        if (ACTIVE.isEmpty()) return false;
        Active active = ACTIVE.get(mob);
        if (active == null) return false;
        long now = mob.level().getGameTime();
        if (now > active.until) {
            ACTIVE.remove(mob);
            return false;
        }
        if (now - active.answersSince >= CACHE_TICKS || active.answers.size() >= CACHE_LIMIT) {
            active.answers.clear();
            active.answersSince = now;
        }
        long key = BlockPos.asLong(x, y, z);
        if (active.answers.containsKey(key)) return active.answers.get(key);
        BlockPos pos = new BlockPos(x, y, z);
        boolean danger = false;
        for (Hazard hazard : active.hazards) {
            if (hazard.dangerousAt(active.ctx, pos)) { danger = true; break; }
        }
        active.answers.put(key, danger);
        return danger;
    }
}
