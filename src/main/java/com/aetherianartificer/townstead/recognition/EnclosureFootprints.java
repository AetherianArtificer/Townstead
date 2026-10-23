package com.aetherianartificer.townstead.recognition;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The ground an enclosure actually holds, as columns. MCA stores a building as a box, and the box
 * of an L-shaped pen takes in the field inside the L, so the animals standing there would count as
 * penned. Worked out on first use and kept until the pen changes; never saved, because the world
 * is the record.
 */
public final class EnclosureFootprints {
    /** Null columns: the pen was open when last walked, so its box stands in until it changes. */
    private record Entry(BlockPos pos0, BlockPos pos1, @Nullable LongSet columns) {}

    private static final Map<ServerLevel, Map<Long, Entry>> CACHE = new WeakHashMap<>();

    private EnclosureFootprints() {}

    /** Columns of the pen's ground and ring, or null when it is open or not loaded right now. */
    public static @Nullable LongSet columns(ServerLevel level, Village village, Building building) {
        if (level == null || village == null || building == null) return null;
        long key = key(village, building);
        Entry entry;
        synchronized (CACHE) {
            entry = CACHE.computeIfAbsent(level, ignored -> new HashMap<>()).get(key);
        }
        BlockPos pos0 = building.getPos0();
        BlockPos pos1 = building.getPos1();
        if (entry != null && entry.pos0().equals(pos0) && entry.pos1().equals(pos1)) return entry.columns();

        List<BlockPos> seeds = new ArrayList<>(building.getBlockPosStream().toList());
        seeds.add(building.getCenter());
        SiteRequirements.Scan scan = new SiteRequirements.Scan(level).bounded(new WalkEnclosure.Bounds(
                pos0.getX() - 1, pos0.getZ() - 1, pos1.getX() + 1, pos1.getZ() + 1));
        SiteRequirements.Evaluation evaluation = SiteRequirements.evaluate(scan, building.getType(), seeds,
                building.getCenter(), OptionalBuildingRecognition.reach(building.getType()));
        if (evaluation.verdict() == SiteRequirements.Verdict.UNKNOWN) return null;
        LongOpenHashSet columns = null;
        if (evaluation.verdict() == SiteRequirements.Verdict.SATISFIED) {
            columns = new LongOpenHashSet();
            for (BlockPos cell : evaluation.cells()) columns.add(SiteGeometry.column(cell.getX(), cell.getZ()));
        }
        synchronized (CACHE) {
            CACHE.computeIfAbsent(level, ignored -> new HashMap<>()).put(key, new Entry(pos0, pos1, columns));
        }
        return columns;
    }

    public static void invalidate(ServerLevel level, Village village, Building building) {
        if (level == null || village == null || building == null) return;
        synchronized (CACHE) {
            Map<Long, Entry> entries = CACHE.get(level);
            if (entries != null) entries.remove(key(village, building));
        }
    }

    private static long key(Village village, Building building) {
        return ((long) village.getId() << 32) ^ (building.getId() & 0xFFFFFFFFL);
    }
}
