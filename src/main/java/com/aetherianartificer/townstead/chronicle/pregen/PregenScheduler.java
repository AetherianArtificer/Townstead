package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Defers pre-history generation off the stamping path: one village per server
 * tick, residents gathered from loaded villagers around the anchor. Dedup by
 * village key so re-stamps never regenerate.
 */
public final class PregenScheduler {

    private record Job(VillageKey key, long birthDay, boolean playerFounded, BlockPos anchor) {}

    private static final double RESIDENT_RADIUS = 64.0;

    private static final Queue<Job> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<VillageKey> SEEN = ConcurrentHashMap.newKeySet();

    private PregenScheduler() {}

    public static void schedule(VillageKey key, long birthDay, boolean playerFounded, BlockPos anchor) {
        if (!SEEN.add(key)) return;
        QUEUE.add(new Job(key, birthDay, playerFounded, anchor));
    }

    /** Admin reroll support: forget the dedupe mark so a village can regenerate. */
    public static void forget(VillageKey key) {
        SEEN.remove(key);
    }

    public static void tick(MinecraftServer server) {
        Job job = QUEUE.poll();
        if (job == null) return;
        if (ChronicleEventRegistry.isEmpty()) return;
        try {
            ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, job.key().dimension()));
            if (level == null) return;
            List<VillagerEntityMCA> residents = new ArrayList<>();
            AABB box = new AABB(job.anchor()).inflate(RESIDENT_RADIUS);
            for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, box)) {
                if (ChronicleEmitter.resolveVillageId(villager) == job.key().villageId()) {
                    residents.add(villager);
                }
            }
            ChroniclePregen.generate(server, job.key(), job.birthDay(), job.playerFounded(), residents);
            Townstead.LOGGER.debug("[Chronicles] Pre-generated history for village {} ({} residents seen)",
                    job.key().villageId(), residents.size());
        } catch (Throwable t) {
            Townstead.LOGGER.warn("[Chronicles] Pre-gen failed for village {}", job.key().villageId(), t);
        }
    }

    public static int queued() {
        return QUEUE.size();
    }

    public static void clearAll() {
        QUEUE.clear();
        SEEN.clear();
    }
}
