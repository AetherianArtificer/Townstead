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

        long t0 = System.nanoTime();
        VillageSpiritCache.Entry cached = VillageSpiritCache.get(level, village.getId());
        if (cached != null) {
            send(player, VillageSpiritSyncPayload.fromCache(village.getId(), cached));
            Townstead.LOGGER.info("[TS-Diag/SpiritQ] enqueue cacheHit village={} player={} elapsedUs={}",
                    village.getId(), player.getName().getString(), (System.nanoTime() - t0) / 1_000L);
            return;
        }

        String key = keyOf(level, village.getId(), player.getUUID());
        if (!PENDING_KEYS.add(key)) {
            Townstead.LOGGER.info("[TS-Diag/SpiritQ] enqueue dedup village={} player={} queueDepth={}",
                    village.getId(), player.getName().getString(), QUEUE.size());
            return;
        }
        int buildings = village.getBuildings().size();
        QUEUE.add(new Job(key, level, village.getId(), player.getUUID(),
                new ArrayList<>(village.getBuildings().values()), System.nanoTime()));
        Townstead.LOGGER.info("[TS-Diag/SpiritQ] enqueue queued village={} player={} buildings={} queueDepth={} setupUs={}",
                village.getId(), player.getName().getString(), buildings, QUEUE.size(),
                (System.nanoTime() - t0) / 1_000L);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || QUEUE.isEmpty()) return;
        long tickStart = System.nanoTime();
        long deadline = tickStart + MAX_NANOS_PER_TICK;
        int processed = 0;
        int jobsCompleted = 0;
        int queueDepthBefore = QUEUE.size();

        while (!QUEUE.isEmpty() && processed < MAX_BUILDINGS_PER_TICK && System.nanoTime() < deadline) {
            Job job = QUEUE.peek();
            processed += job.process(Math.max(1, MAX_BUILDINGS_PER_TICK - processed));
            if (!job.done()) break;

            QUEUE.poll();
            PENDING_KEYS.remove(job.key);
            complete(server, job);
            jobsCompleted++;
        }
        long elapsed = System.nanoTime() - tickStart;
        // Only log when we actually did work — silent ticks would flood logs.
        Townstead.LOGGER.info("[TS-Diag/SpiritQ] tick buildingsProcessed={} jobsCompleted={} queueBefore={} queueAfter={} elapsedUs={} budgetUs={}",
                processed, jobsCompleted, queueDepthBefore, QUEUE.size(),
                elapsed / 1_000L, MAX_NANOS_PER_TICK / 1_000L);
    }

    public static void clear() {
        QUEUE.clear();
        PENDING_KEYS.clear();
    }

    private static void complete(MinecraftServer server, Job job) {
        long t0 = System.nanoTime();
        SpiritTotals totals = new SpiritTotals(Map.copyOf(job.perSpirit), job.total, job.contributingBuildings);
        SpiritReadout readout = VillageSpiritAggregator.readoutFor(totals);
        java.util.Map<String, java.util.List<ContributorRow>> contributors = job.buildContributors();
        VillageSpiritCache.Entry entry = new VillageSpiritCache.Entry(totals, readout, contributors);
        VillageSpiritCache.put(job.level, job.villageId, entry);

        ServerPlayer player = server.getPlayerList().getPlayer(job.playerUuid);
        boolean delivered = false;
        if (player != null) {
            send(player, VillageSpiritSyncPayload.fromCache(job.villageId, entry));
            delivered = true;
        }
        long latency = System.nanoTime() - job.enqueuedAtNanos;
        long completeTime = System.nanoTime() - t0;
        Townstead.LOGGER.info("[TS-Diag/SpiritQ] complete village={} buildings={} contributing={} totalPts={} spirits={} latencyMs={} completeUs={} delivered={}",
                job.villageId, job.buildings.size(), job.contributingBuildings, job.total,
                job.perSpirit.size(), latency / 1_000_000L, completeTime / 1_000L, delivered);
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
        // spirit id -> (buildingType -> [count, totalPoints]). Aggregated as
        // we walk the building list so the Spirit page's contributor view can
        // be served from the cache without re-iterating.
        final Map<String, Map<String, int[]>> contributors = new HashMap<>();
        final long enqueuedAtNanos;
        int cursor = 0;
        int total = 0;
        int contributingBuildings = 0;

        Job(String key, ServerLevel level, int villageId, UUID playerUuid,
                List<Building> buildings, long enqueuedAtNanos) {
            this.key = key;
            this.level = level;
            this.villageId = villageId;
            this.playerUuid = playerUuid;
            this.buildings = buildings;
            this.enqueuedAtNanos = enqueuedAtNanos;
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
            String type = building.getType();
            Map<String, Integer> contributions = BuildingSpiritIndex.contributionsFor(type);
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
                int[] agg = contributors
                        .computeIfAbsent(spirit, k -> new HashMap<>())
                        .computeIfAbsent(type, k -> new int[]{0, 0});
                agg[0]++;
                agg[1] += pts;
            }
            if (anyAdded) contributingBuildings++;
        }

        Map<String, java.util.List<ContributorRow>> buildContributors() {
            if (contributors.isEmpty()) return Map.of();
            Map<String, java.util.List<ContributorRow>> out = new HashMap<>();
            for (Map.Entry<String, Map<String, int[]>> spiritEntry : contributors.entrySet()) {
                java.util.List<ContributorRow> rows = new java.util.ArrayList<>(spiritEntry.getValue().size());
                for (Map.Entry<String, int[]> typeEntry : spiritEntry.getValue().entrySet()) {
                    rows.add(new ContributorRow(typeEntry.getKey(),
                            typeEntry.getValue()[0], typeEntry.getValue()[1]));
                }
                rows.sort(java.util.Comparator.comparingInt(ContributorRow::points).reversed());
                out.put(spiritEntry.getKey(), java.util.List.copyOf(rows));
            }
            return Map.copyOf(out);
        }
    }
}
