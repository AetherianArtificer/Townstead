package com.aetherianartificer.townstead.chronicle.emit;

import com.aetherianartificer.townstead.building.BuildingCells;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.TriggerKey;
import com.aetherianartificer.townstead.chronicle.template.ChronicleTriggerIndex;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Fire in a village, as two chronicle moments: {@code townstead:building_burned} when a fire catches in one of its buildings,
 * for whoever lives or works nearest, and {@code townstead:fire_seen} when a fire grows big in the village, for those who saw
 * it. Fire is any block in {@code #townstead:fires}: vanilla's, and Burnt's when it's in. Each building that catches is also
 * posted as the public API's {@code VillageFireEvent}.
 *
 * <p>Fire catches on the block-change path, so this only notes it there and does the rest on the next server tick.</p>
 */
public final class FireWatcher {

    public static final TagKey<Block> FIRES = TagKey.create(Registries.BLOCK, ResourceLocation.tryParse("townstead:fires"));
    public static final String BUILDING_BURNED = "townstead:building_burned", FIRE_SEEN = "townstead:fire_seen";
    public static final String TRIGGER = "world";

    private record Seen(ServerLevel level, BlockPos pos) {}


    // Fires a village has to see within this many ticks to be a big fire; how near a villager is to have seen it; and the
    // most fires looked at a tick.
    private static final int BIG = 6, MOST_A_TICK = 32;
    private static final long WINDOW = 400L, ONCE_A_DAY = 24000L;
    private static final double SAW_IT = 24.0;

    private static final Queue<Seen> PENDING = new ConcurrentLinkedQueue<>();
    private static final Map<String, long[]> VILLAGE_FIRES = new ConcurrentHashMap<>();
    private static final Map<String, Long> BUILDING_DONE = new ConcurrentHashMap<>();

    private FireWatcher() {}

    public static void onBlockChanged(ServerLevel level, BlockPos pos, @Nullable BlockState oldState, @Nullable BlockState newState) {
        if (newState == null || !newState.is(FIRES) || oldState != null && oldState.is(FIRES)) return;
        if (PENDING.size() < 1024) PENDING.add(new Seen(level, pos.immutable()));
    }

    public static void tick(MinecraftServer server) {
        for (int i = 0; i < MOST_A_TICK; i++) {
            Seen seen = PENDING.poll();
            if (seen == null) break;
            try {
                look(seen.level(), seen.pos());
            } catch (Throwable t) {
                // A fire this can't place in a village tells no story.
            }
        }
    }

    private static void look(ServerLevel level, BlockPos pos) {
        var village = com.aetherianartificer.townstead.village.TownRange.at(level, pos).orElse(null);
        if (village == null) return;
        long now = level.getGameTime();
        String villageKey = level.dimension().location() + "#" + village.getId();
        // The building it caught in: one MCA knows, or else a piece of the village as the world generated it.
        var building = BuildingCells.at(level, pos);
        var piece = building == null ? com.aetherianartificer.townstead.village.GeneratedVillages.pieceAt(level, pos) : null;
        if (building != null || piece != null) {
            String buildingKey = villageKey + "#" + (building != null ? "b" + building.getId() : "p" + piece.getCenter().asLong());
            BlockPos middle = building != null ? building.getCenter() : piece.getCenter();
            Long last = BUILDING_DONE.get(buildingKey);
            if (last == null || now - last >= ONCE_A_DAY) {
                BUILDING_DONE.put(buildingKey, now);
                com.aetherianartificer.townstead.api.impl.v1.ApiEvents.villageFire(level, village, middle,
                        building != null ? building.getId() : null);
                VillagerEntityMCA actor = nearest(level, pos);
                if (actor != null && !ChronicleTriggerIndex.isEmpty()) {
                    ChronicleEmitter.emit(level, new TriggerKey(TRIGGER, BUILDING_BURNED), actor, 1.0f,
                            building != null ? Map.of("building", building.getType()) : Map.of());
                }
            }
        }
        long[] window = VILLAGE_FIRES.computeIfAbsent(villageKey, k -> new long[]{now, 0L, now - ONCE_A_DAY});
        if (now - window[0] > WINDOW) {
            window[0] = now;
            window[1] = 0L;
        }
        window[1]++;
        if (window[1] == BIG && now - window[2] >= ONCE_A_DAY) {
            window[2] = now;
            VillagerEntityMCA actor = nearest(level, pos);
            if (actor != null && !ChronicleTriggerIndex.isEmpty()) ChronicleEmitter.emit(level, new TriggerKey(TRIGGER, FIRE_SEEN), actor, 1.0f, Map.of());
        }
    }

    private static @Nullable VillagerEntityMCA nearest(ServerLevel level, BlockPos pos) {
        VillagerEntityMCA best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos).inflate(SAW_IT))) {
            if (villager.isBaby() || !villager.isAlive()) continue;
            double distance = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = villager;
            }
        }
        return best;
    }
}
