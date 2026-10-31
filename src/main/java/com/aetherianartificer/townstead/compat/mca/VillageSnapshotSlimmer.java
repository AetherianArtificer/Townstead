package com.aetherianartificer.townstead.compat.mca;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Bounds the wire size of MCA's {@code GetVillageResponse} by truncating each
 * building's {@code blocks2} position lists to at most {@link #MAX_POSITIONS_PER_BLOCK}
 * entries per block id. The retained entries are the real, unmodified position
 * records, so they decode normally on every MCA version.
 *
 * <p><b>History.</b> The prior implementation replaced every position with an
 * empty {@link CompoundTag}, keeping only list lengths for MCA's old
 * {@code List<BlockPos>.size()} tooltip. That broke the floor-system client,
 * which rebuilds building geometry from the real coordinates: empty records fail
 * {@code BlockPos.CODEC} ("Not a list: {}") and the map renders nothing. Keeping
 * real (if truncated) positions renders correctly on both old and new MCA.
 *
 * <p><b>Role now.</b> Defense-in-depth. The actual payload bloat that once tripped
 * the 2 MiB {@link net.minecraft.nbt.NbtAccounter} cap came from ordinary houses
 * recording their walls, because Townstead's dock requirement tags promoted
 * generic blocks to globally trackable; that is fixed at the source, since docks no
 * longer list their deck material as an ingredient. Townstead's own synthetic buildings
 * already cap their seeded {@code blocks2} at 8 positions per block id. This
 * truncation remains as a ceiling for legacy save data that still carries
 * contaminated building block maps until MCA re-scans those buildings.
 *
 * <p>The cap is well above every building type's {@code minBlocks} requirement,
 * so {@code isComplete()} (hence map visibility) is never affected. The only
 * visible effect is that a building with more than {@value #MAX_POSITIONS_PER_BLOCK}
 * of one block type shows a capped count in the hover tooltip.
 *
 * <p>The result is a deep-copied {@link CompoundTag}; the caller never mutates
 * the live {@code Village} snapshot used by the running server.</p>
 */
public final class VillageSnapshotSlimmer {
    /**
     * Max positions retained per block id per building. Chosen well above the
     * largest building-type {@code minBlocks} (dock_l3 = 67 across four groups)
     * so completeness always holds, while still bounding pathological block maps.
     */
    static final int MAX_POSITIONS_PER_BLOCK = 128;

    private VillageSnapshotSlimmer() {}

    public static CompoundTag slim(CompoundTag villageData) {
        CompoundTag copy = new CompoundTag();
        for (String key : villageData.getAllKeys()) {
            if ("buildings".equals(key) && villageData.contains("buildings", Tag.TAG_LIST)) {
                copy.put("buildings", slimBuildings(villageData.getList("buildings", Tag.TAG_COMPOUND)));
                continue;
            }
            Tag value = villageData.get(key);
            if (value != null) copy.put(key, value.copy());
        }
        return copy;
    }

    private static ListTag slimBuildings(ListTag source) {
        ListTag buildings = new ListTag();
        for (int i = 0; i < source.size(); i++) {
            buildings.add(slimBuilding(source.getCompound(i)));
        }
        return buildings;
    }

    private static CompoundTag slimBuilding(CompoundTag source) {
        CompoundTag building = new CompoundTag();
        for (String key : source.getAllKeys()) {
            if ("blocks2".equals(key) && source.contains("blocks2", Tag.TAG_COMPOUND)) {
                building.put("blocks2", slimBlocks2(source.getCompound("blocks2")));
                continue;
            }
            Tag value = source.get(key);
            if (value != null) building.put(key, value.copy());
        }
        return building;
    }

    private static CompoundTag slimBlocks2(CompoundTag blocks2) {
        CompoundTag trimmed = new CompoundTag();
        for (String blockKey : blocks2.getAllKeys()) {
            Tag raw = blocks2.get(blockKey);
            // Positions are stored as a ListTag, but the element type varies by MCA
            // version: the floor-system build encodes each BlockPos via BlockPos.CODEC
            // (an int list), while Townstead's synthetic buildings and legacy MCA use
            // {x,y,z} compounds. Copy elements as-is so both survive; only the count
            // is capped. (The prior TAG_COMPOUND-typed read silently dropped every
            // int-list position, emptying real buildings so they failed isComplete.)
            if (!(raw instanceof ListTag positions)) {
                if (raw != null) trimmed.put(blockKey, raw.copy());
                continue;
            }
            if (positions.size() <= MAX_POSITIONS_PER_BLOCK) {
                trimmed.put(blockKey, positions.copy());
                continue;
            }
            ListTag kept = new ListTag();
            for (int i = 0; i < MAX_POSITIONS_PER_BLOCK; i++) {
                kept.add(positions.get(i).copy());
            }
            trimmed.put(blockKey, kept);
        }
        return trimmed;
    }
}
