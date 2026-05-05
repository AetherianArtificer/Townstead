package com.aetherianartificer.townstead.spirit;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Incrementally computes spirit snapshots requested by the Spirit page.
 *
 * Runs on the server thread, but spreads large villages over multiple ticks so
 * opening the Spirit page cannot monopolize a tick with a full-village pass.
 */
public final class VillageSpiritQueryScheduler {
    private static final int MAX_BUILDINGS_PER_TICK = 96;
    private static final long MAX_NANOS_PER_TICK = 2_000_000L;

    private static final Queue<Job> QUEUE = new ArrayDeque<>();
    private static final Set<String> PENDING_KEYS = new HashSet<>();

    private VillageSpiritQueryScheduler() {}

    public static void enqueue(ServerLevel level, Village village, ServerPlayer player) {
        if (level == null || village == null || player == null) return;

        VillageSpiritCache.Entry cached = VillageSpiritCache.get(level, village.getId());
        if (cached != null) {
            send(player, VillageSpiritSyncPayload.fromCache(village.getId(), cached));
            return;
        }

        String key = keyOf(level, village.getId(), player.getUUID());
        if (!PENDING_KEYS.add(key)) return;
        QUEUE.add(new Job(key, level, village.getId(), player.getUUID(),
                new ArrayList<>(village.getBuildings().values())));
    }

    public static void tick(MinecraftServer server) {
        if (server == null || QUEUE.isEmpty()) return;
        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        int processed = 0;

        while (!QUEUE.isEmpty() && processed < MAX_BUILDINGS_PER_TICK && System.nanoTime() < deadline) {
            Job job = QUEUE.peek();
            processed += job.process(Math.max(1, MAX_BUILDINGS_PER_TICK - processed));
            if (!job.done()) break;

            QUEUE.poll();
            PENDING_KEYS.remove(job.key);
            complete(server, job);
        }
    }

    public static void clear() {
        QUEUE.clear();
        PENDING_KEYS.clear();
    }

    private static void complete(MinecraftServer server, Job job) {
        SpiritTotals totals = new SpiritTotals(Map.copyOf(job.perSpirit), job.total, job.contributingBuildings);
        SpiritReadout readout = VillageSpiritAggregator.readoutFor(totals);
        VillageSpiritCache.Entry entry = new VillageSpiritCache.Entry(totals, readout);
        VillageSpiritCache.put(job.level, job.villageId, entry);

        ServerPlayer player = server.getPlayerList().getPlayer(job.playerUuid);
        if (player != null) {
            send(player, VillageSpiritSyncPayload.fromCache(job.villageId, entry));
        }
        Townstead.LOGGER.debug("[Spirit] completed queued spirit snapshot village={} buildings={}",
                job.villageId, job.buildings.size());
    }

    private static void send(ServerPlayer player, VillageSpiritSyncPayload payload) {
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static String keyOf(ServerLevel level, int villageId, UUID playerUuid) {
        return level.dimension().location() + "|" + villageId + "|" + playerUuid;
    }

    private static final class Job {
        final String key;
        final ServerLevel level;
        final int villageId;
        final UUID playerUuid;
        final List<Building> buildings;
        final Map<String, Integer> perSpirit = new HashMap<>();
        int cursor = 0;
        int total = 0;
        int contributingBuildings = 0;

        Job(String key, ServerLevel level, int villageId, UUID playerUuid, List<Building> buildings) {
            this.key = key;
            this.level = level;
            this.villageId = villageId;
            this.playerUuid = playerUuid;
            this.buildings = buildings;
        }

        int process(int limit) {
            int processed = 0;
            while (processed < limit && cursor < buildings.size()) {
                process(buildings.get(cursor++));
                processed++;
            }
            return processed;
        }

        boolean done() {
            return cursor >= buildings.size();
        }

        private void process(Building building) {
            if (building == null || !building.isComplete()) return;
            Map<String, Integer> contributions = BuildingSpiritIndex.contributionsFor(building.getType());
            if (contributions.isEmpty()) return;
            boolean anyAdded = false;
            for (Map.Entry<String, Integer> e : contributions.entrySet()) {
                String spirit = e.getKey();
                Integer pts = e.getValue();
                if (pts == null || pts <= 0) continue;
                if (!SpiritRegistry.contains(spirit)) continue;
                perSpirit.merge(spirit, pts, Integer::sum);
                total += pts;
                anyAdded = true;
            }
            if (anyAdded) contributingBuildings++;
        }
    }
}
