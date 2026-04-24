package com.aetherianartificer.townstead.farming.cellplan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Y-resolved cell plan. Takes a {@link CellPlan} with XZ-only keys and resolves each
 * to a 3D {@link BlockPos} via heightmap scan (±3 Y from the post).
 *
 * <p>Built once per blueprint rebuild, not per tick. Provides O(1) lookups for the farmer AI.</p>
 */
public final class ResolvedCellPlan implements CellPlanView {
    public static final ResolvedCellPlan EMPTY = new ResolvedCellPlan(Map.of(), Map.of(), Set.of(), 0L);

    private final Map<Long, String> seedByPos;        // resolved 3D pos.asLong() → seed ID / AUTO / NONE
    private final Map<Long, SoilType> soilByPos;      // resolved 3D pos.asLong() → SoilType
    private final Set<Long> protectedPositions;
    private final long signature;

    private ResolvedCellPlan(Map<Long, String> seedByPos, Map<Long, SoilType> soilByPos,
                              Set<Long> protectedPositions, long signature) {
        this.seedByPos = seedByPos;
        this.soilByPos = soilByPos;
        this.protectedPositions = protectedPositions;
        this.signature = signature;
    }

    /**
     * Resolves a {@link CellPlan}'s XZ-packed keys to 3D positions using heightmap scan.
     * For each XZ cell, finds the farmland or solid ground in the ±3 Y range around the post.
     * The crop position (where seeds are planted) is ground.above().
     */
    public static ResolvedCellPlan resolve(ServerLevel level, BlockPos postPos, CellPlan plan) {
        if (plan == null || plan.isEmpty()) return EMPTY;

        Map<Long, String> seedByPos = new HashMap<>();
        Map<Long, SoilType> soilByPos = new HashMap<>();
        Set<Long> protectedPositions = new HashSet<>();

        // Resolve each XZ key to a 3D crop position (one above the ground).
        // For WATER cells, soilPos = cropPos (water goes at the surface, rice plants INTO the water).
        // For all other cells, soilPos = cropPos.below() (plant ON top of soil).
        for (Integer xzKey : plan.soilPlan().keySet()) {
            SoilType type = plan.soilPlan().get(xzKey);
            BlockPos cropPos = resolveCropPos(level, postPos, xzKey, type);
            if (cropPos == null) continue;
            BlockPos soilPos = (type == SoilType.WATER) ? cropPos : cropPos.below();
            soilByPos.put(soilPos.asLong(), type);
            if (type == SoilType.PROTECTED) {
                protectedPositions.add(cropPos.asLong());
                protectedPositions.add(soilPos.asLong());
            }
        }
        for (Integer xzKey : plan.seedPlan().keySet()) {
            SoilType soilType = plan.soilPlan().getOrDefault(xzKey, SoilType.FARMLAND);
            BlockPos cropPos = resolveCropPos(level, postPos, xzKey, soilType);
            if (cropPos == null) continue;
            if (!plan.soilPlan().containsKey(xzKey)) {
                soilByPos.put(cropPos.below().asLong(), soilType);
            }
            // Seed override keyed by cropPos which equals soilPos for WATER cells.
            BlockPos seedKey = (soilType == SoilType.WATER) ? cropPos : cropPos;
            String assignment = plan.seedPlan().get(xzKey);
            seedByPos.put(seedKey.asLong(), assignment);
            if (SeedAssignment.PROTECTED.equals(assignment)) {
                protectedPositions.add(cropPos.asLong());
                protectedPositions.add((soilType == SoilType.WATER ? cropPos : cropPos.below()).asLong());
            }
        }

        return new ResolvedCellPlan(seedByPos, soilByPos, protectedPositions, plan.signature());
    }

    private static final int Y_SCAN_RANGE = 16;

    /**
     * True if the block is a legitimate "farming ground" block — either already farmland/dirt/grass
     * types, or a vanilla-tillable natural surface. This is independent of what's stacked above,
     * so trees, tall grass, crops, or any other overlying block don't disqualify the ground beneath.
     */
    private static boolean isNaturalGround(BlockState state) {
        if (state.getBlock() instanceof FarmBlock) return true;
        return state.is(net.minecraft.tags.BlockTags.DIRT)
                || state.is(net.minecraft.world.level.block.Blocks.FARMLAND)
                || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.DIRT)
                || state.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
                || state.is(net.minecraft.world.level.block.Blocks.ROOTED_DIRT)
                || state.is(net.minecraft.world.level.block.Blocks.PODZOL)
                || state.is(net.minecraft.world.level.block.Blocks.MYCELIUM)
                || state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)
                || state.is(net.minecraft.world.level.block.Blocks.MUD)
                || state.is(net.minecraft.world.level.block.Blocks.SAND)
                || state.is(net.minecraft.world.level.block.Blocks.RED_SAND)
                || state.is(net.minecraft.world.level.block.Blocks.CLAY)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_SOIL);
    }

    /**
     * For an XZ offset relative to the post, finds the crop position (one above the ground surface).
     * Uses the same 3-pass scan as GridScanner: farmland first, then solid ground, then water.
     */
    @Nullable
    private static BlockPos resolveCropPos(ServerLevel level, BlockPos postPos, int xzKey, SoilType desiredSoil) {
        int xOff = CellPlan.unpackX(xzKey);
        int zOff = CellPlan.unpackZ(xzKey);
        int wx = postPos.getX() + xOff;
        int wz = postPos.getZ() + zOff;
        int baseY = postPos.getY();

        // For WATER cells: FIRST look for any water anywhere in the column (so existing rice
        // plants — whose lower block is waterlogged — stay correctly resolved even after they
        // grow a second block above). Only if the column is fully dry do we fall back to the
        // topmost non-air solid as the replace-with-water position.
        if (desiredSoil == SoilType.WATER) {
            for (int dy = Y_SCAN_RANGE; dy >= -Y_SCAN_RANGE; dy--) {
                BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
                if (level.getFluidState(candidate).is(Fluids.WATER)) return candidate;
            }
            for (int dy = Y_SCAN_RANGE; dy >= -Y_SCAN_RANGE; dy--) {
                BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
                if (!level.getBlockState(candidate).isAir()) return candidate;
            }
            return null;
        }

        // Pass 1: farmland — prefer the one closest in Y to the post.
        BlockPos bestFarm = null;
        int bestFarmDist = Integer.MAX_VALUE;
        for (int dy = -Y_SCAN_RANGE; dy <= Y_SCAN_RANGE; dy++) {
            BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
            if (level.getBlockState(candidate).getBlock() instanceof FarmBlock) {
                int dist = Math.abs(dy);
                if (dist < bestFarmDist) { bestFarmDist = dist; bestFarm = candidate; }
            }
        }
        if (bestFarm != null) return bestFarm.above();

        // Pass 2: real farming ground — a block that is *itself* a valid farming surface (dirt,
        // grass, farmland, sand, etc.), independent of what's stacked above. This naturally accepts
        // dirt under a tree (the farmer can clear the tree first) and rejects stray stone blocks
        // or walls/fences, without needing above-block checks. Pick the one closest in Y to the post.
        BlockPos bestGround = null;
        int bestGroundDist = Integer.MAX_VALUE;
        for (int dy = -Y_SCAN_RANGE; dy <= Y_SCAN_RANGE; dy++) {
            BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
            BlockState state = level.getBlockState(candidate);
            if (!isNaturalGround(state)) continue;
            int dist = Math.abs(dy);
            if (dist < bestGroundDist) { bestGroundDist = dist; bestGround = candidate; }
        }
        return bestGround != null ? bestGround.above() : null;
    }

    // ── CellPlanView impl ──

    @Override
    public boolean isProtected(BlockPos pos) {
        return protectedPositions.contains(pos.asLong());
    }

    @Override
    @Nullable
    public String seedOverride(BlockPos pos) {
        return seedByPos.get(pos.asLong());
    }

    @Override
    @Nullable
    public SoilType soilOverride(BlockPos pos) {
        return soilByPos.get(pos.asLong());
    }

    public long signature() {
        return signature;
    }

    public boolean isEmpty() {
        return seedByPos.isEmpty() && soilByPos.isEmpty();
    }

    /**
     * Enumerates every non-protected, non-NONE cell the plan paints, as {@link PlannedCell}s.
     * This is the authoritative todo list for the farmer AI.
     */
    public List<PlannedCell> plannedCells() {
        List<PlannedCell> out = new ArrayList<>();
        for (Map.Entry<Long, SoilType> entry : soilByPos.entrySet()) {
            SoilType desiredSoil = entry.getValue();
            if (desiredSoil == SoilType.PROTECTED || desiredSoil == SoilType.NONE || desiredSoil == SoilType.CLAIM) continue;
            BlockPos soilPos = BlockPos.of(entry.getKey());
            // WATER cells: the plant (rice etc.) goes INTO the water block, same position.
            // Other cells: the plant sits on top of the soil.
            BlockPos cropPos = (desiredSoil == SoilType.WATER) ? soilPos : soilPos.above();
            if (protectedPositions.contains(cropPos.asLong())) continue;

            String seed = seedByPos.get(cropPos.asLong());
            if (SeedAssignment.PROTECTED.equals(seed)) continue;
            // No explicit seed painted → don't plant. Soil-only paint means "prep the ground, leave it empty".
            if (seed == null) seed = SeedAssignment.NONE;

            out.add(new PlannedCell(soilPos, cropPos, desiredSoil, seed));
        }
        return out;
    }
}
