package com.aetherianartificer.townstead.tick;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clears stale bed occupancy flags for the villager's own MCA village during
 * that villager's scheduled sleep window. A bed is only sanitized when the
 * block is marked occupied but no actual sleeping villager is present there.
 */
public final class BedOccupancySanitizer {
    private static final long SANITIZE_INTERVAL_TICKS = 200L;
    private static final int BED_SCAN_RADIUS = 64;
    private static final Map<Integer, Long> LAST_SANITIZE_BY_VILLAGE = new ConcurrentHashMap<>();

    private BedOccupancySanitizer() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.isBaby() || villager.isSleeping()) return;
        if (scheduledActivity(villager) != Activity.REST) return;

        Optional<Village> village = villager.getResidency().getHomeVillage();
        if (village.isEmpty()) return;

        Village homeVillage = village.get();
        long gameTime = level.getGameTime();
        Long lastSanitize = LAST_SANITIZE_BY_VILLAGE.get(homeVillage.getId());
        if (lastSanitize != null && gameTime - lastSanitize < SANITIZE_INTERVAL_TICKS) return;
        LAST_SANITIZE_BY_VILLAGE.put(homeVillage.getId(), gameTime);

        BlockPos center = new BlockPos(homeVillage.getCenter());
        PoiManager poiManager = level.getPoiManager();
        poiManager.findAll(
                holder -> holder.is(PoiTypes.HOME),
                pos -> homeVillage.isWithinBorder(pos, Village.BORDER_MARGIN),
                center,
                BED_SCAN_RADIUS,
                PoiManager.Occupancy.ANY
        ).forEach(pos -> clearIfStale(level, pos));
    }

    public static void forget(VillagerEntityMCA villager) {
        villager.getResidency().getHomeVillage().ifPresent(village -> LAST_SANITIZE_BY_VILLAGE.remove(village.getId()));
    }

    private static Activity scheduledActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private static void clearIfStale(ServerLevel level, BlockPos pos) {
        BlockPos headPos = normalizeBedHead(level, pos);
        if (headPos == null) return;

        BlockState headState = level.getBlockState(headPos);
        if (!(headState.getBlock() instanceof BedBlock) || !headState.getValue(BedBlock.OCCUPIED)) {
            return;
        }

        if (hasSleepingOccupant(level, headPos)) {
            return;
        }

        level.setBlock(headPos, headState.setValue(BedBlock.OCCUPIED, false), 3);

        BlockPos footPos = headPos.relative(BedBlock.getConnectedDirection(headState));
        BlockState footState = level.getBlockState(footPos);
        if (footState.getBlock() instanceof BedBlock && footState.getValue(BedBlock.OCCUPIED)) {
            level.setBlock(footPos, footState.setValue(BedBlock.OCCUPIED, false), 3);
        }
    }

    private static boolean hasSleepingOccupant(ServerLevel level, BlockPos headPos) {
        AABB box = new AABB(headPos).inflate(1.5D, 1.5D, 1.5D);
        return !level.getEntitiesOfClass(VillagerEntityMCA.class, box, villager ->
                villager.isSleeping() && villager.getSleepingPos().filter(headPos::equals).isPresent()
        ).isEmpty();
    }

    private static BlockPos normalizeBedHead(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return null;
        if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return pos.relative(BedBlock.getConnectedDirection(state));
        }
        return pos.immutable();
    }
}
