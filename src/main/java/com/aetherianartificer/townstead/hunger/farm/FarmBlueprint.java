package com.aetherianartificer.townstead.hunger.farm;

import com.aetherianartificer.townstead.farming.cellplan.CellPlanView;
import com.aetherianartificer.townstead.farming.cellplan.PlannedCell;
import com.aetherianartificer.townstead.farming.cellplan.ResolvedCellPlan;
import com.aetherianartificer.townstead.farming.cellplan.SoilType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A farm blueprint is nothing but a resolution of a Field Post's painted plan.
 * Every cell is a {@link PlannedCell}. No world-scan fallback exists — if no post
 * covers the farmer's anchor, the blueprint is empty and the farmer does no farm work.
 */
public final class FarmBlueprint implements CellPlanView {
    private final BlockPos anchor;
    private final BlockPos postPos;
    private final List<PlannedCell> cells;
    private final Map<Long, PlannedCell> cellsByPos;
    private final ResolvedCellPlan cellPlan;

    private FarmBlueprint(BlockPos anchor, BlockPos postPos, List<PlannedCell> cells, ResolvedCellPlan cellPlan) {
        this.anchor = anchor.immutable();
        this.postPos = postPos.immutable();
        this.cells = List.copyOf(cells);
        Map<Long, PlannedCell> byPos = new HashMap<>();
        for (PlannedCell c : this.cells) byPos.put(c.soilPos().asLong(), c);
        this.cellsByPos = Map.copyOf(byPos);
        this.cellPlan = cellPlan != null ? cellPlan : ResolvedCellPlan.EMPTY;
    }

    public static FarmBlueprint fromCellPlan(BlockPos anchor, BlockPos postPos, ResolvedCellPlan resolved) {
        return new FarmBlueprint(anchor, postPos, resolved.plannedCells(), resolved);
    }

    public static FarmBlueprint empty(BlockPos anchor) {
        return new FarmBlueprint(anchor, anchor, List.of(), ResolvedCellPlan.EMPTY);
    }

    public BlockPos anchor() { return anchor; }
    public BlockPos postPos() { return postPos; }
    public List<PlannedCell> cells() { return cells; }
    public ResolvedCellPlan cellPlan() { return cellPlan; }

    public boolean isEmpty() { return cells.isEmpty(); }

    @Nullable
    public PlannedCell cellAt(BlockPos soilPos) {
        return cellsByPos.get(soilPos.asLong());
    }

    public boolean containsSoil(BlockPos soilPos) {
        return cellsByPos.containsKey(soilPos.asLong());
    }

    /** Convenience — the raw soil positions. */
    public List<BlockPos> soilCells() {
        return cells.stream().map(PlannedCell::soilPos).toList();
    }

    // ── CellPlanView delegation (for protected/seed/soil lookups outside soilCells) ──

    @Override
    public boolean isProtected(BlockPos pos) { return cellPlan.isProtected(pos); }

    @Override
    @Nullable
    public String seedOverride(BlockPos pos) { return cellPlan.seedOverride(pos); }

    @Override
    @Nullable
    public SoilType soilOverride(BlockPos pos) { return cellPlan.soilOverride(pos); }

    @Override
    public int filterSeedSlot(SimpleContainer inv, BlockPos plantPos,
                               BiFunction<SimpleContainer, BlockPos, Integer> fallback) {
        return cellPlan.filterSeedSlot(inv, plantPos, fallback);
    }
}
