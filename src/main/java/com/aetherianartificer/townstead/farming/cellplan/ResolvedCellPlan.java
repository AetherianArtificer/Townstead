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

        // Resolve each XZ key to a 3D crop position (one above the ground)
        for (Integer xzKey : plan.soilPlan().keySet()) {
            BlockPos cropPos = resolveCropPos(level, postPos, xzKey);
            if (cropPos == null) continue;
            SoilType type = plan.soilPlan().get(xzKey);
            BlockPos soilPos = cropPos.below();
            soilByPos.put(soilPos.asLong(), type);
            if (type == SoilType.PROTECTED) {
                protectedPositions.add(cropPos.asLong());
                protectedPositions.add(soilPos.asLong());
            }
        }
        for (Integer xzKey : plan.seedPlan().keySet()) {
            BlockPos cropPos = resolveCropPos(level, postPos, xzKey);
            if (cropPos == null) continue;
            String assignment = plan.seedPlan().get(xzKey);
            seedByPos.put(cropPos.asLong(), assignment);
            if (SeedAssignment.PROTECTED.equals(assignment)) {
                protectedPositions.add(cropPos.asLong());
                protectedPositions.add(cropPos.below().asLong());
            }
        }

        return new ResolvedCellPlan(seedByPos, soilByPos, protectedPositions, plan.signature());
    }

    private static final int Y_SCAN_RANGE = 16;

    /**
     * For an XZ offset relative to the post, finds the crop position (one above the ground surface).
     * Uses the same 3-pass scan as GridScanner: farmland first, then solid ground, then water.
     */
    @Nullable
    private static BlockPos resolveCropPos(ServerLevel level, BlockPos postPos, int xzKey) {
        int xOff = CellPlan.unpackX(xzKey);
        int zOff = CellPlan.unpackZ(xzKey);
        int wx = postPos.getX() + xOff;
        int wz = postPos.getZ() + zOff;
        int baseY = postPos.getY();

        // Pass 1: farmland
        for (int dy = Y_SCAN_RANGE; dy >= -Y_SCAN_RANGE; dy--) {
            BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
            if (level.getBlockState(candidate).getBlock() instanceof FarmBlock) {
                return candidate.above();
            }
        }
        // Pass 2: any solid ground (skip trees/crops/fluids)
        for (int dy = Y_SCAN_RANGE; dy >= -Y_SCAN_RANGE; dy--) {
            BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
            BlockState state = level.getBlockState(candidate);
            if (state.isAir()) continue;
            if (state.getBlock() instanceof CropBlock) continue;
            if (state.getBlock() instanceof BushBlock) continue;
            if (state.getBlock() instanceof LeavesBlock) continue;
            if (state.is(BlockTags.LOGS)) continue;
            if (state.is(BlockTags.FENCES)) continue;
            if (state.is(BlockTags.WALLS)) continue;
            if (state.is(BlockTags.SIGNS)) continue;
            if (state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.LAVA)) continue;
            return candidate.above();
        }
        return null;
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
            if (desiredSoil == SoilType.PROTECTED || desiredSoil == SoilType.NONE) continue;
            BlockPos soilPos = BlockPos.of(entry.getKey());
            BlockPos cropPos = soilPos.above();
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
