package com.aetherianartificer.townstead.hunger.farm;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class FarmBlueprint {
    private final String id;
    private final BlockPos anchor;
    private final List<BlockPos> soilCells;
    private final Set<Long> soilCellKeys;
    private final String plannerType;

    public FarmBlueprint(String id, BlockPos anchor, List<BlockPos> soilCells, Set<Long> soilCellKeys, String plannerType) {
        this.id = id;
        this.anchor = anchor.immutable();
        this.soilCells = List.copyOf(soilCells);
        this.soilCellKeys = Set.copyOf(soilCellKeys);
        this.plannerType = plannerType != null ? plannerType : "";
    }

    public FarmBlueprint(String id, BlockPos anchor, List<BlockPos> soilCells, Set<Long> soilCellKeys) {
        this(id, anchor, soilCells, soilCellKeys, "");
    }

    public static FarmBlueprint empty(BlockPos anchor) {
        return new FarmBlueprint("empty", anchor, List.of(), Collections.emptySet(), "");
    }

    public String id() {
        return id;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public List<BlockPos> soilCells() {
        return soilCells;
    }

    public String plannerType() {
        return plannerType;
    }

    public boolean containsSoil(BlockPos pos) {
        return soilCellKeys.contains(pos.asLong());
    }

    public boolean isEmpty() {
        return soilCells.isEmpty();
    }
}
