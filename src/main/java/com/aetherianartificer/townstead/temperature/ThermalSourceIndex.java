package com.aetherianartificer.townstead.temperature;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.DoubleSupplier;

/**
 * Where thermal sources may be, per chunk section. A section is scanned once, on first query,
 * then kept exact by {@code Level.setBlock}. Entries hold their section weakly, so an unloaded or
 * reloaded chunk is simply rebuilt; a slow rebuild covers mods that write sections directly.
 * Server thread only: callers fall back to a direct scan elsewhere.
 */
public final class ThermalSourceIndex {
    private static final int REBUILD_TICKS = 1200;
    private static final int MAX_SECTIONS = 16384;
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<Entry>> LEVELS = new WeakHashMap<>();
    private static volatile boolean offThreadChange;
    private static long versions;

    private ThermalSourceIndex() {}

    private static final class Entry {
        final WeakReference<LevelChunkSection> section;
        final long builtAt;
        SourceBits bits;
        // Only ever increases, and a rebuilt entry starts above every earlier one.
        long version = ++versions;

        Entry(LevelChunkSection section, long builtAt) {
            this.section = new WeakReference<>(section);
            this.builtAt = builtAt;
        }
    }

    private record Hit(LevelChunkSection section, SourceBits bits, int baseX, int baseY, int baseZ, double minDistSq) {}

    public interface Visitor {
        void visit(BlockPos.MutableBlockPos pos, BlockState state);
    }

    public static void clear() {
        LEVELS.clear();
    }

    /** Keeps an indexed section exact; unindexed sections cost one map lookup. */
    public static void changed(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getServer().isSameThread()) {
            offThreadChange = true;
            return;
        }
        Long2ObjectOpenHashMap<Entry> sections = LEVELS.get(level);
        if (sections == null) return;
        Entry entry = sections.get(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        if (entry == null) return;
        boolean may = ThermalBlocks.mayBeSource(state);
        if (entry.bits == null) {
            if (!may) return;
            entry.bits = new SourceBits();
        }
        if (entry.bits.set(SourceBits.index(pos.getX(), pos.getY(), pos.getZ()), may)) entry.version = ++versions;
    }

    /**
     * A value that changes whenever any possible source in the box appears, disappears or changes
     * state, or a section in it loads, unloads or is rebuilt. {@code Long.MIN_VALUE} off the
     * server thread, which callers treat as always changed.
     */
    public static long stamp(ServerLevel level, BlockPos center, int radius, int vertical) {
        if (!level.getServer().isSameThread()) return Long.MIN_VALUE;
        if (offThreadChange) {
            offThreadChange = false;
            LEVELS.clear();
        }
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - vertical);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + vertical);
        Long2ObjectOpenHashMap<Entry> sections = LEVELS.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
        long now = level.getGameTime();
        long stamp = 0;
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    stamp = stamp * 31 + 1;
                    continue;
                }
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
                    stamp = stamp * 31 + entry(sections, SectionPos.asLong(cx, sy, cz), section, now).version;
                }
            }
        }
        return stamp;
    }

    /**
     * Visits possible sources in the box, nearest section first. A section whose closest point is
     * at or beyond {@code cutoff} (squared distance) ends the walk. False off the server thread.
     */
    public static boolean forEach(ServerLevel level, BlockPos center, int radius, int vertical,
                                  DoubleSupplier cutoff, Visitor visitor) {
        if (!level.getServer().isSameThread()) return false;
        if (offThreadChange) {
            offThreadChange = false;
            LEVELS.clear();
        }
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - vertical);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + vertical);
        if (minY > maxY) return true;
        Long2ObjectOpenHashMap<Entry> sections = LEVELS.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
        long now = level.getGameTime();

        ArrayList<Hit> hits = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
                    SourceBits bits = entry(sections, SectionPos.asLong(cx, sy, cz), section, now).bits;
                    if (bits == null || bits.count() == 0) continue;
                    int bx = cx << 4, by = sy << 4, bz = cz << 4;
                    double dx = gap(center.getX(), Math.max(minX, bx), Math.min(maxX, bx + 15));
                    double dy = gap(center.getY(), Math.max(minY, by), Math.min(maxY, by + 15));
                    double dz = gap(center.getZ(), Math.max(minZ, bz), Math.min(maxZ, bz + 15));
                    hits.add(new Hit(section, bits, bx, by, bz, dx * dx + dy * dy + dz * dz));
                }
            }
        }
        if (hits.isEmpty()) return true;
        hits.sort((a, b) -> Double.compare(a.minDistSq(), b.minDistSq()));

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Hit hit : hits) {
            if (hit.minDistSq() >= cutoff.getAsDouble()) break;
            hit.bits().forEach(index -> {
                int x = hit.baseX() + (index & 15);
                int y = hit.baseY() + (index >>> 8);
                int z = hit.baseZ() + (index >>> 4 & 15);
                if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return;
                visitor.visit(cursor.set(x, y, z), hit.section().getBlockState(index & 15, index >>> 8, index >>> 4 & 15));
            });
        }
        return true;
    }

    private static double gap(int point, int min, int max) {
        return point < min ? min - point : point > max ? point - max : 0;
    }

    private static Entry entry(Long2ObjectOpenHashMap<Entry> sections, long key, LevelChunkSection section, long now) {
        Entry entry = sections.get(key);
        if (entry != null && entry.section.get() == section && now - entry.builtAt < REBUILD_TICKS) return entry;
        entry = build(section, now);
        if (sections.size() >= MAX_SECTIONS && !sections.containsKey(key)) prune(sections);
        sections.put(key, entry);
        return entry;
    }

    private static Entry build(LevelChunkSection section, long now) {
        Entry entry = new Entry(section, now);
        if (section.hasOnlyAir() || !section.maybeHas(ThermalBlocks::mayBeSource)) return entry;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!ThermalBlocks.mayBeSource(section.getBlockState(x, y, z))) continue;
                    if (entry.bits == null) entry.bits = new SourceBits();
                    entry.bits.set(SourceBits.index(x, y, z), true);
                }
            }
        }
        return entry;
    }

    private static void prune(Long2ObjectOpenHashMap<Entry> sections) {
        sections.values().removeIf(entry -> entry.section.get() == null);
        if (sections.size() >= MAX_SECTIONS) sections.clear();
    }
}
