package com.aetherianartificer.townstead.journey;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A villager making the rounds: they walk to residents of their village one at a time, nearest
 * first, and stop with each for a moment before going on to the next.
 */
public final class Rounds {
    private static final int TICK = 10;
    private static final int PAUSE = 60;
    private static final double CLOSE = 2.5;
    private static final Map<VillagerEntityMCA, Round> ACTIVE = new WeakHashMap<>();

    private static final class Round {
        final Deque<UUID> left;
        UUID current;
        int paused;
        long giveUpAt;

        Round(Deque<UUID> left) {
            this.left = left;
        }
    }

    private Rounds() {}

    /** Starts {@code villager} on a round of up to {@code max} residents. False when there is nobody to see. */
    public static boolean start(VillagerEntityMCA villager, int max) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        Village village = villager.getResidency().getHomeVillage()
                .or(() -> Village.findNearest(villager)).orElse(null);
        if (village == null) return false;
        List<VillagerEntityMCA> residents = village.getResidents(level).stream()
                .filter(v -> v != villager && v.isAlive())
                .sorted(Comparator.comparingDouble(v -> v.distanceToSqr(villager)))
                .limit(Math.max(1, max))
                .toList();
        if (residents.isEmpty()) return false;
        Deque<UUID> left = new ArrayDeque<>();
        residents.forEach(v -> left.add(v.getUUID()));
        synchronized (ACTIVE) {
            ACTIVE.put(villager, new Round(left));
        }
        return true;
    }

    public static boolean onRounds(VillagerEntityMCA villager) {
        synchronized (ACTIVE) {
            return ACTIVE.containsKey(villager);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % TICK != 0) return;
        synchronized (ACTIVE) {
            if (ACTIVE.isEmpty()) return;
            Iterator<Map.Entry<VillagerEntityMCA, Round>> it = ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<VillagerEntityMCA, Round> entry = it.next();
                VillagerEntityMCA villager = entry.getKey();
                Round round = entry.getValue();
                if (villager.isRemoved() || !villager.isAlive() || !(villager.level() instanceof ServerLevel level)) {
                    it.remove();
                    continue;
                }
                Entity target = round.current == null ? null : level.getEntity(round.current);
                if (target == null || !target.isAlive() || level.getGameTime() > round.giveUpAt) {
                    round.current = round.left.poll();
                    round.paused = 0;
                    round.giveUpAt = level.getGameTime() + 20 * 45;
                    if (round.current == null) {
                        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                        it.remove();
                    }
                    continue;
                }
                villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
                if (villager.distanceTo(target) > CLOSE) {
                    villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5f, 2));
                    continue;
                }
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                round.paused += TICK;
                if (round.paused >= PAUSE) round.giveUpAt = 0;
            }
        }
    }
}
