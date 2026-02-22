package com.aetherianartificer.townstead.hunger.farm;

import com.aetherianartificer.townstead.compat.farming.FarmerCropCompatRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FarmPlanner {
    private static final int CLUSTER_WIDTH = 5;
    private static final int CLUSTER_LENGTH = 7;
    private static final int CLUSTER_GAP = 2;

    private FarmPlanner() {}

    public static FarmBlueprint planStarterRows(
            ServerLevel level,
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadius,
            int maxClusters,
            int maxPlots
    ) {
        return planStarterRows(level, anchor, horizontalRadius, verticalRadius, maxClusters, maxPlots, "starter_rows");
    }

    public static FarmBlueprint planStarterRows(
            ServerLevel level,
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadius,
            int maxClusters,
            int maxPlots,
            String plannerType
    ) {
        Map<Long, BlockPos> baseSoils = new HashMap<>();

        BlockPos min = anchor.offset(-horizontalRadius, -verticalRadius, -horizontalRadius);
        BlockPos max = anchor.offset(horizontalRadius, verticalRadius, horizontalRadius);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!isExistingFarmSoil(level, pos)) continue;
            BlockPos soil = pos.immutable();
            baseSoils.put(soil.asLong(), soil);
        }

        Set<Long> keys = new HashSet<>();
        List<BlockPos> soils = new ArrayList<>();

        // Always preserve existing farmed soil in plan first.
        addExistingSoils(baseSoils.values(), keys, soils, maxPlots);

        List<BlockPos> origins = candidateClusterOrigins(anchor, horizontalRadius, verticalRadius);
        if (origins.isEmpty()) return soils.isEmpty() ? FarmBlueprint.empty(anchor) : new FarmBlueprint(plannerType, anchor, soils, keys, plannerType);

        BlockPos priorityCenter = baseSoils.isEmpty() ? anchor : findNearestToAnchor(baseSoils.values(), anchor);
        int planY = priorityCenter == null ? anchor.getY() : priorityCenter.getY();
        origins = new ArrayList<>(origins.stream().filter(p -> p.getY() == planY).toList());
        if (origins.isEmpty()) {
            origins = List.of(new BlockPos(anchor.getX(), planY, anchor.getZ()));
        }
        origins.sort(Comparator
                .comparingInt((BlockPos o) -> clusterQuality(level, anchor, o, horizontalRadius, verticalRadius)).reversed()
                .thenComparingInt(o -> distSq(o, priorityCenter)));

        int placed = 0;
        for (BlockPos origin : origins) {
            if (placed >= maxClusters || keys.size() >= maxPlots) break;
            int added = addCluster(level, anchor, origin, horizontalRadius, verticalRadius, keys, soils, maxPlots);
            if (added > 0) placed++;
        }

        if (soils.isEmpty()) return FarmBlueprint.empty(anchor);
        return new FarmBlueprint(plannerType, anchor, soils, keys, plannerType);
    }

    private static boolean isLane(BlockPos anchor, BlockPos pos) {
        // Keep a simple walk lane every 4 blocks on Z.
        return Math.floorMod(pos.getZ() - anchor.getZ(), 4) == 0;
    }

    private static boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FarmBlock) return true;
        BlockState above = level.getBlockState(pos.above());
        if (above.getBlock() instanceof CropBlock) return true;
        return FarmerCropCompatRegistry.isExistingFarmSoil(level, pos);
    }

    private static boolean isPotentialExpansionSoil(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FarmBlock) return true;
        if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT)) {
            return true;
        }

        BlockState above = level.getBlockState(pos.above());
        return above.getBlock() instanceof CropBlock;
    }

    private static BlockPos[] cardinalNeighbors(BlockPos pos) {
        return new BlockPos[]{
                pos.north(),
                pos.south(),
                pos.east(),
                pos.west()
        };
    }

    private static List<BlockPos> candidateClusterOrigins(BlockPos anchor, int horizontalRadius, int verticalRadius) {
        List<BlockPos> origins = new ArrayList<>();
        int stepX = CLUSTER_WIDTH + CLUSTER_GAP;
        int stepZ = CLUSTER_LENGTH + CLUSTER_GAP;
        for (int x = anchor.getX() - horizontalRadius; x <= anchor.getX() + horizontalRadius; x += stepX) {
            for (int y = anchor.getY() - verticalRadius; y <= anchor.getY() + verticalRadius; y++) {
                for (int z = anchor.getZ() - horizontalRadius; z <= anchor.getZ() + horizontalRadius; z += stepZ) {
                    origins.add(new BlockPos(x, y, z));
                }
            }
        }
        return origins;
    }

    private static void addExistingSoils(
            Iterable<BlockPos> existing,
            Set<Long> keys,
            List<BlockPos> soils,
            int maxPlots
    ) {
        for (BlockPos pos : existing) {
            if (keys.size() >= maxPlots) break;
            if (keys.add(pos.asLong())) soils.add(pos.immutable());
        }
    }

    private static int addCluster(
            ServerLevel level,
            BlockPos anchor,
            BlockPos origin,
            int horizontalRadius,
            int verticalRadius,
            Set<Long> keys,
            List<BlockPos> soils,
            int maxPlots
    ) {
        int before = keys.size();
        int maxX = origin.getX() + CLUSTER_WIDTH - 1;
        int maxZ = origin.getZ() + CLUSTER_LENGTH - 1;

        for (int x = origin.getX(); x <= maxX && keys.size() < maxPlots; x++) {
            for (int z = origin.getZ(); z <= maxZ && keys.size() < maxPlots; z++) {
                BlockPos pos = new BlockPos(x, origin.getY(), z);
                if (!isInsideBounds(anchor, pos, horizontalRadius, verticalRadius)) continue;
                if (!isPotentialExpansionSoil(level, pos)) continue;
                if (keys.add(pos.asLong())) soils.add(pos.immutable());
            }
        }
        return keys.size() - before;
    }

    private static int clusterQuality(
            ServerLevel level,
            BlockPos anchor,
            BlockPos origin,
            int horizontalRadius,
            int verticalRadius
    ) {
        int totalCandidate = 0;
        int hydratedCandidate = 0;
        int maxX = origin.getX() + CLUSTER_WIDTH - 1;
        int maxZ = origin.getZ() + CLUSTER_LENGTH - 1;
        for (int x = origin.getX(); x <= maxX; x++) {
            for (int z = origin.getZ(); z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, origin.getY(), z);
                if (!isInsideBounds(anchor, pos, horizontalRadius, verticalRadius)) continue;
                if (!isPotentialExpansionSoil(level, pos)) continue;
                totalCandidate++;
                if (hasNearbyWater(level, pos)) hydratedCandidate++;
            }
        }
        // Water coverage is quality, not a hard requirement.
        return (hydratedCandidate * 3) + totalCandidate;
    }

    private static BlockPos findNearestToAnchor(Iterable<BlockPos> positions, BlockPos anchor) {
        BlockPos best = null;
        int bestDistSq = Integer.MAX_VALUE;
        for (BlockPos pos : positions) {
            int dx = pos.getX() - anchor.getX();
            int dy = pos.getY() - anchor.getY();
            int dz = pos.getZ() - anchor.getZ();
            int distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = pos;
            }
        }
        return best;
    }

    private static int distSq(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean hasNearbyWater(ServerLevel level, BlockPos soilPos) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if ((dx * dx + dz * dz) > 16) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getFluidState(soilPos.offset(dx, dy, dz)).is(FluidTags.WATER)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isInsideBounds(BlockPos anchor, BlockPos pos, int horizontalRadius, int verticalRadius) {
        int dx = Math.abs(pos.getX() - anchor.getX());
        int dz = Math.abs(pos.getZ() - anchor.getZ());
        int dy = Math.abs(pos.getY() - anchor.getY());
        return dx <= horizontalRadius && dz <= horizontalRadius && dy <= verticalRadius;
    }
}
