package com.aetherianartificer.townstead.compat.mca;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Trims the wire payload of MCA's {@code GetVillageResponse} by stripping the
 * per-position {@code x/y/z} ints from every building's {@code blocks2} entry.
 * Server-side {@code validateBlocks} consults the in-memory {@code blocks} map
 * and never re-reads the wire NBT, so dropping the coordinates does not affect
 * validation. The client never inspects individual {@link net.minecraft.core.BlockPos}
 * coordinates either; {@code BlueprintScreen} only calls
 * {@code List<BlockPos>.size()} for the hover tooltip count, so we keep the
 * list lengths intact by substituting empty {@link CompoundTag}s for the real
 * position records.
 *
 * <p>Why: with Townstead 0.6.0's {@code DockBuildingSync} and {@code
 * EnclosureBuildingSync} writing every plank / every interior column block
 * into {@code blocks2}, villages with a handful of pens easily blow past the
 * 2 MiB {@link net.minecraft.nbt.NbtAccounter} cap on the client decode path.
 * Trimming position payload server-side fixes the bloat at the source.</p>
 *
 * <p>The result is a deep-copied {@link CompoundTag}; the caller never mutates
 * the live {@code Village} snapshot used by the running server.</p>
 */
public final class VillageSnapshotSlimmer {
    private VillageSnapshotSlimmer() {}

    public static CompoundTag slim(CompoundTag villageData) {
        CompoundTag copy = villageData.copy();
        if (!copy.contains("buildings", Tag.TAG_LIST)) return copy;
        ListTag buildings = copy.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < buildings.size(); i++) {
            CompoundTag building = buildings.getCompound(i);
            if (!building.contains("blocks2", Tag.TAG_COMPOUND)) continue;
            CompoundTag blocks2 = building.getCompound("blocks2");
            CompoundTag trimmed = new CompoundTag();
            for (String blockKey : blocks2.getAllKeys()) {
                ListTag positions = blocks2.getList(blockKey, Tag.TAG_COMPOUND);
                ListTag empties = new ListTag();
                for (int j = 0; j < positions.size(); j++) {
                    empties.add(new CompoundTag());
                }
                trimmed.put(blockKey, empties);
            }
            building.put("blocks2", trimmed);
        }
        return copy;
    }
}
