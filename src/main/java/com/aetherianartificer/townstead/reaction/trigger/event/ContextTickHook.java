package com.aetherianartificer.townstead.reaction.trigger.event;

import com.aetherianartificer.townstead.reaction.ReactionDispatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-tick driver for {@code context_enter} and {@code time} triggers.
 * Called from {@code VillagerServerTickDispatcher} on every villager
 * tick, but only does work on a 20-tick stride. On each stride it
 * resolves the villager's current tag set via {@link ContextResolver},
 * diffs against the previous snapshot, and dispatches
 * {@code onContextEnter} for any newly-present tags. The time half of
 * the same stride dispatches {@code onTimePhase} for the current phase.
 */
public final class ContextTickHook {
    private static final int STRIDE = 20;
    private static final WeakHashMap<LivingEntity, Set<String>> LAST_TAGS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private ContextTickHook() {}

    public static void tick(VillagerEntityMCA villager, long gameTime) {
        if (villager == null) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if ((gameTime + (villager.getId() & 0x0F)) % STRIDE != 0) return;

        Set<String> now = ContextResolver.tagsFor(level, villager);
        Set<String> prior;
        Set<String> entered = new HashSet<>();
        synchronized (LOCK) {
            prior = LAST_TAGS.get(villager);
            for (String tag : now) {
                if (prior == null || !prior.contains(tag)) entered.add(tag);
            }
            LAST_TAGS.put(villager, Set.copyOf(now));
        }

        if (!entered.isEmpty()) {
            ReactionDispatcher.onContextEnter(level, villager, entered);
        }
        if (!now.isEmpty()) {
            ReactionDispatcher.onContextPresent(level, villager, now);
        }

        // Time triggers dispatch on the same stride; the dispatcher's
        // per-reaction cooldown plus the trigger's own interval_ticks
        // throttling keep these from spamming.
        String phase = currentPhase(level);
        if (phase != null) {
            ReactionDispatcher.onTimePhase(level, villager, phase);
        }
    }

    private static String currentPhase(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime >= 0L && dayTime < 1500L) return "dawn";
        if (dayTime >= 1500L && dayTime < 12000L) return "day";
        if (dayTime >= 12000L && dayTime < 13500L) return "dusk";
        return "night";
    }
}
