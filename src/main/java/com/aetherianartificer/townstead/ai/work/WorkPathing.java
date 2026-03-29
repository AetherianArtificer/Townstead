package com.aetherianartificer.townstead.ai.work;

import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightPathingHooks;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class WorkPathing {
    private static final Set<String> MCA_GRAVESTONE_BLOCKS = Set.of(
            "upright_headstone",
            "slanted_headstone",
            "cross_headstone",
            "wall_headstone",
            "gravelling_headstone",
            "cobblestone_upright_headstone",
            "cobblestone_slanted_headstone",
            "wooden_upright_headstone",
            "wooden_slanted_headstone",
            "golden_upright_headstone",
            "golden_slanted_headstone",
            "deepslate_upright_headstone",
            "deepslate_slanted_headstone",
            "tombstone"
    );

    private WorkPathing() {}

    public static void clearMovementIntent(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    public static boolean isSafeStandPosition(BlockGetter level, BlockPos feetPos) {
        if (level == null || feetPos == null) return false;
        BlockPos floorPos = feetPos.below();
        BlockPos headPos = feetPos.above();
        BlockState feetState = level.getBlockState(feetPos);
        BlockState headState = level.getBlockState(headPos);
        BlockState floorState = level.getBlockState(floorPos);
        if (!isPassable(feetState, level, feetPos)) return false;
        if (!isPassable(headState, level, headPos)) return false;
        if (!floorState.isFaceSturdy(level, floorPos, Direction.UP)) return false;
        if (FarmersDelightPathingHooks.isUnsafeWorkSurface(level, floorPos)
                || FarmersDelightPathingHooks.isUnsafeWorkSurface(level, feetPos)
                || FarmersDelightPathingHooks.isUnsafeWorkSurface(level, headPos)) {
            return false;
        }
        if (isAvoidanceHazard(level, feetPos) || isAvoidanceHazard(level, floorPos) || isAvoidanceHazard(level, headPos)) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (isAvoidanceHazard(level, feetPos.relative(direction))
                    || isAvoidanceHazard(level, headPos.relative(direction))) {
                return false;
            }
        }
        return !FarmersDelightPathingHooks.isHazardousCookware(level, feetPos)
                && !FarmersDelightPathingHooks.isHazardousCookware(level, floorPos);
    }

    public static List<BlockPos> standCandidatesAround(
            BlockGetter level,
            BlockPos anchor,
            @Nullable Set<Long> allowedFeetPositions
    ) {
        if (level == null || anchor == null) return List.of();
        List<BlockPos> candidates = new ArrayList<>();
        int[] verticalChoices = {-1, 0, 1};
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                int manhattan = Math.abs(dx) + Math.abs(dz);
                if (manhattan > 3) continue;
                for (int yOffset : verticalChoices) {
                    BlockPos candidate = anchor.offset(dx, yOffset, dz).immutable();
                    if (allowedFeetPositions != null && !allowedFeetPositions.contains(candidate.asLong())) continue;
                    if (!isSafeStandPosition(level, candidate)) continue;
                    candidates.add(candidate);
                }
            }
        }
        candidates.sort(Comparator
                .comparingInt((BlockPos pos) -> Math.abs(pos.getX() - anchor.getX()) + Math.abs(pos.getZ() - anchor.getZ()))
                .thenComparingInt(pos -> pos.getY() - anchor.getY())
                .thenComparingLong(BlockPos::asLong));
        return List.copyOf(candidates);
    }

    public static @Nullable BlockPos nearestStandCandidate(
            BlockGetter level,
            VillagerEntityMCA villager,
            BlockPos anchor,
            @Nullable Set<Long> allowedFeetPositions
    ) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : standCandidatesAround(level, anchor, allowedFeetPositions)) {
            double dist = villager.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isPassable(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(net.minecraft.tags.BlockTags.DOORS)
                || state.is(net.minecraft.tags.BlockTags.FENCE_GATES)
                || state.is(net.minecraft.tags.BlockTags.TRAPDOORS)) {
            return false;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isAvoidanceHazard(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        return "mca".equals(id.getNamespace()) && MCA_GRAVESTONE_BLOCKS.contains(id.getPath());
    }
}
